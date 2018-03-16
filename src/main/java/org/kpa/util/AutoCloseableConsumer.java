package org.kpa.util;

import java.util.Objects;
import java.util.function.Consumer;

public interface AutoCloseableConsumer<T> extends AutoCloseable, Consumer<T> {
    @Override
    default void close(){}

    default AutoCloseableConsumer<T> andThen(AutoCloseableConsumer<? super T> after) {
        Objects.requireNonNull(after);
        return (T t) -> { accept(t); after.accept(t); };
    }
}
