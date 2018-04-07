package org.kpa.util;

import java.util.function.Supplier;

/**
 * Created by krucpav on 14.11.16.
 */
public class CachedVal<T> implements Supplier<T> {
    private T val;
    private final Supplier<T> getter;

    public CachedVal(Supplier<T> getter) {
        this.getter = getter;
    }

    @Override
    public T get() {
        if (val == null) {
            val = getter.get();
        }
        return val;
    }

    public void set(T val) {
        this.val = val;
    }
}
