package org.kpa.util;

import org.reflections.Reflections;

import java.util.HashSet;
import java.util.Set;

public class Reflection {
    public static <T> Set<Class<? extends T>> findClassExtensions(final Class<? extends T> type) {
        String packStr = type.getName();
        packStr = packStr.substring(0, packStr.lastIndexOf("."));
        Reflections ref = new Reflections(packStr);
        Set<Class<? extends T>> result = new HashSet<>();
        result.addAll(ref.getSubTypesOf((Class<T>) type));
        return result;
    }
}
