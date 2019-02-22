package org.kpa.util;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class StringArraySimple implements StoredArray<String[]> {
    private List<String[]> list = new LinkedList<>();

    @Override
    public boolean addAll(@NotNull Collection<? extends String[]> strings) {
        return list.addAll(strings);
    }

    @Override
    public boolean add(String[] strings) {
        return list.add(strings);
    }

    @Override
    public String[] get(int index) {

        return list.get(index);
    }

    @Override
    public List<String[]> subList(int startIndex, int maxCount) {
        return Collections.unmodifiableList(list.subList(startIndex, startIndex + maxCount));
    }

    @Override
    public List<String[]> get() {
        return Collections.unmodifiableList(list);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public void clear() {
        list.clear();
    }

    @Override
    public void close() {

    }
}
