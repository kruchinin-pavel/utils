package org.kpa.util.queue;

import java.util.Objects;

public class KeyValueImpl<K, V> implements KeyValue<K, V> {
    private final K key;
    private final V value;

    public KeyValueImpl(K key, V value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public V getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeyValueImpl<?, ?> keyValue = (KeyValueImpl<?, ?>) o;
        return Objects.equals(key, keyValue.key) &&
                Objects.equals(value, keyValue.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    public static <K, V> KeyValue<K, V> of(K k, V v) {
        return new KeyValueImpl<>(k, v);
    }
}
