package com.pscap.utils.transport;

import com.google.common.base.Preconditions;
import org.kpa.util.ThreadStates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class ThreadedWorker<T> implements Consumer<T> {
    private boolean sendNull = true;
    private final Consumer<T> consumer;
    private final ExecutorService executor;
    private final AtomicInteger msgsCounter = new AtomicInteger();
    private static final AtomicInteger thrCounter = new AtomicInteger();
    private final AtomicReference<Thread> currentThread = new AtomicReference<>();
    private static final Logger log = LoggerFactory.getLogger(ThreadedWorker.class);
    private final AtomicReference<Throwable> lastException = new AtomicReference<>();
    private final LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(10);
    private final BlockingQueue<T> messagesQueue;
    private final String workerName;
    private boolean blockinQueue;
    private int bigQueueThreshold = 30;
    private long lastQueuePrintMillis = 0;

    public ThreadedWorker(long keepAlive, String workerName, Consumer<T> consumer) {
        this(keepAlive, workerName, consumer, 1_024 * 1_024);
    }

    public ThreadedWorker(long keepAlive, String workerName, Consumer<T> consumer, int capacity) {
        messagesQueue = new LinkedBlockingQueue<>(capacity);
        this.consumer = consumer;
        executor = new ThreadPoolExecutor(0, 1, keepAlive, TimeUnit.MILLISECONDS,
                workQueue, r -> {
            Thread thread = new Thread(() -> {
                try {
                    currentThread.set(Thread.currentThread());
                    r.run();
                } finally {
                    currentThread.compareAndSet(Thread.currentThread(), null);
                }
            });
            thread.setName(workerName + "-" + thrCounter.incrementAndGet());
            return thread;
        });
        this.workerName = workerName;
    }

    public ThreadedWorker<T> printSlowOnQueueSize(int bigQueueThreshold) {
        this.bigQueueThreshold = bigQueueThreshold;
        return this;
    }

    public ThreadedWorker<T> dontSendNull() {
        sendNull = false;
        return this;
    }

    public ThreadedWorker<T> blockingQueue() {
        blockinQueue = true;
        return this;
    }

    public int size() {
        return msgsCounter.get();
    }

    @Override
    public void accept(T row) {
        validateException();
        if (!blockinQueue) {
            Preconditions.checkArgument(messagesQueue.offer(row), "Can't offer task to queue. " +
                    "This: %s", this);
        } else {
            try {
                messagesQueue.put(row);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        int size = size();
        if (size > bigQueueThreshold) {
            if (System.currentTimeMillis() - lastQueuePrintMillis > 1_000) {
                lastQueuePrintMillis = System.currentTimeMillis();
                log.warn("Slow {}", this);
            }
        }
        if (msgsCounter.incrementAndGet() == 1 && workQueue.size() < 2) {
            executor.submit(() -> {
                T data;
                try {
                    while ((data = messagesQueue.poll()) != null) {
                        try {
                            consumer.accept(data);
                            if (sendNull && msgsCounter.get() == 1) {
                                consumer.accept(null);
                            }
                        } finally {
                            msgsCounter.decrementAndGet();
                        }
                    }
                } catch (Throwable e) {
                    log.error("Exception caught within executor: {}", e.getMessage(), e);
                    lastException.set(e);
                }
            });
        }
    }

    private void validateException() {
        Throwable exception;
        if ((exception = lastException.get()) != null) {
            throw new IllegalArgumentException("Got unprocessed exception in buffer: " + exception.getMessage(), exception);
        }
    }

    public void join() throws TimeoutException, InterruptedException {
        long upTime = System.currentTimeMillis() + 30_000;
        while (msgsCounter.get() > 0 || currentThread.get() != null) {
            validateException();
            Thread.sleep(1_000);
            if (upTime < System.currentTimeMillis()) {
                throw new TimeoutException("Didn't awaited for thread completion. Msgs queue: " + msgsCounter.get());
            }
        }
    }

    @Override
    public String toString() {
        return "com.pscap.utils.transport.ThreadedWorker{" +
                "size=" + size() +
                ", workerName=" + workerName +
                ", " + ThreadStates.printThreadState(currentThread.get()) + "}";
    }
}
