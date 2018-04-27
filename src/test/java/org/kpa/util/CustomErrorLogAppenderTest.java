package org.kpa.util;

import com.google.common.base.Joiner;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class CustomErrorLogAppenderTest {

    private static final Logger logger = LoggerFactory.getLogger(CustomErrorLogAppenderTest.class);

    @Test
    public void testAppender() throws InterruptedException {
        List<String> errors = new ArrayList<>();
        CustomErrorLogAppender.registerErrorHandler(errors::add);

        for (int i = 0; i < 100; i++) {
            RuntimeException e = new RuntimeException("Testing exception");
            logger.error("Testing Exception happened: " + e.getMessage(), e);
        }
        logger.info("Messages being sent to telegram:\n\t{}", Joiner.on("\n\t").join(errors));
        Thread.sleep(100);
        assertEquals(1, errors.size());
    }

}