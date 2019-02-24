package com.pscap.utils.transport;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertTrue;

public class ThreadedWorkerTest {
    private final Logger logger = LoggerFactory.getLogger(ThreadedWorkerTest.class);

    @Test
    public void testThreadWorker() {
        ThreadedWorker<String> wrk = new ThreadedWorker<String>(5_000, "wrk-test", str -> {
            try {
                Thread.sleep(1_000);
                logger.info(str);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).dontSendNull().trackQueue();

        for (int i = 9; i < 100; i++) {
            wrk.accept("str:" + i);
        }
        assertTrue(wrk.size() > 30);
    }

}