package org.kpa.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;
import java.util.function.Supplier;

public interface InsecureFunction<R, T> {
    Logger logger = LoggerFactory.getLogger(InsecureFunction.class);

    T apply(R r) throws Exception;

    static <R, T> Function<R, T> secure(InsecureFunction<R, T> function) {
        return v -> {
            try {
                return function.apply(v);
            } catch (Exception e) {
                throw new RuntimeException("Unprocessed Exception in InsecureConsumer(v): v=" + v
                        + ", msg=" + e.getMessage(), e);
            }
        };
    }

    static <R, T> Function<R, T> ignore(InsecureFunction<R, T> function, Supplier<T> onError) {
        return v -> {
            try {
                return function.apply(v);
            } catch (Exception e) {
                logger.error("Error in InsecureConsumer(v). v={}, msg={}. Ignoring.", v, e.getMessage(), e);
            }
            return onError.get();
        };
    }

}
