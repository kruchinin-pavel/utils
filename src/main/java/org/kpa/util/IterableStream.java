package org.kpa.util;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class IterableStream<T> implements Iterable<T> {
    private final AtomicLong sequencer = new AtomicLong();
    private AtomicReference<Item<T>> first = new AtomicReference<>(new Item<>(null, -1));
    public AtomicReference<Item<T>> last = new AtomicReference<>(first.get());
    private final int maxQueue;

    public IterableStream() {
        this(10_000);
    }

    public IterableStream(int max_queue) {
        maxQueue = max_queue;
    }

    private void enqueue(T v) {
        if (Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted");
        }
        Item<T> next = new Item<>(v, sequencer.incrementAndGet());
        last.get().setNext(next);
        last.set(next);
        int tries = 0;
        while (count() > maxQueue) {
            shrink();
            if (count() > maxQueue) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            if (tries++ > 10) {
                throw new RuntimeException("Possible deadlock");
            }
        }
    }

    public long count() {
        return last.get().seq - first.get().seq;
    }

    public int shrink() {
        Item<T> first;
        int shrinkedCount = 0;
        while ((first = this.first.get()).mayShrink()) {
            shrinkedCount++;
            first = first.next();
            this.first.set(first);
            if (first.last) break;
        }
        return shrinkedCount;
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        final Item<T> ffirst = first.get();
        Preconditions.checkArgument(ffirst.seq == -1, "Stream cache is expired." +
                " Iterable is pointed not to start: %s", ffirst);
        ffirst.allocate();
        return new Iterator<T>() {
            Item<T> curr = ffirst;

            @Override
            public boolean hasNext() {
                curr.waitNextSet();
                return !curr.last;
            }

            @Override
            public T next() {
                Item<T> curr = this.curr;
                curr.deallocate();
                this.curr = this.curr.next();
                this.curr.allocate();
                return this.curr.content;
            }
        };
    }

    private static class Item<T> {
        private final T content;
        private final long seq;
        private Item<T> next;
        private boolean last = false;
        AtomicInteger allocated = new AtomicInteger(0);

        public void deallocate() {
            allocated.decrementAndGet();
        }

        public void allocate() {
            synchronized (this) {
                waitNextSet();
                if (allocated == null) {
                    allocated = new AtomicInteger(1);
                } else {
                    allocated.incrementAndGet();
                }
            }
        }

        public boolean mayShrink() {
            synchronized (this) {
                return isNextSet() && allocated != null && allocated.get() == 0;
            }
        }

        @Override
        public String toString() {
            return "Item{" +
                    "content=" + content +
                    ", seq=" + seq +
                    ", last=" + last +
                    '}';
        }

        private Item(T content, long seq) {
            this.content = content;
            this.seq = seq;
        }

        public Item<T> getNext() {
            return last ? this : next;
        }

        public synchronized Item<T> next() {
            waitNextSet();
            return last ? this : next;
        }

        private void waitNextSet() {
            synchronized (this) {
                if (!isNextSet()) {
                    try {
                        this.wait(10_000);
                        Preconditions.checkArgument(isNextSet(), "Didn't awaited for set next: {}", this);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        private boolean isNextSet() {
            return last || next != null;
        }

        public void setNext(Item<T> next) {
            synchronized (this) {
                Preconditions.checkArgument(!isNextSet(), "Next is already set: %s", this);
                if (next == null) {
                    last = true;
                } else {
                    this.next = next;
                }
                this.notifyAll();
            }
        }
    }

    public void consume(Stream<T> stream) {
        new Thread(() -> {
            try {
                stream.forEach(this::enqueue);
            } finally {
                last.get().setNext(null);
            }
        }, "str_cnsmr").start();
    }
}
