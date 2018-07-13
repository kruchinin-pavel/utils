package org.kpa.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class MultiIterableTest {
    @Test
    public void test() {
        List<Integer> collected = new MultiIterable<>(
                Arrays.asList(Arrays.asList(0, 2, 4, 8), Arrays.asList(1, 3, 5, 9), Arrays.asList(6, 7, 10, 11)),
                Integer::compareTo, false
        ).stream().collect(Collectors.toList());

        assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), collected);
    }

}