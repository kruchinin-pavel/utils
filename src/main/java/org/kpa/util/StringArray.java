package org.kpa.util;

import java.util.Collection;
import java.util.List;

public interface StringArray extends AutoCloseable {

    StringArray add(Collection<String[]> strings);

    StringArray add(String[] strings);

    String[] get(int index);

    List<String[]> subList(int startIndex);

    List<String[]> get() throws InterruptedException;

    int size();

    @Override
    void close();
}
