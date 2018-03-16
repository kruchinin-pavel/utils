package org.kpa.util;

import java.io.Flushable;

public interface CloseableFlushableConsumer<T> extends AutoCloseableConsumer<T>, Flushable {

}
