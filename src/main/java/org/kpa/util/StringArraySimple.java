package org.kpa.util;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class StringArraySimple implements StringArray {
    private List<String[]> list = new LinkedList<>();

    @Override
    public StringArray add(Collection<String[]> strings) {
        list.addAll(strings);
        return this;
    }

    @Override
    public StringArray add(String[] strings) {
        list.add(strings);
        return this;
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
    public void close() {

    }
}
