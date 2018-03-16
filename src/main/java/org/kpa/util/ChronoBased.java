package org.kpa.util;

import java.time.ZonedDateTime;

public interface ChronoBased<T extends ChronoBased> extends Comparable<T> {
    ZonedDateTime getLocalTime();

    @Override
    default int compareTo(T o) {
        return getLocalTime().compareTo(o.getLocalTime());
    }

}
