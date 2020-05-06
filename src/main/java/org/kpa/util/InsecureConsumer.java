package org.kpa.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public interface InsecureConsumer<T> {
    Logger logger = LoggerFactory.getLogger(InsecureConsumer.class);

    void accept(T t) throws Exception;

    static <T> Consumer<T> secure(InsecureConsumer<T> consumer) {
        return v -> {
            try {
                consumer.accept(v);
            } catch (Exception e) {
                throw new RuntimeException("Unprocessed Exception in InsecureConsumer(v): v=" + v
                        + ", msg=" + e.getMessage(), e);
            }
        };
    }

    static <T> Consumer<T> ignore(InsecureConsumer<T> consumer) {
        return v -> {
            try {
                consumer.accept(v);
            } catch (InterruptedException e) {
                logger.info("Interrupted - calling interrupt for current thread");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.error("Error in InsecureConsumer(v). v={}, msg={}. Ignoring.", v, e.getMessage(), e);
            }
        };
    }
}
