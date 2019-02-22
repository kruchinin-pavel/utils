package org.kpa.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class StringArrayStore implements StoredArray<String[]> {
    private File file;
    public final String id;
    private static final Logger logger = LoggerFactory.getLogger(StringArrayStore.class);
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 1,
            5, TimeUnit.SECONDS, new LinkedBlockingQueue<>(1_024 * 1_024),
            new DaemonNamedFactory("str_cache", false));
    private final AtomicInteger size = new AtomicInteger();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class StringArray {
        public StringArray() {
        }

        public StringArray(String[] data) {
            this.data = data;
        }

        public String[] data;
    }

    public StringArrayStore(String id) throws IOException {
        file = File.createTempFile("string_cache", id);
        this.id = id;
        logger.info("New {} cache created: {}", id, file);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends String[]> strings) {
        synchronized (this) {
            if (strings.size() > 0) {
                executor.submit(() -> {
                    Json.toFile(file.getAbsolutePath(), strings, StandardOpenOption.APPEND);
                });
            }
            size.addAndGet(strings.size());
        }
        return true;
    }

    @Override
    public boolean add(String[] strings) {
        size.incrementAndGet();
        executor.submit(() -> {
            Json.toFile(file.getAbsolutePath(), Collections.singletonList(new StringArray(strings)), StandardOpenOption.APPEND);
        });
        return true;
    }

    public void awaitCompletion() throws InterruptedException {
        while (executor.getQueue().size() > 0) {
            Thread.sleep(1000);
        }
    }

    @Override
    public String[] get(int index) {
        logger.info("Getting from cache by index {}: {}", index, this);
        try {
            awaitCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Iterator<StringArray> iter = Json.iterableFile(file.getAbsolutePath(), StringArray.class).iterator();
        int index_ = 0;
        while (iter.hasNext()) {
            StringArray next = iter.next();
            if (index_++ == index) {
                return next.data;
            }
        }
        throw new IndexOutOfBoundsException("" + index + " > " + index_);
    }

    @Override
    public List<String[]> subList(int startIndex, int maxCount) {
        logger.info("Getting from cache sublist from index {}: {}", startIndex, this);
        try {
            awaitCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Iterator<StringArray> iter = Json.iterableFile(file.getAbsolutePath(), StringArray.class).iterator();
        List<String[]> ret = new ArrayList<>();
        int index = 0;
        while (iter.hasNext()) {
            String[] data = iter.next().data;
            if (index++ >= startIndex) {
                ret.add(data);
            }
            if (index >= startIndex + maxCount) {
                break;
            }
        }
        return ret;
    }

    @Override
    public List<String[]> get() {
        logger.info("Getting from cache: {}", this);
        try {
            awaitCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Utils.stream(Json.iterableFile(file.getAbsolutePath(), StringArray.class)).map(v -> v.data).collect(Collectors.toList());
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
        return "StringArrayStore{" +
                "file=" + file +
                ", id='" + id + '\'' +
                ", size=" + size.get() +
                '}';
    }

}
