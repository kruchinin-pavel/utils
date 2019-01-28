package org.kpa.util;

import java.util.Collection;
import java.util.List;

public interface StringArray extends AutoCloseable {

    StringArray add(Collection<String[]> strings);

    StringArray add(String[] strings);

    String[] get(int index);

    List<String[]> subList(int startIndex, int maxCount);

    List<String[]> get();

    int size();

    void clear();

    @Override
    void close();
}
