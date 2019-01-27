package org.kpa.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class StringArrayCache implements StringArray {
    private final int step;
    private int lastStartIndex = 0;
    private final int cacheCapacity;
    private final StringArrayStore stringArrayStore;
    private List<String[]> lastSubList = new LinkedList<>();
    private static final Logger log = LoggerFactory.getLogger(StringArrayCache.class);

    public StringArrayCache(String id, int cacheCapacity, int step) {
        this.cacheCapacity = cacheCapacity;
        this.step = step;
        try {
            stringArrayStore = new StringArrayStore(id);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int getCachedSize() {
        return lastSubList.size();
    }

    public void clearCache() {
        synchronized (this) {
            log.info("Clearing cache: {}", this);
            lastStartIndex = -1;
            lastSubList = null;
        }
    }

    @Override
    public StringArray add(Collection<String[]> strings) {
        synchronized (this) {
            stringArrayStore.add(strings);
            lastSubList.addAll(strings);
            evict();
        }
        return this;
    }

    private void evict() {
        synchronized (this) {
            int delta = lastSubList.size() - cacheCapacity;
            if (delta >= step) {
                lastSubList.subList(0, delta).clear();
                lastStartIndex += delta;
                log.debug("Evicting cache to new index {}", lastStartIndex);
            }
        }
    }

    public int getLastStartIndex() {
        return lastStartIndex;
    }

    @Override
    public StringArray add(String[] strings) {
        synchronized (this) {
            stringArrayStore.add(strings);
            lastSubList.add(strings);
            evict();
        }
        return this;
    }

    private void reloadCache(int startIndex) {
        synchronized (this) {
            if (lastStartIndex >= 0 && lastStartIndex <= startIndex) {
                return;
            }
            log.info("Cache miss. Reload from index: {}. This={}", startIndex, this);
            lastStartIndex = startIndex;
            lastSubList = stringArrayStore.subList(startIndex, size());
        }
    }

    @Override
    public String[] get(int index) {
        synchronized (this) {
            reloadCache(index);
            return lastSubList.get(index - lastStartIndex);
        }
    }

    @Override
    public List<String[]> subList(int startIndex, int maxCount) {
        synchronized (this) {
            if (size() - startIndex > cacheCapacity) {
                log.info("Request is more then cache capacity({}). Direct request from file at  {} to {}. This={}",
                        size() - startIndex, startIndex, size(), this);
                return stringArrayStore.subList(startIndex, maxCount);
            }
            reloadCache(startIndex);
            int startIncl = startIndex - lastStartIndex;
            int endExcl = Math.min(lastSubList.size(), startIncl + maxCount);
            if (startIncl >= endExcl) {
                throw new IllegalStateException(
                        String.format("Wrong state gor cache: %s. startIncl=%s, endExcl=%s, " +
                                        "lastSubList.size=%s, lastStartIndex=%s, startIndex=%s, maxCount=%s",
                                this, startIncl, endExcl, lastSubList.size(), lastStartIndex, startIndex, maxCount));
            }
            return Collections.unmodifiableList(
                    lastSubList.subList(startIncl, endExcl));
        }
    }

    @Override
    public List<String[]> get() {
        return subList(0, 1);
    }

    @Override
    public int size() {
        synchronized (this) {
            return stringArrayStore.size();
        }
    }

    @Override
    public void close() {
        stringArrayStore.close();
    }

    @Override
    public String toString() {
        return "StringArrayCache{" +
                "stringArrayStore=" + stringArrayStore +
                '}';
    }
}
