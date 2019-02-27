package org.kpa.util;

import com.google.common.base.Preconditions;
import com.pscap.utils.transport.ThreadedWorker;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class FileStoredList<T> extends StoredList<T> {
    private File file;
    public final String id;
    private static final Logger logger = LoggerFactory.getLogger(FileStoredList.class);
    private final ThreadedWorker<ItemWrap<T>> executor;
    private final AtomicInteger size = new AtomicInteger();
    private final BiConsumer<String, Collection<? extends T>> addAllFunc;
    private final BiFunction<String, Integer, Iterator<T>> iteratorFunc;

    public FileStoredList(String id, int queueCapacity,
                          BiConsumer<String, Collection<? extends T>> addAllFunc,
                          BiFunction<String, Integer, Iterator<T>> iteratorFunc) {
        this.id = id;
        this.addAllFunc = addAllFunc;
        this.iteratorFunc = iteratorFunc;
        updateFileName();
        executor = new ThreadedWorker<>(5_000, "str_cache",
                this::process, queueCapacity).blockingQueue();
    }

    private static class ItemWrap<T> {
        public final Collection<T> objects;
        public final String fileName;

        private ItemWrap(Collection<T> objects, String fileName) {
            this.fileName = fileName;
            this.objects = objects;
        }
    }

    private ItemWrap<T> lastWrap = null;

    private void process(ItemWrap<T> wrap) {
        if (wrap != null) {
            if (lastWrap != null && !lastWrap.fileName.equals(wrap.fileName)) lastWrap = null;
            if (lastWrap == null) lastWrap = new ItemWrap<>(new ArrayList<>(), wrap.fileName);
            lastWrap.objects.addAll(wrap.objects);
            return;
        }
        try {
            addAllFunc.accept(lastWrap.fileName, lastWrap.objects);
            lastWrap.objects.clear();
        } catch (Throwable e) {
            if (file.getAbsolutePath().equalsIgnoreCase(wrap.fileName)) {
                throw new RuntimeException(e);
            }
        }

    }

    private void updateFileName() {
        try {
            File oldFile = this.file;
            this.file = File.createTempFile("string_cache", id);
            file.deleteOnExit();
            logger.info("New {} cache created: {}. modCount={}", id, this.file, modCount);
            if (oldFile != null) Files.deleteIfExists(Paths.get(oldFile.toString()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends T> values) {
        if (values.size() == 0) return false;
        size.addAndGet(values.size());
        executor.accept(new ItemWrap<>((Collection<T>) values, file.getAbsolutePath()));
        return true;
    }


    private volatile int modCount = 0;

    @Override
    public boolean add(T val) {
        return addAll(Collections.singleton(val));
    }

    public void awaitCompletion() throws InterruptedException, TimeoutException {
        executor.join();
    }

    @Override
    public T get(int index) {
        logger.info("Getting from cache by index {}: {}", index, this);
        try {
            awaitCompletion();
        } catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }
        Iterator<? extends T> iter = iteratorFunc.apply(file.getAbsolutePath(), index);
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
        } catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }
        Iterator<T> iter = iteratorFunc.apply(file.getAbsolutePath(), startIndex);
        List<T> ret = new ArrayList<>();
        int index = startIndex;
        while (iter.hasNext() && index++ < toIndex) {
            ret.add(iter.next());
        }
        return ret;
    }

    @NotNull
    @Override
    public ListIterator<T> listIterator(int index) {
        logger.info("Getting listIterator index={} from file: {}", index, this);
        try {
            awaitCompletion();
        } catch (InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        }

        Iterator<T> val = iteratorFunc.apply(file.getAbsolutePath(), index);
        AtomicInteger nextIndex = new AtomicInteger(index + 1);
        return new ListIterator<T>() {
            @Override
            public boolean hasNext() {
                return val.hasNext();
            }

            @Override
            public T next() {
                T next = val.next();
                nextIndex.incrementAndGet();
                return next;
            }

            @Override
            public boolean hasPrevious() {
                throw new UnsupportedOperationException();
            }

            @Override
            public T previous() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int nextIndex() {
                return nextIndex.get();
            }

            @Override
            public int previousIndex() {
                return Math.max(nextIndex.get() - 2, -1);
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void set(T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void add(T t) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        logger.info("Getting iterator from file: {}", this);
        try {
            awaitCompletion();
        } catch (InterruptedException | TimeoutException e) {
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
            updateFileName();
            size.set(0);
        }
    }

    @Override
    public void close() {
        try {
            awaitCompletion();
            Files.deleteIfExists(Paths.get(file.toString()));
            logger.info("Cache disposed: {}", this);
        } catch (IOException e) {
            logger.warn("Cache dispose error: {}", e.getMessage(), e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
