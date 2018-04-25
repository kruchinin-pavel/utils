package org.kpa.util;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class MultiIterable<T extends Comparable<T>> implements Iterable<T> {
    private final Iterable<Iterable<? extends T>> iterables;
    private final Comparator<T> comparator;

    public static <R extends Comparable<R>> Iterable<R> create(Iterable<Iterable<? extends R>> iterables) {
        return new MultiIterable<R>(iterables, Comparator.naturalOrder());
    }

    public static <R extends Comparable<R>> Iterable<R> create(Iterable<? extends R>... iterables) {
        List<Iterable<? extends R>> iterables1 = new ArrayList<>(Arrays.asList(iterables));
        iterables1.removeAll(Collections.singleton(null));
        return new MultiIterable<>(iterables1, Comparator.naturalOrder());
    }

    public MultiIterable(Iterable<Iterable<? extends T>> iterables, Comparator<T> comparator) {
        this.iterables = iterables;
        this.comparator = comparator;
    }

    @Override
    public Iterator<T> iterator() {
        List<PeekIterator<? extends T>> iterators = new ArrayList<>(StreamSupport.stream(iterables.spliterator(), false)
                .map(PeekIterator::wrap)
                .collect(Collectors.toList()));

        return new Iterator<T>() {
            private PeekIterator<? extends T> iter;

            @Override
            public boolean hasNext() {
                if (iter != null) {
                    if (!iter.hasNext() || (iterators.size() > 1 && comparator.compare(iter.peekNext(), iterators.get(1).peekNext()) > 0)) {
                        iter = null;
                    }
                }
                if (iter == null) {
                    sort(iterators);
                    if (iterators.size() > 0) {
                        iter = iterators.get(0);
                    }
                }
                return iter != null && iter.hasNext();
            }

            @Override
            public T next() {
                return this.iter.next();
            }
        };
    }

    private void sort(List<PeekIterator<? extends T>> iterators) {
        AtomicBoolean needRemove = new AtomicBoolean();
        Collections.sort(iterators, (o1, o2) -> {
            try {
                T v1 = o1.peekNext();
                T v2 = o2.peekNext();
                if (v1 == null && v2 == null) {
                    needRemove.set(true);
                    return 0;
                } else if (v1 == null) {
                    needRemove.set(true);
                    return 1;
                } else if (v2 == null) {
                    needRemove.set(true);
                    return -1;
                }
                return comparator.compare(v1, v2);
            } catch (NullPointerException e) {
                throw new RuntimeException(e);
            }
        });
        if (needRemove.get()) {
            iterators.removeIf(i -> !i.hasNext());
        }
    }

    public Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    private static class PeekIterator<T> implements Iterator<T> {
        private final Iterator<T> iterator;
        private T next;

        public PeekIterator(Iterator<T> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            if (next == null && iterator.hasNext()) {
                next = iterator.next();
            }
            return next != null;
        }

        @Override
        public T next() {
            hasNext();
            T next = this.next;
            this.next = null;
            return next;
        }

        public T peekNext() {
            hasNext();
            return next;
        }

        public static <R> PeekIterator<R> wrap(Iterable<R> iterable) {
            Preconditions.checkNotNull(iterable, "iterable is null");
            return new PeekIterator<R>(iterable.iterator());
        }

    }
}
