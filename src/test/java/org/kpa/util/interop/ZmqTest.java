package org.kpa.util.interop;

import com.google.common.base.Strings;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ZmqTest {
    private String rcv;
    private final static String EXPECTED = "ABC";
    private static final Logger logger = LoggerFactory.getLogger(ZmqTest.class);

    @Test
    public void doTest() throws InterruptedException {
        try (Zmq zmq = Zmq.pubSub(5556, "tcp://localhost:5556")) {
            int count = 0;
            while (count++ < 1000) {
                assertTrue(zmq.send(EXPECTED));
                rcv = zmq.tryReceive();
                if (Strings.isNullOrEmpty(rcv)) {
                    logger.info("Nothing received on {} try.", count);
                } else {
                    logger.info("Received string: {}", rcv);
                    break;
                }
                Thread.sleep(1000);
            }
        }
        assertEquals(EXPECTED, rcv);
    }


}