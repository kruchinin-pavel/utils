package org.kpa.util.queue;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

public class ConflatingQueueImpl<K, V> implements ConflatingQueue<K, V> {
    private final BlockingQueue<K> keyQueue = new LinkedBlockingQueue<>();
    private final Map<K, AtomicReference<KeyValue<K, V>>> keyValRefByKey = new ConcurrentHashMap<>();

    @Override
    public boolean offer(KeyValue<K, V> keyValue) {
        if (keyValue == null) throw new NullPointerException("keyValue");
        AtomicReference<KeyValue<K, V>> keyValRefNew = new AtomicReference<>();
        keyValRefByKey.compute(keyValue.getKey(), (key, keyValRef) -> {
            KeyValue<K, V> oldKeyVal;
            if (keyValRef == null || (oldKeyVal = keyValRef.get()) == null || !keyValRef.compareAndSet(oldKeyVal, keyValue)) {
                keyValRef = keyValRefNew;
            }
            keyValRef.set(keyValue);
            return keyValRef;
        });
        if (keyValRefNew.get() != null) {
            return keyQueue.offer(keyValRefNew.get().getKey());
        }
        return true;
    }

    @Override
    public KeyValue<K, V> take() throws InterruptedException {
        AtomicReference<KeyValue<K, V>> keyValueRef;
        do {
            keyValueRef = keyValRefByKey.remove(keyQueue.take());
        } while (keyValueRef == null);
        KeyValue<K, V> keyValue;
        do {
            keyValue = keyValueRef.get();
        } while (!keyValueRef.compareAndSet(keyValue, null));
        return keyValue;
    }

    @Override
    public boolean isEmpty() {
        return keyQueue.isEmpty();
    }
}
