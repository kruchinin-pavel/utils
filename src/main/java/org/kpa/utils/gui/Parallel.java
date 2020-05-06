package org.kpa.utils.gui;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Created by krucpav on 27.04.17.
 */
// TODO move to utils
public class Parallel {
    public interface InsecureSupplier<T> {
        Logger logger = LoggerFactory.getLogger(InsecureSupplier.class);

        T get() throws Exception;

        default T getWithLogOnError() throws Exception {
            try {
                return get();
            } catch (Exception e) {
                logger.error("Error caught in parallel run: {}", e.getMessage(), e);
                throw e;
            }
        }
    }

    public interface InsecureRunnable {
        void run() throws Exception;
    }

    private static final ListeningExecutorService exec = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

    static {
        MoreExecutors.addDelayedShutdownHook(exec, 30, TimeUnit.SECONDS);
    }

    public static Fut<InsecureRunnable> delayed(InsecureRunnable source) {
        return delayed(source, null);
    }

    public static Fut<InsecureRunnable> delayed(InsecureRunnable source, Runnable onComplete) {
        return delayed(() -> {
            source.run();
            return source;
        }, onComplete);
    }

    public static <T> Fut<T> delayed(InsecureSupplier<T> source) {
        return delayed(source, (Runnable) null);
    }

    public static <T> Fut<T> delayed(InsecureSupplier<T> source, Runnable onComplete) {
        return delayed(source, onComplete == null ? null : v -> onComplete.run());
    }

    public static <T> Fut<T> delayed(final InsecureSupplier<T> source, final Consumer<T> onComplete) {
        final Fut<T> ret = new Fut<>(exec.submit(source::getWithLogOnError), exec);
        if (onComplete != null) {
            ret.addListener(() -> {
                try {
                    onComplete.accept(ret.get());
                } catch (Exception e) {
                    logger.error("Error on passing result to listener: {}", e.getMessage(), e);
                    onComplete.accept(null);
                }
            }, exec);
        }
        return new Fut<>(ret, exec);
    }


    private static final Logger logger = LoggerFactory.getLogger(Parallel.class);

    public static void swingLater(InsecureRunnable source) {
        swingLater(source, null);
    }

    private static AtomicInteger taskCounter = new AtomicInteger();

    public static void swingWait(InsecureRunnable source) {
        Object obj = swingWait(() -> {
            source.run();
            return new Object();
        });
    }

    private static class ResWrap<T> {
        public ResWrap(T result) {
            this.result = result;
        }

        T result;
    }

    public static <T> T swingWait(InsecureSupplier<T> source) {
        AtomicReference<ResWrap<T>> ref = new AtomicReference<>();
        swingWait(source, t -> {
            ref.set(new ResWrap<>(t));
            synchronized (ref) {
                ref.notify();
            }
        });
        synchronized (ref) {
            while (ref.get() == null) {
                try {
                    ref.wait(1_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return ref.get().result;
    }

    public static <T> void swingWait(InsecureSupplier<T> source, Consumer<T> onComplete) {
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                SwingUtilities.invokeAndWait(() -> swingWait(source, onComplete));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                T res = source.getWithLogOnError();
                if (onComplete != null) {
                    onComplete.accept(res);
                }
            } catch (Exception e) {
                logger.error("Error ignored: {}", e.getMessage(), e);
            }
        }
    }

    public static void swing(InsecureRunnable source) {
        if (!SwingUtilities.isEventDispatchThread()) {
            swingLater(source);
        }
        try {
            source.run();
        } catch (Exception e) {
            logger.error("Error happened in swing thread: {}", e.getMessage(), e);
        }
    }

    public static void swingLater(InsecureRunnable source, Consumer<InsecureRunnable> onComplete) {
        final LongRunWarning stack = new LongRunWarning();
        int i = taskCounter.incrementAndGet();
        if (i > 1000 && i % 100 == 0) {
            logger.warn("Huge event queue: {}. Stack={}", i, Throwables.getStackTraceAsString(stack));
        }
        SwingUtilities.invokeLater(() -> {
            long ct = System.nanoTime();
            try {
                swing(source);
                if (onComplete != null) onComplete.accept(source);
            } catch (Exception e) {
                logger.error("Error happened in swing thread: {}", e.getMessage(), e);
                if (onComplete != null) onComplete.accept(null);
            } finally {
                taskCounter.decrementAndGet();
                ct = (System.nanoTime() - ct) / 1_000_000;
                if (ct > 200) {
                    logger.warn("Long ({}ms.) run of: {}", ct, Throwables.getStackTraceAsString(stack));
                }
            }
        });
    }
}
