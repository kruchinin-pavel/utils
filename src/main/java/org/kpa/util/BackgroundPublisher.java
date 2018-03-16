package org.kpa.util;


import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Created by krucpav on 22.08.17.
 */
public abstract class BackgroundPublisher<T> implements AutoCloseableConsumer<T> {
    private final ArrayBlockingQueue<T> values;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicBoolean executedBackground = new AtomicBoolean();
    private final AtomicReference<Thread> threadRef = new AtomicReference<>();
    private final AtomicReference<Exception> exceptionRef = new AtomicReference<>();
    private static final Logger logger = LoggerFactory.getLogger(BackgroundPublisher.class);
    private final long newTasksTimeout;
    private Runnable timeOutConsumer = null;

    public BackgroundPublisher(int queueCapacity, long newTasksTimeout) {
        values = new ArrayBlockingQueue<>(queueCapacity);
        this.newTasksTimeout = newTasksTimeout;
    }

    @Override
    public final void close() {
        if (!stopped.compareAndSet(false, true)) return;
        logger.info("Closing publisher");
        do {
            triggerIfRequired();
            Thread thread = threadRef.get();
            if (thread != null && thread.isAlive()) {
                logger.info("Joining publisher thread");
                try {
                    thread.join(180_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } while (threadRef.get() != null);
        implClose();
        logger.info("Publisher closed");
    }

    protected void implClose() {
    }

    protected void implStart() {
    }

    protected abstract void implPublish(List<T> vals) throws Exception;

    public void onPrintTimeout(Runnable onPrintTimeout) {
        this.timeOutConsumer = onPrintTimeout;
    }

    public final void accept(T val) {
        Preconditions.checkArgument(!stopped.get(), "Already stopped");
        checkErrors();
        try {
            values.put(val);
            triggerIfRequired();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void checkErrors() {
        if (exceptionRef.get() != null)
            throw new RuntimeException("Error: " + exceptionRef.get().getMessage(), exceptionRef.get());
    }


    private void triggerIfRequired() {
        if (executedBackground.compareAndSet(false, true)) {
            if (values.size() == 0) {
                executedBackground.compareAndSet(true, false);
                return;
            }
            Thread thr = new Thread(() -> {
                logger.debug("Thread started");
                if (started.compareAndSet(false, true)) implStart();
                AtomicInteger counter = new AtomicInteger();
                try {
                    List<T> data = new ArrayList<>();
                    long timeout = System.currentTimeMillis() + newTasksTimeout;
                    do {
                        data.clear();
                        values.drainTo(data);
                        if (data.size() > 0) {
                            implPublish(data);
                            timeout = System.currentTimeMillis() + newTasksTimeout;
                        } else {
                            if (Thread.currentThread().isInterrupted() || stopped.get()) {
                                break;
                            }
                            Thread.sleep(10);
                        }
                        counter.addAndGet(data.size());
                    }
                    while (data.size() > 0 || System.currentTimeMillis() < timeout);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted");
                } catch (Exception e) {
                    logger.error("Error processing rows: {}", e.getMessage(), e);
                    exceptionRef.compareAndSet(null, e);
                } finally {
                    Preconditions.checkArgument(threadRef.compareAndSet(Thread.currentThread(), null), "Illegal thread set");
                    executedBackground.compareAndSet(true, false);
                    if (values.size() > 0) {
                        triggerIfRequired();
                    }
                    logger.debug("Thread completed. Published in background {} rows", counter.get());
                    Runnable timeOutConsumer = this.timeOutConsumer;
                    if (timeOutConsumer != null) {
                        timeOutConsumer.run();
                    }
                }
            });
            Preconditions.checkArgument(threadRef.compareAndSet(null, thr), "Thread already initialized");
            logger.debug("Starting new thread");
            thr.start();
        }
    }

    public static <T> BackgroundPublisher<T> wrap(Consumer<T> consumer) {
        return new BackgroundPublisher<T>(100, 1_000) {
            @Override
            protected void implPublish(List<T> vals) {
                vals.forEach(consumer);
            }

            @Override
            protected void implClose() {
                if (consumer instanceof AutoCloseable) {
                    try {
                        ((AutoCloseable) consumer).close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
    }
}
