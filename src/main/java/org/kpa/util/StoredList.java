package org.kpa.util;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public abstract class StoredList<T> implements List<T>, AutoCloseable {

    @Override
    public abstract void close();

    @Override
    public boolean isEmpty() {
        return size()==0;
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException("Not supported");
    }

    @NotNull
    @Override
    public abstract Iterator<T> iterator();

    @NotNull
    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException("Not suppoerted");
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends T> c) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
        return false;
    }

    @Override
    public T set(int index, T element) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void add(int index, T element) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public T remove(int index) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public int indexOf(Object o) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException("Not supported");
    }

    @NotNull
    @Override
    public ListIterator<T> listIterator() {
        throw new UnsupportedOperationException("Not supported");
    }

    @NotNull
    @Override
    public ListIterator<T> listIterator(int index) {
        throw new UnsupportedOperationException("Not supported");
    }

}
