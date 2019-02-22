package org.kpa.util;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public interface StoredArray<T> extends List<T>, AutoCloseable {

    List<T> get();

    @Override
    void close();

    @Override
    default boolean isEmpty() {
        return size()==0;
    }

    @Override
    default boolean contains(Object o) {
        throw new UnsupportedOperationException("Not supported");
    }

    @NotNull
    @Override
    default Iterator<T> iterator() {
        throw new UnsupportedOperationException("Not supported");
    }

    @NotNull
    @Override
    default Object[] toArray() {
        throw new UnsupportedOperationException("Not suppoerted");
    }

    @NotNull
    @Override
    default <T> T[] toArray(@NotNull T[] a) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    default boolean remove(Object o) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    default boolean containsAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    default boolean addAll(int index, @NotNull Collection<? extends T> c) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    default boolean removeAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    default boolean retainAll(@NotNull Collection<?> c) {
        return false;
    }

    @Override
    default T set(int index, T element) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    default void add(int index, T element) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    default T remove(int index) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    default int indexOf(Object o) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    default int lastIndexOf(Object o) {
        throw new UnsupportedOperationException("Not supported");
    }

    @NotNull
    @Override
    default ListIterator<T> listIterator() {
        throw new UnsupportedOperationException("Not supported");
    }

    @NotNull
    @Override
    default ListIterator<T> listIterator(int index) {
        throw new UnsupportedOperationException("Not supported");
    }

}
