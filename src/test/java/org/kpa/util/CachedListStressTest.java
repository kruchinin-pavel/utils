package org.kpa.util;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

public class CachedListStressTest {
    public static final int CACHE_CAPACITY = 80;
    public static final int CACHE_STEP = 10;
    private CachedList<String[]> cache = CachedList.createCachedStringArray("tmp", CACHE_CAPACITY, CACHE_STEP);
    private static final Logger log = LoggerFactory.getLogger(CachedListStressTest.class);

    @Test
    public void stressTest() throws InterruptedException {
        ExecutorService comparison = Executors.newCachedThreadPool();
        AtomicLong counter = new AtomicLong();
        AtomicReference<String[]> firstStrExp = new AtomicReference<>(new String[]{"initial string"});
        cache.add(firstStrExp.get());
        String[] actuals = cache.get(0);
        assertArrayEquals(firstStrExp.get(), actuals);
        List<Future<Boolean>> res = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            IntStream.range(0, 100).forEach(v -> cache.add(new String[]{"str" + counter.incrementAndGet()}));
            final int indexTo = cache.size();
            final int indexFrom = Math.max(indexTo - 130, 1);
            res.add(comparison.submit(() -> {
                List<String[]> ret = cache.subList(indexFrom, indexTo);
                List<String[]> expect = new ArrayList<>();
                for (int j = indexFrom; j < indexTo; j++) {
                    expect.add(new String[]{"str" + j});
                }
                assertArrayEquals(expect.toArray(), ret.toArray());
                log.info("Checked from index={}, toIndex={}", indexFrom, indexTo);
                return true;
            }));
        }
        comparison.shutdown();
        comparison.awaitTermination(3, TimeUnit.MINUTES);
        res.stream().forEach(t -> {
            try {
                assertTrue(t.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

}