package org.kpa.util;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class FileStoredList<T> extends StoredList<T> {
    private File file;
    public final String id;
    private static final Logger logger = LoggerFactory.getLogger(FileStoredList.class);
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 1,
            5, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1_024 * 1_024),
            new DaemonNamedFactory("str_cache", false));
    private final AtomicInteger size = new AtomicInteger();
    private final BiConsumer<String, T> addFunc;
    private final BiConsumer<String, Collection<? extends T>> addAllFunc;
    private final BiFunction<String, Integer, Iterator<T>> iteratorFunc;

    public FileStoredList(String id,
                          BiConsumer<String, T> addFunc,
                          BiConsumer<String, Collection<? extends T>> addAllFunc,
                          BiFunction<String, Integer, Iterator<T>> iteratorFunc) throws IOException {
        file = File.createTempFile("string_cache", id);
        this.id = id;
        this.addFunc = addFunc;
        this.addAllFunc = addAllFunc;
        this.iteratorFunc = iteratorFunc;
        logger.info("New {} cache created: {}", id, file);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends T> strings) {
        synchronized (this) {
            if (strings.size() > 0) {
                executor.submit(() -> addAllFunc.accept(file.getAbsolutePath(), strings));
            }
            size.addAndGet(strings.size());
        }
        return true;
    }

    @Override
    public boolean add(T strings) {
        size.incrementAndGet();
        executor.submit(() -> addFunc.accept(file.getAbsolutePath(), strings));
        return true;
    }

    public void awaitCompletion() throws InterruptedException {
        while (executor.getQueue().size() > 0) {
            Thread.sleep(1000);
        }
    }

    @Override
    public T get(int index) {
        logger.info("Getting from cache by index {}: {}", index, this);
        try {
            awaitCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Iterator<T> iter = iteratorFunc.apply(file.getAbsolutePath(), index);
        int index_ = 0;
        while (iter.hasNext()) {
            T next = iter.next();
            if (index_++ == index) {
                return next;
            }
        }
        throw new IndexOutOfBoundsException("" + index + " > " + index_);
    }

    @Override
    public List<T> subList(int startIndex, int toIndex) {
        Preconditions.checkArgument(toIndex >= startIndex && toIndex <= size(),
                "Invalid toIndex: %s. Size: %s. startIndex=%s", toIndex, size(), startIndex);
        logger.info("Getting from cache sublist from index {}: {}", startIndex, this);
        try {
            awaitCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Iterator<T> iter = iteratorFunc.apply(file.getAbsolutePath(), startIndex);
        List<T> ret = new ArrayList<>();
        int index = 0;
        while (iter.hasNext() && index++ < toIndex - startIndex) {
            ret.add(iter.next());
        }
        return ret;
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        logger.info("Getting iterator from file: {}", this);
        try {
            awaitCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return iteratorFunc.apply(file.getAbsolutePath(), 0);
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    @Override
    public void clear() {
        synchronized (this) {
            try {
                Files.deleteIfExists(Paths.get(file.toString()));
                size.set(0);
                file = File.createTempFile("string_cache", id);
            } catch (IOException e) {
                logger.warn("Temp file deletion failed on buffer clear: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public void close() {
        try {
            Files.deleteIfExists(Paths.get(file.toString()));
            logger.info("Cache disposed: {}", this);
        } catch (IOException e) {
            logger.warn("Cache dispose error: {}", e.getMessage(), e);
        }
    }

    @Override
    public String toString() {
        return "FileStoredList{" +
                "file=" + file +
                ", id='" + id + '\'' +
                ", size=" + size.get() +
                '}';
    }

}
