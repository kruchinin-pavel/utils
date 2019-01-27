package org.kpa.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertArrayEquals;

public class StringArrayStoreTest {

    private List<String[]> expected;
    private StringArrayStore cache;

    @Before
    public void prepare() throws IOException, InterruptedException {
        expected = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            expected.add(new String[]{"" + ThreadLocalRandom.current().nextLong(),
                    "" + ThreadLocalRandom.current().nextLong(),
                    "" + ThreadLocalRandom.current().nextLong()});
        }
        cache = new StringArrayStore("tmp");
        expected.forEach(cache::add);
        cache.awaitCompletion();
    }

    @Test
    public void doTest() {
        List<String[]> returned = cache.get();
        for (int i = 0; i < 1000; i++) {
            assertArrayEquals(expected.get(i), returned.get(i));
        }
    }

    @Test
    public void doSublistTest() {
        expected = expected.subList(50, expected.size());
        List<String[]> returned = cache.subList(50, expected.size());
        for (int i = 50; i < Math.max(expected.size(), returned.size()); i++) {
            assertArrayEquals(expected.get(i), returned.get(i));
        }
    }

    @Test
    public void doGetTest() {
        assertArrayEquals(expected.get(0), cache.get(0));
        assertArrayEquals(expected.get(1), cache.get(1));
        assertArrayEquals(expected.get(50), cache.get(50));
    }

    @After
    public void clear() {
        cache.close();
    }
}