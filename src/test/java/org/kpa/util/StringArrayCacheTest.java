package org.kpa.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;

public class StringArrayCacheTest {
    public static final int COUNT = 10000;
    public static final int CACHE_CAPACITY = 80;
    public static final int CACHE_STEP = 10;
    private StringArrayCache cache;
    private List<String[]> expected;
    private static final Logger log = LoggerFactory.getLogger(StringArrayCacheTest.class);

    @Before
    public void prepare() {
        expected = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) {
            expected.add(new String[]{"" + ThreadLocalRandom.current().nextLong(),
                    "" + ThreadLocalRandom.current().nextLong(),
                    "" + ThreadLocalRandom.current().nextLong()});
        }
        cache = new StringArrayCache("tmp", CACHE_CAPACITY, CACHE_STEP);
        expected.forEach(cache::add);
    }

    @Test
    public void doSublistTest() {
        assertTrue(cache.getLastStartIndex() > COUNT - 100);
        assertTrue(cache.getCachedSize() <= CACHE_CAPACITY + CACHE_STEP);
        compare(expected.subList(10, COUNT), cache.subList(10));
        assertTrue(cache.getCachedSize() <= CACHE_CAPACITY + CACHE_STEP);
        compare(expected.subList(900, COUNT), cache.subList(900));
        assertTrue(cache.getCachedSize() <= CACHE_CAPACITY + CACHE_STEP);
        compare(expected.subList(COUNT - 500, COUNT), cache.subList(COUNT - 500));
        assertTrue(cache.getCachedSize() <= CACHE_CAPACITY + CACHE_STEP);
        compare(expected.subList(40, COUNT), cache.subList(40));
        compare(expected.subList(COUNT - 30, COUNT), cache.subList(COUNT - 30));
        assertTrue(cache.getCachedSize() <= CACHE_CAPACITY + CACHE_STEP);
        assertTrue(cache.getLastStartIndex() <= COUNT - 30);
    }

    private static void compare(List<String[]> expected, List<String[]> returned) {
        for (int i = 50; i < Math.max(expected.size(), returned.size()); i++) {
            assertArrayEquals(expected.get(i), returned.get(i));
        }
    }


    @After
    public void clear() {
        cache.close();
    }
}