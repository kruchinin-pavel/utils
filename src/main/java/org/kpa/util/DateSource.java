package org.kpa.util;

import java.time.LocalDateTime;
import java.util.function.Supplier;

import static org.kpa.util.DateSourceHelper.nsToMs;

/**
 * Created with IntelliJ IDEA.
 * User: kruchinin
 * Date: 15.07.12
 * Time: 10:49
 * To change this template use File | Settings | File Templates.
 */
public interface DateSource extends Supplier<LocalDateTime> {
    long nanos();

    default long millis() {
        return nsToMs(nanos());
    }

    @Override
    default LocalDateTime get() {
        return now();
    }

    /**
     * @return GMT current time
     */
    default LocalDateTime now() {
        return DateSourceHelper.nsToLdtCurrentTZ(nanos());
    }

}
