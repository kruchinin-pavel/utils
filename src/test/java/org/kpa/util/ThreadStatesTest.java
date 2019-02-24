package org.kpa.util;

import com.google.common.base.Joiner;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class ThreadStatesTest {
    private static final Logger logger = LoggerFactory.getLogger(ThreadStatesTest.class);

    @Test
    public void testPrintThreadState() throws InterruptedException {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted.");
            }
        });
        thread.start();
        Thread.sleep(1000);
        ThreadStates.printThreadState(null, false);
        System.out.println(ThreadStates.printThreadState(thread, false));
        assertEquals(-1, ThreadStates.printThreadState(thread).indexOf("stack"));
    }

    private static final Logger log = LoggerFactory.getLogger(ThreadStatesTest.class);

    /**
     * Test parallel run of multple threads
     *
     * @param args
     * @throws InterruptedException
     * @throws IOException
     */
    public static void main(String[] args) throws InterruptedException, IOException {
        final Object monitor = new Object();
        Collection<Thread> group = Arrays.asList(
                new Thread(() -> run(monitor, ThreadStatesTest::method1), "th1"),
                new Thread(() -> run(monitor, ThreadStatesTest::method2), "th2"));
        group.forEach(Thread::start);
        while (System.in.available() == 0) {

            log.info("States: {}", Joiner.on("\n\t").join(group.stream()
                    .map(v -> v.getName() + "=" + ThreadStates.printThreadState(v))
                    .collect(Collectors.toList())));
            Thread.sleep(1000);
        }
        group.forEach(Thread::interrupt);
        for (Thread thread : group) {
            thread.join();
        }
        log.info("Completed");
    }


    private static void run(final Object monitor, Runnable runnable) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                synchronized (monitor) {
                    try {
                        runnable.run();
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } finally {
            log.info("Completed thread: {}", Thread.currentThread().getName());
        }
    }

    private static void method1() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(1000));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void method2() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(1000));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}