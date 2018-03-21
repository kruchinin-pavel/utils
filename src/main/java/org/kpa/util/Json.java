package org.kpa.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.base.Strings;
import org.apache.commons.io.input.BOMInputStream;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Json {

    private static final ObjectMapper mapper = new ObjectMapper().disable(SerializationFeature.INDENT_OUTPUT);
    private static final ObjectMapper mapperFormatted = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    static {
        config(mapper);
        config(mapperFormatted);
    }

    private static void config(ObjectMapper mapper) {
        mapper.registerModule(new ParameterNamesModule())
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public static <T> Iterable<T> readObjects(Iterable<String> lines, Class<T> clazz) {
        return () -> {
            Iterator<String> iter = lines.iterator();
            return new Iterator<T>() {
                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public T next() {
                    return readObject(iter.next(), clazz);
                }

            };
        };
    }

    public static <T> T readFile(String fileName, Class<T> clazz) {
        try {
            return mapperFormatted.readValue(
                    new InputStreamReader(new BOMInputStream(new FileInputStream(fileName)), Charset.defaultCharset()), clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing file: [" + fileName + "]", e);
        }
    }

    public static void writeFile(String fileName, Object value) {
        try {
            Files.write(Paths.get(fileName), Collections.singletonList(mapperFormatted.writeValueAsString(value)), Charset.defaultCharset());
        } catch (Exception e) {
            throw new RuntimeException("Error writing file: [" + fileName + "]", e);
        }
    }

    public static <T> T readObject(String string, Class<T> clazz) {
        try {
            return mapper.readValue(new StringReader(string), clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing string: [" + string + "]", e);
        }
    }

    public static void toFile(String fileName, Collection<?> list) {
        toFile(fileName, list.stream());
    }
    public static void toFile(String fileName, Stream<?> stream) {
        try {
            Files.write(Paths.get(fileName),
                    stream.map(Json::writeObject).collect(Collectors.toList()));
        } catch (IOException e1) {
            throw new RuntimeException(e1);
        }
    }

    public static String writeObject(Object val) {
        try {
            return mapper.writeValueAsString(val);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <R> Iterable<R> iterableFile(String fileName, Class<R> clazz) {
        return iterableFile(fileName, clazz, null);
    }

    public static <R> Iterable<R> iterableFile(String fileName, Class<R> clazz, BiConsumer<R, FileRef> postProcess) {
        return new JsonFile<>(fileName, clazz, postProcess);
    }

    private static class JsonFile<T> implements Iterable<T> {
        private final String fileName;
        private final Class<T> clazz;
        private BiConsumer<T, FileRef> postProcess = null;

        JsonFile(String fileName, Class<T> clazz, BiConsumer<T, FileRef> postProcess) {
            this.fileName = fileName;
            this.clazz = clazz;
            this.postProcess = postProcess;
        }

        @Override
        public Iterator<T> iterator() {
            Path path = FileUtils.path(fileName);
            BufferedReader reader = FileUtils.newBufferedReader(path);
            AtomicLong lineCounter = new AtomicLong(-1);
            return new Iterator<T>() {
                T next = null;

                @Override
                public boolean hasNext() {
                    String line;
                    try {
                        while ((line = reader.readLine()) != null) {
                            lineCounter.incrementAndGet();
                            if (Strings.isNullOrEmpty(line.trim())) {
                                continue;
                            }
                            next = readObject(line, clazz);
                            if (postProcess != null) postProcess.accept(next, new FileRef(path, lineCounter.get()));
                            break;
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error reading file: " + path + ":" + lineCounter.get() + " msg:" + e.getMessage(), e);
                    }
                    return next != null;
                }

                @Override
                public T next() {
                    if (!hasNext()) {
                        return null;
                    }
                    T obj = this.next;
                    this.next = null;
                    return obj;
                }

            };
        }


    }
}
