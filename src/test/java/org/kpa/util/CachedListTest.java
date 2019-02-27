package org.kpa.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;

public class CachedListTest {
    public static final int COUNT = 10000;
    public static final int CACHE_CAPACITY = 80;
    public static final int MAX_COUNT = 30;
    public static final int CACHE_STEP = 10;
    private CachedList<String[]> cache;
    private List<String[]> expected;

    @Before
    public void prepare() {
        expected = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) {
            expected.add(new String[]{
                    "" + i,
                    "" + ThreadLocalRandom.current().nextLong(),
                    "" + ThreadLocalRandom.current().nextLong(),
                    "" + ThreadLocalRandom.current().nextLong()});
        }
        cache = CachedList.createCachedStringArray("tmp", CACHE_CAPACITY, CACHE_CAPACITY, CACHE_STEP);
        expected.forEach(cache::add);
    }

    @Test
    public void doSublistTest() {
        assertTrue(cache.getLastStartIndex() > COUNT - 100);
        assertTrue(cache.getCachedSize() <= CACHE_CAPACITY + CACHE_STEP);
        compare(expected.subList(10, 10 + MAX_COUNT), cache.subList(10, MAX_COUNT));
        assertTrue(cache.getCachedSize() <= CACHE_CAPACITY + CACHE_STEP);
        compare(expected.subList(900, 900 + MAX_COUNT), cache.subList(900, 900 + MAX_COUNT));
        assertTrue(cache.getCachedSize() <= CACHE_CAPACITY + CACHE_STEP);
        compare(expected.subList(COUNT - 500, COUNT - 500 + MAX_COUNT), cache.subList(COUNT - 500, COUNT - 500 + MAX_COUNT));
        assertTrue(cache.getCachedSize() <= CACHE_CAPACITY + CACHE_STEP);
        compare(expected.subList(40, 40 + MAX_COUNT), cache.subList(40, 40 + MAX_COUNT));
        compare(expected.subList(COUNT - 30, COUNT), cache.subList(COUNT - 30, COUNT - 30 + MAX_COUNT));
        assertTrue(cache.getCachedSize() <= CACHE_CAPACITY + CACHE_STEP);
        assertTrue(cache.getLastStartIndex() <= COUNT - 30);
    }

    @Test
    public void doSublist2Test() {
        cache.clearCache();
        assertEquals(20, cache.subList(COUNT - 20, COUNT).size());
        cache.add(new String[]{"asd", "asd"});
        assertEquals(21, cache.subList(COUNT - 20, COUNT - 20 + 300).size());
        int stIndex = COUNT - 2 * CACHE_CAPACITY;
        List<String[]> ret = cache.subList(stIndex, stIndex + MAX_COUNT);
        assertEquals("" + stIndex, ret.get(0)[0]);
        assertEquals(MAX_COUNT, ret.size());
        assertEquals(COUNT, cache.subList(0, COUNT).size());
    }

    @Test
    public void doGetTest() {
        int oldCacheSize = cache.getCachedSize();
        cache.get(0);
        assertEquals(oldCacheSize, cache.getCachedSize());
    }

    @Test
    public void doOutOfRangeTest() {
        cache.subList(COUNT * 2, COUNT * 2 + MAX_COUNT);
    }

    private static void compare(Iterator<String[]> expectedIt, Iterator<String[]> returnedIt) {
        List<String[]> expected = new ArrayList<>();
        List<String[]> returned = new ArrayList<>();
        while (expectedIt.hasNext()) expected.add(expectedIt.next());
        while (returnedIt.hasNext()) returned.add(returnedIt.next());
        compare(expected, returned);
    }

    private static void compare(List<String[]> expected, List<String[]> returned) {
        for (int i = 50; i < Math.max(expected.size(), returned.size()); i++) {
            assertArrayEquals(expected.get(i), returned.get(i));
        }
    }

    @Test
    public void testClear() throws InterruptedException {
        assertEquals(COUNT, cache.size());
        cache.clear();
        assertEquals(0, cache.size());
        List<String[]> exp = Collections.singletonList(new String[]{"test1", "test1"});
        cache.add(exp.get(0));
        compare(exp.iterator(), cache.iterator());
        assertEquals(1, cache.size());
    }


    @After
    public void clear() {
        cache.close();
    }
}