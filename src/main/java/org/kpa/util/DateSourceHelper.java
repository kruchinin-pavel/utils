package org.kpa.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Created by krucpav on 21.03.17.
 */
public class DateSourceHelper {
    public static final ZoneId gmtZone = ZoneId.of("GMT");

    static {
        System.out.println("Data source helper is heated up " + gmtNow());
    }

    /**
     * Nanos to millis
     *
     * @param nanos nanos since epoch
     * @return millis since epoch
     */
    public static long nsToMs(long nanos) {
        return nanos / 1_000_000;
    }

    /**
     * Millis to nanos
     *
     * @param millis millis since epoch (1970.01.01 00:00) of GMT
     * @return nanos since epoch
     */
    public static long msToNs(long millis) {
        return millis * 1_000_000;
    }

    /**
     * nanos since epoch to LocalDateTime of GMT
     *
     * @param nanos since epoch
     * @return LocalDateTime of GMT
     */
    public static LocalDateTime nsToLdt(long nanos) {
        return Instant.ofEpochMilli(nsToMs(nanos)).atZone(gmtZone).plusNanos(nanos % 1_000_000).toLocalDateTime();
    }

    public static LocalDateTime nsToLdtCurrentTZ(long nanos) {
        return Instant.ofEpochMilli(nsToMs(nanos)).atZone(ZoneId.systemDefault())
                .plusNanos(nanos % 1_000_000).toLocalDateTime();
    }

    public static long getGMTMs(LocalDateTime dateTime) {
        Instant instant = dateTime.atZone(DateSourceHelper.gmtZone).toInstant();
        return instant.getEpochSecond() * 1000 + instant.getNano() / 1_000_000;
    }

    public static LocalDateTime gmtNow() {
        return LocalDateTime.now(gmtZone);
    }
}
