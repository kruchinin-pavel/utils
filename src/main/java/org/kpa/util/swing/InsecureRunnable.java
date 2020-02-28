
package org.kpa.util.swing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface InsecureRunnable {
    Logger logger = LoggerFactory.getLogger(InsecureRunnable.class);

    void run() throws Exception;

    static Runnable secure(InsecureRunnable consumer) {
        return () -> {
            try {
                consumer.run();
            } catch (Exception e) {
                throw new RuntimeException("Unprocessed Exception in InsecureRunnable: " + e.getMessage(), e);
            }
        };
    }


    static Runnable ignore(InsecureRunnable consumer) {
        return () -> {
            try {
                consumer.run();
            } catch (Exception e) {
                logger.error("Error in {}. Ignoring.", e.getMessage(), e);
            }
        };
    }
}