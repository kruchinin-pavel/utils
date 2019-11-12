package org.kpa.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Created by krucpav on 14.11.16.
 */
public class CachedVal<T> implements Supplier<T> {
    private T val;
    private final long ttlMs;
    private final Supplier<T> getter;
    private final Consumer<T> disposer;
    private static final Thread thread;
    private final AtomicLong accessCount = new AtomicLong();
    private static final Logger logger = LoggerFactory.getLogger(CachedVal.class);
    private static final ConcurrentLinkedQueue<WeakReference<CachedVal<?>>> validVals = new ConcurrentLinkedQueue<>();

    static {
        thread = new Thread(() -> {
            try {
                do {
                    Iterator<WeakReference<CachedVal<?>>> iter = validVals.iterator();
                    while (iter.hasNext()) {
                        WeakReference<CachedVal<?>> next = iter.next();
                        CachedVal<?> val = next.get();
                        if (val == null) {
                            iter.remove();
                        } else if (System.nanoTime() > val.accessCount.get()) {
                            val.evict();
                        }
                    }
                    Thread.sleep(10);
                } while (!Thread.currentThread().isInterrupted());
            } catch (InterruptedException e) {
                logger.info("cached counter thread interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("STOP: Error caught within cached counter thread: {}", e.getMessage(), e);
                System.exit(1);
            }
        }, "cached counter");
        thread.setDaemon(true);
        thread.start();
    }

    public CachedVal(Supplier<T> getter) {
        this(getter, v -> {
        }, 5_000);
    }

    public CachedVal(Supplier<T> getter, Consumer<T> disposer, long ttlMs) {
        this.getter = getter;
        this.disposer = disposer;
        this.ttlMs = ttlMs;
        synchronized (validVals) {
            validVals.add(new WeakReference<>(this));
        }
    }


    @Override
    public T get() {
        accessCount.set(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ttlMs));
        if (val == null) {
            val = getter.get();
        }
        return val;
    }

    public void evict() {
        try {
            T val = this.val;
            this.val = null;
            if (disposer != null) {
                disposer.accept(val);
            } else if (val instanceof AutoCloseable) {
                ((AutoCloseable) val).close();
            }
        } catch (Exception e) {
            logger.error("Error disposing value(ignored): {}. Msg: {}", val, e.getMessage(), e);
        }
    }

    public void set(T val) {
        this.val = val;
    }

    public static <T> CachedVal<T> getAlive(Supplier<T> getter) {
        return new CachedVal<>(getter, v -> {
        }, 5_000);
    }

    public static <T> CachedVal<T> getDisposable(Supplier<T> getter, long ttlMs) {
        return new CachedVal<>(getter, null, ttlMs);
    }
}
