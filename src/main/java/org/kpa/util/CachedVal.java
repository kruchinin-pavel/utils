package org.kpa.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Created by krucpav on 14.11.16.
 */
public class CachedVal<T> implements Supplier<T> {
    private T val;
    private final Supplier<T> getter;
    private final Consumer<T> disposer;
    private final long ttlMs;
    private final Logger logger = LoggerFactory.getLogger(CachedVal.class);

    public CachedVal(Supplier<T> getter) {
        this(getter, v -> {
        }, 5_000);
    }

    public CachedVal(Supplier<T> getter, Consumer<T> disposer, long ttlMs) {
        this.getter = getter;
        this.disposer = disposer;
        this.ttlMs = ttlMs;
    }

    private AtomicLong accessCount = new AtomicLong();

    @Override
    public T get() {
        accessCount.incrementAndGet();
        if (val == null) {
            val = getter.get();
            new Thread(() -> {
                try {
                    long _accessCount;
                    do {
                        _accessCount = accessCount.get();
                        Thread.sleep(ttlMs);
                    } while (!accessCount.compareAndSet(_accessCount, 0));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    evict();
                }
            }, "cached counter").start();
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
