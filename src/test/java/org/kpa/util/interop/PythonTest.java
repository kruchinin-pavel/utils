package org.kpa.util.interop;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeoutException;

public class PythonTest {
    private static final Logger log = LoggerFactory.getLogger(PythonTest.class);

    public static void main(String[] args) throws InterruptedException, TimeoutException {
        try (Python python = Python.createSubprocess("src/test/python/zmq_echo.py")) {
            python.waitForLive();
            Preconditions.checkArgument(python.isAlive());
            log.info("Python successfully started: {}", python);

            python.close();
            Preconditions.checkArgument(python.getIsAliveTmout() == -1);
        }
        log.info("Python successfully stopped.");
    }

}