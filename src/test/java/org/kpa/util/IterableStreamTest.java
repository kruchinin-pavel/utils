package org.kpa.util;

import junit.framework.TestCase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class IterableStreamTest extends TestCase {

    public static final int END_INCLUSIVE = 100_000;

    @Test
    public void test() throws InterruptedException {
        IterableStream<Integer> iter = new IterableStream<>(10_000);
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Future<List<Integer>>> tasks = new ArrayList<>();
        IntStream.rangeClosed(1, 100).forEach(index -> {
            tasks.add(executor.submit(() -> {
                AtomicInteger lasVal = new AtomicInteger(-1);
                AtomicInteger count = new AtomicInteger(0);
                iter.forEach(newValue -> {
                    lasVal.set(newValue);
                    count.incrementAndGet();
                });
                return Arrays.asList(lasVal.get(), count.get());
            }));
        });
        iter.consume(IntStream.rangeClosed(1, END_INCLUSIVE).boxed());
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        tasks.forEach(task -> {
            try {
                assertEquals(END_INCLUSIVE, (int) task.get().get(0));
                assertEquals(END_INCLUSIVE, (int) task.get().get(1));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        iter.shrink();
        assertEquals(0, iter.count());
    }

}