package org.kpa.utils.gui;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Created by krucpav on 20.04.17.
 */
public class Fut<T> implements AutoCloseable, ListenableFuture<T> {
    private final AtomicReference<T> res = new AtomicReference<>();
    private final ListenableFuture<T> future;
    private final AtomicReference<RuntimeException> exceptionRef = new AtomicReference<>();
    private final ListeningExecutorService exec;

    public Fut(ListenableFuture<T> future, ListeningExecutorService exec) {
        this.future = future;
        this.exec = exec;
    }

    public Fut<T> addListener(Consumer<T> listener) {
        future.addListener(() -> listener.accept(get()), exec);
        return this;
    }

    public Fut<T> addSwingListener(Consumer<T> listener) {
        addListener(val -> Parallel.swingLater(() -> listener.accept(val)));
        return this;
    }

    public void addListener(Runnable listener, Executor executor) {
        future.addListener(listener, executor);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return future.isDone();
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }

    @Override
    public T get() {
        if (exceptionRef.get() != null) {
            throw exceptionRef.get();
        }
        synchronized (res) {
            if (res.get() == null) {
                try {
                    res.set(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    exceptionRef.set(new RuntimeException(e));
                    throw exceptionRef.get();
                } catch (ExecutionException e) {
                    exceptionRef.set(new RuntimeException(e));
                    throw exceptionRef.get();
                }
            }
        }
        return res.get();
    }

    @Override
    public void close() {
        Object object = res.get();
        if (object != null && object instanceof AutoCloseable) {
            try {
                ((AutoCloseable) object).close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
