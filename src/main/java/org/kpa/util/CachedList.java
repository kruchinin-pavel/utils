package org.kpa.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class CachedList<T> extends StoredList<T> {
    private final int step;
    private int lastStartIndex = 0;
    private final int cacheCapacity;
    private final StoredList<T> store;
    private List<T> lastSubList = new LinkedList<>();
    private static final Logger log = LoggerFactory.getLogger(CachedList.class);
    private final LoadingCache<Integer, T> lastQueriedItems = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS).maximumSize(1000).weakValues().build(new CacheLoader<Integer, T>() {
                @Override
                public T load(@NotNull Integer key) {
                    return store.get(key);
                }
            });

    private CachedList(StoredList<T> store, int cacheCapacity, int step) {
        this.store = store;
        this.cacheCapacity = cacheCapacity;
        this.step = step;
    }

    int getCachedSize() {
        return lastSubList.size();
    }

    void clearCache() {
        synchronized (this) {
            log.info("Clearing cache: {}", this);
            lastStartIndex = -1;
            lastSubList = new LinkedList<>();
        }
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends T> strings) {
        synchronized (this) {
            store.addAll(strings);
            lastSubList.addAll(strings);
            evict();
        }
        return true;
    }

    private void evict() {
        synchronized (this) {
            int delta = Math.max(lastSubList.size() - cacheCapacity, 0);
            if (delta >= step) {
                lastSubList.subList(0, delta).clear();
                lastStartIndex += delta;
                log.debug("Evicting cache to new index {}", lastStartIndex);
            }
        }
    }

    int getLastStartIndex() {
        return lastStartIndex;
    }

    @Override
    public boolean add(T strings) {
        synchronized (this) {
            store.add(strings);
            lastSubList.add(strings);
            evict();
        }
        return true;
    }

    private void reloadCacheIfRequired() {
        synchronized (this) {
            if (lastStartIndex >= 0 && lastStartIndex <= size() - cacheCapacity) {
                return;
            }
            lastStartIndex = Math.max(size() - cacheCapacity, 0);
            log.info("Cache miss. Reload from index: {}. This={}", lastStartIndex, this);
            lastSubList = store.subList(lastStartIndex, size());
        }
    }

    @Override
    public T get(int index) {
        try {
            return lastQueriedItems.get(index);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<T> subList(int startIndex, int toIndex) {
        Preconditions.checkArgument(toIndex >= startIndex,
                "Invalid toIndex: %s. Size: %s. startIndex=%s", toIndex, size(), startIndex);
        if (startIndex >= size()) {
            log.debug("Out of bounds requested(empty list returned): startIndex={}, this={}", startIndex, this);
            return Collections.emptyList();
        }
        synchronized (this) {
            toIndex = Math.min(size(), toIndex);
            if (size() - startIndex > cacheCapacity) {
                log.info("Request is more then cache capacity({}). Direct request from file at  {} to {}. This={}",
                        size() - startIndex, startIndex, size(), this);
                return store.subList(startIndex, toIndex);
            }
            reloadCacheIfRequired();
            int startIncl = startIndex - lastStartIndex;
            int endExcl = Math.min(lastSubList.size(), startIncl + toIndex - startIndex);
            if (startIncl >= endExcl) {
                throw new IllegalStateException(
                        String.format("Wrong state for cache: %s. startIncl=%s, endExcl=%s, " +
                                        "lastSubList.size=%s, lastStartIndex=%s, startIndex=%s, toIndex=%s",
                                this, startIncl, endExcl, lastSubList.size(), lastStartIndex, startIndex, toIndex));
            }
            return Collections.unmodifiableList(lastSubList.subList(startIncl, endExcl));
        }
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return store.iterator();
    }

    @Override
    public int size() {
        synchronized (this) {
            return store.size();
        }
    }

    @Override
    public void close() {
        store.close();
    }

    @Override
    public String toString() {
        return "CachedList{" +
                "store=" + store +
                '}';
    }

    @Override
    public void clear() {
        synchronized (this) {
            store.clear();
            lastSubList.clear();
            lastStartIndex = 0;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class StringArray {
        public StringArray() {
        }

        public StringArray(String[] data) {
            this.data = data;
        }

        public String[] data;
    }


    public static CachedList<String[]> createCachedStringArray(String id, int cacheCapacity, int step) {
        return new CachedList<>(new FileStoredList<>(id,
                (file, strings) -> Json.toFile(file, strings.stream().map(StringArray::new), StandardOpenOption.APPEND),
                (file, startIndex) ->
                        Utils.convert(Json.iterableFile(file, StringArray.class, null, startIndex), sa -> sa.data).iterator()), cacheCapacity, step);
    }

    public static <T> CachedList<T> createCached(String id, int cacheCapacity, int step,
                                                 BiConsumer<String, Collection<? extends T>> addAllFunc,
                                                 BiFunction<String, Integer, Iterator<T>> iteratorFunc) {
        return new CachedList<T>(new FileStoredList<>(id, addAllFunc, iteratorFunc), cacheCapacity, step);
    }

    @NotNull
    @Override
    public ListIterator<T> listIterator(int index) {
        ListIterator<T> iterator = store.listIterator(index);
        return new ListIterator<T>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public T next() {
                T next = iterator.next();
                lastQueriedItems.put(iterator.previousIndex(), next);
                return next;
            }

            @Override
            public boolean hasPrevious() {
                return iterator.hasPrevious();
            }

            @Override
            public T previous() {
                return iterator.previous();
            }

            @Override
            public int nextIndex() {
                return iterator.nextIndex();
            }

            @Override
            public int previousIndex() {
                return iterator.previousIndex();
            }

            @Override
            public void remove() {
                iterator.remove();
            }

            @Override
            public void set(T t) {
                iterator.set(t);
            }

            @Override
            public void add(T t) {
                iterator.add(t);
            }
        };
    }

    @Override
    public Iterator<T> iterator(int index) {
        return store.iterator(index);
    }

}
