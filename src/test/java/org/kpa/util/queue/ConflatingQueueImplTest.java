package org.kpa.util.queue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ConflatingQueueImplTest {
    private final ConflatingQueue<String, Integer> queue = new ConflatingQueueImpl<>();
    private final AtomicReference<CountDownLatch> consumerLatch = new AtomicReference<>(new CountDownLatch(1));
    private final AtomicReference<CountDownLatch> producerLatch = new AtomicReference<>(new CountDownLatch(1));
    private volatile KeyValue<String, Integer> lastVal = KeyValueImpl.of("DUMMY", 0);

    private final Thread consumerThread = new Thread(() -> {
        while (!Thread.interrupted()) {
            try {
                awaiLatch(consumerLatch);
                if (queue.isEmpty()) lastVal = null;
                else lastVal = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                producerLatch.get().countDown();
            }
        }
    });

    @Test
    public void testQueue() throws InterruptedException {
        assertNotNull(lastVal);
        // Empty conflating_queue
        assertNull(readOneMessageByConsumer());

        // one message
        queue.offer(KeyValueImpl.of("BTCUSD", 7000));
        assertEquals(KeyValueImpl.of("BTCUSD", 7000), readOneMessageByConsumer());

        // conflated BTCUSD
        queue.offer(KeyValueImpl.of("BTCUSD", 7001));
        queue.offer(KeyValueImpl.of("ETHUSD", 250));
        queue.offer(KeyValueImpl.of("BTCUSD", 7002));
        assertEquals(KeyValueImpl.of("BTCUSD", 7002), readOneMessageByConsumer());
        assertEquals(KeyValueImpl.of("ETHUSD", 250), readOneMessageByConsumer());

        // Empty conflating_queue
        assertNull(readOneMessageByConsumer());
    }


    public void awaiLatch(AtomicReference<CountDownLatch> latchRef) throws InterruptedException {
        latchRef.get().await();
        latchRef.set(new CountDownLatch(1));
    }

    private KeyValue<String, Integer> readOneMessageByConsumer() throws InterruptedException {
        consumerLatch.get().countDown();
        awaiLatch(producerLatch);
        return lastVal;
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