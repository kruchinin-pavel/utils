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
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class FileStoredList<T> extends StoredList<T> {
    private File file;
    public final String id;
    private ItemWrap<T> lastWrap = null;
    private final AtomicInteger size = new AtomicInteger();
    private final AtomicLong storedSize = new AtomicLong();
    private final BiConsumer<String, Collection<? extends T>> addAllFunc;
    private final BiFunction<String, Integer, Iterator<T>> iteratorFunc;
    private static final Set<FileStoredList> items = new CopyOnWriteArraySet<>();
    private static final Logger logger = LoggerFactory.getLogger(FileStoredList.class);
    private static final ThreadedWorker<Object> executor = new ThreadedWorker<>(5_000, "str_cache",
            o -> {
                if (o == null || !(o instanceof ItemWrap)) {
                    items.forEach(v -> v.process(null));
                }
                if (o != null) {
                    if (o instanceof ItemWrap) {
                        ((ItemWrap) o).list.process((ItemWrap) o);
                    } else if (o instanceof Runnable) {
                        ((Runnable) o).run();
                    } else {
                        throw new IllegalArgumentException("Unknown object: " + o);
                    }
                }
            }, 1_024 * 1_024).printSlowOnQueueSize(5_000).blockingQueue();

    public FileStoredList(String id,
                          BiConsumer<String, Collection<? extends T>> addAllFunc,
                          BiFunction<String, Integer, Iterator<T>> iteratorFunc) {
        this.id = id;
        this.addAllFunc = addAllFunc;
        this.iteratorFunc = iteratorFunc;
        updateFileName();
        items.add(this);
    }

    private static class ItemWrap<T> {
        public final Collection<T> objects;
        private final long storedSize;
        private final FileStoredList<T> list;
        final String fileName;

        private ItemWrap(FileStoredList<T> list, Collection<T> objects, String fileName, long storedSize) {
            this.list = list;
            this.fileName = fileName;
            this.objects = objects;
            this.storedSize = storedSize;
        }
    }

    private void process(ItemWrap<T> wrap) {
        try {
            if (wrap != null) {
                if (lastWrap != null && !lastWrap.fileName.equals(wrap.fileName)) lastWrap = null;
                if (lastWrap == null)
                    lastWrap = new ItemWrap<>(this, new ArrayList<>(), wrap.fileName, wrap.storedSize);
                lastWrap.objects.addAll(wrap.objects);
            } else {
                addAllFunc.accept(lastWrap.fileName, lastWrap.objects);
                lastWrap.objects.clear();
                storedSize.set(lastWrap.storedSize);
            }
        } catch (Throwable e) {
            if (file == null || (wrap != null && file.getAbsolutePath().equalsIgnoreCase(wrap.fileName))) {
                throw new RuntimeException(e);
            }
        }
    }

    private void updateFileName() {
        File oldFile = null;
        try {
            oldFile = this.file;
            this.file = File.createTempFile("string_cache", id);
            logger.info("New {} cache created: {}. modCount={}", id, this.file, modCount);
            if (oldFile != null) {
                Files.deleteIfExists(Paths.get(oldFile.toString()));
            }
        } catch (IOException e) {
            if (oldFile != null) file.deleteOnExit();
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends T> values) {
        if (values.size() == 0) return false;
        size.addAndGet(values.size());
        executor.accept(new ItemWrap<>(this, (Collection<T>) values, file.getAbsolutePath(), size.get()));
        return true;
    }


    private volatile int modCount = 0;

    @Override
    public boolean add(T val) {
        return addAll(Collections.singleton(val));
    }

    @Override
    public T get(int index) {
        logger.info("Getting from cache by index {}: {}", index, this);
        awaitForStoringIndex(index);
        Iterator<? extends T> iter = iteratorFunc.apply(file.getAbsolutePath(), index);
        return iter.next();
    }

    @Override
    public List<T> subList(int startIndex, int toIndex) {
        Preconditions.checkArgument(toIndex >= startIndex && toIndex <= size(),
                "Invalid toIndex: %s. Size: %s. startIndex=%s", toIndex, size(), startIndex);
        awaitForStoringIndex(toIndex);
        logger.info("Getting from cache sublist from index {}: {}", startIndex, this);
        List<T> ret = new ArrayList<>();
        Iterator<T> iter = iteratorFunc.apply(file.getAbsolutePath(), startIndex);
        int index = startIndex;
        while (iter.hasNext() && index++ < toIndex) {
            ret.add(iter.next());
        }
        return ret;
    }

    private void awaitForStoringIndex(int index) {
        if (index >= storedSize.get()) {
            AtomicReference<Boolean> returned = new AtomicReference<>();
            executor.accept((Runnable) () -> returned.set(true));
            Utils.waitOrThrow(10_000, returned::get);
        }
    }

    public Iterator<T> iterator(int index) {
        logger.info("Getting iterator from file: {}", this);
        awaitForStoringIndex(size());
        return iteratorFunc.apply(file.getAbsolutePath(), index);
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return iterator(0);
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

    private final RunOnce doClose = new RunOnce();
    @Override
    public void close() {
        doClose.runOnce(() -> {
            try {
                Files.deleteIfExists(Paths.get(file.toString()));
                items.remove(this);
                logger.info("Cache disposed: {}", this);
            } catch (IOException e) {
                logger.warn("Cache dispose error: {}", e.getMessage(), e);
            }
        });
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
