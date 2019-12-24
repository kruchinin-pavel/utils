package org.kpa.util.queue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class ConflatingQueueImplStressTest {
    private volatile long lastWriteNanos = 0;
    private volatile long lastReadNanos = 0;
    private final List<Long> durations = new ArrayList<>(100);
    private final Map<String, Integer> lastPrices = new ConcurrentHashMap<>();
    private final ConflatingQueue<String, Integer> queue = new ConflatingQueueImpl<>();

    private final Thread consumerThread = new Thread(() -> {
        while (!Thread.interrupted()) {
            try {
                KeyValue<String, Integer> lastVal = queue.take();
                lastReadNanos = System.nanoTime();
                lastPrices.put(lastVal.getKey(), lastVal.getValue());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    });


    @Test
    public void testQueue() throws InterruptedException {
        testQueue(true);
        testQueue(false);
    }

    public void testQueue(boolean warmingUp) throws InterruptedException {
        for (int step = 0; step < 100; step++) {
            int lastBtcPrice = 0, lastEthPrice = 0, lastXbtPrice = 0;
            lastWriteNanos = 0;
            for (int i = 0; i < 10_000; i++) {
                lastBtcPrice = 7_000 + i;
                lastEthPrice = 2_000 + i;
                lastXbtPrice = 6_000 + i;
                lastWriteNanos = System.nanoTime();
                queue.offer(KeyValueImpl.of("BTCUSD", lastBtcPrice));
                queue.offer(KeyValueImpl.of("ETHUSD", lastEthPrice));
                queue.offer(KeyValueImpl.of("XBT", lastXbtPrice));
            }
            while (!queue.isEmpty()) Thread.sleep(100);
            if (!warmingUp) {
                durations.add(lastReadNanos - lastWriteNanos);
                assertEquals(lastBtcPrice, (int) lastPrices.get("BTCUSD"));
                assertEquals(lastEthPrice, (int) lastPrices.get("ETHUSD"));
                assertEquals(lastXbtPrice, (int) lastPrices.get("XBT"));
            }
        }
        if (!warmingUp) {
            double avg = Math.floor(durations.stream().mapToDouble(v -> v).sum() / durations.size());
            double dev = Math.floor(durations.stream().mapToDouble(v -> Math.abs(v - avg)).sum() / durations.size());
            System.out.println("Last write - last read (nanos): avg=" + avg + ", avg.dev.=" + dev);
        }
    }

    @Test
    public void testRandomMultpleThreadWrite() throws InterruptedException {
        int cpus = Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
        System.out.println("Running testRandomMultpleThreadWrite on " + cpus + " threads");
        ExecutorService service = Executors.newFixedThreadPool(cpus);
        AtomicInteger lastBtcPrice = new AtomicInteger();
        AtomicInteger lastEthPrice = new AtomicInteger();
        AtomicInteger lastXbtPrice = new AtomicInteger();
        for (int step = 0; step < cpus; step++) {
            int finalStep = step;
            service.submit(() -> {
                for (int i = 0; i < 100_000; i++) {
                    lastWriteNanos = System.nanoTime();
                    lastBtcPrice.set(7_000 + finalStep + i);
                    queue.offer(KeyValueImpl.of("BTCUSD", lastBtcPrice.get()));
                    lastEthPrice.set(2_000 + finalStep + i);
                    queue.offer(KeyValueImpl.of("ETHUSD", lastEthPrice.get()));
                    lastXbtPrice.set(6_000 + finalStep + i);
                    queue.offer(KeyValueImpl.of("XBT", lastXbtPrice.get()));
                }
            });
        }
        service.shutdown();
        service.awaitTermination(100, TimeUnit.SECONDS);
        while (!queue.isEmpty()) Thread.sleep(100);
        assertEquals(lastBtcPrice.get(), (int) lastPrices.get("BTCUSD"));
        assertEquals(lastEthPrice.get(), (int) lastPrices.get("ETHUSD"));
        assertEquals(lastXbtPrice.get(), (int) lastPrices.get("XBT"));
        System.out.println("Ltcy: " + (lastReadNanos - lastWriteNanos) + " nanos");
    }


    @Before
    public void prepare() {
        consumerThread.start();
    }

    @After
    public void shutdown() {
        consumerThread.interrupt();
    }
}