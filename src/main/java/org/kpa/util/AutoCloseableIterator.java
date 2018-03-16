package org.kpa.util;

import java.util.Iterator;

/**
 * Created by krucpav on 14.06.17.
 */
public interface AutoCloseableIterator<T> extends Iterator<T>, AutoCloseable {
    @Override
    void close();
}
