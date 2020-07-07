package org.kpa.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.BOMInputStream;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Json {

    private static ObjectMapper mapper = new ObjectMapper().disable(SerializationFeature.INDENT_OUTPUT);
    private static ObjectMapper mapperFormatted = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static ObjectMapper getMapper() {
        if (mapper == null) {
            mapper = new ObjectMapper().disable(SerializationFeature.INDENT_OUTPUT);
            config(mapper);
        }
        return mapper;
    }

    private static ObjectMapper getMapperFormatted() {
        if (mapperFormatted == null) {
            mapperFormatted = new ObjectMapper().disable(SerializationFeature.INDENT_OUTPUT);
            config(mapperFormatted);
        }
        return mapperFormatted;
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
                    new InputStreamReader(new BOMInputStream(FileUtils.getInputStream(fileName)), Charset.defaultCharset()), clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing file: [" + fileName + "]", e);
        }
    }

    public static void writeFile(String fileName, Object value) {
        try {
            Files.write(Paths.get(fileName), Collections.singletonList(getMapperFormatted().writeValueAsString(value)), Charset.defaultCharset());
        } catch (Exception e) {
            throw new RuntimeException("Error writing file: [" + fileName + "]", e);
        }
    }

    public static <T> T readObject(String string, Class<T> clazz) {
        try {
            return getMapper().readValue(new StringReader(string), clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing string: [" + string + "]", e);
        }
    }

    public static void toFile(String fileName, Collection<?> list, OpenOption... options) {
        toFile(fileName, list.stream(), options);
    }

    public static void toFile(String fileName, Stream<?> stream, OpenOption... options) {
        try {
            Files.write(Paths.get(fileName),
                    stream.map(Json::writeObject).collect(Collectors.toList()), options);
        } catch (IOException e1) {
            throw new RuntimeException(e1);
        }
    }

    public static String writeObject(Object val) {
        try {
            return getMapper().writeValueAsString(val);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> list(String... fileNames) {
        return FileUtils.list(fileNames).stream()
                .filter(fName -> "json".equals(FilenameUtils.getExtension(fName)))
                .collect(Collectors.toList());
    }

    public static <R> Iterable<R> iterableFile(String fileName, Class<R> clazz) {
        return iterableFile(fileName, clazz, null);
    }

    public static <R> Iterable<R> iterableFile(String fileName, Class<R> clazz, BiConsumer<R, FileRef> postProcess) {
        return iterableFile(fileName, clazz, postProcess, 0);
    }

    public static <R> Iterable<R> iterableFile(String fileName, Class<R> clazz, BiConsumer<R, FileRef> postProcess, int startFrom) {
        return new JsonFile<>(fileName, clazz, postProcess, startFrom);
    }

    private static class JsonFile<T> implements Iterable<T> {
        private final String fileName;
        private final Class<T> clazz;
        private BiConsumer<T, FileRef> postProcess = null;
        private final int startFrom;

        JsonFile(String fileName, Class<T> clazz, BiConsumer<T, FileRef> postProcess, int startIndex) {
            this.fileName = fileName;
            this.clazz = clazz;
            this.postProcess = postProcess;
            this.startFrom = startIndex;
        }

        @Override
        public Iterator<T> iterator() {
            final AtomicLong lineCounter = new AtomicLong();
            final Iterator<String> stringIterator = Utils.stringIterator(fileName, startFrom, lineCounter);

            return new Iterator<T>() {
                T next = null;

                @Override
                public boolean hasNext() {
                    String line;
                    try {
                        while (next == null && (line = stringIterator.next()) != null) {
                            next = readObject(line, clazz);
                            if (postProcess != null)
                                postProcess.accept(next, new FileRef(Paths.get(fileName), lineCounter.get()));
                            break;
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error reading file: " + fileName + ":" + lineCounter.get() + " msg:" + e.getMessage(), e);
                    }
                    return next != null;
                }

                @Override
                public T next() {
                    hasNext();
                    T obj = this.next;
                    this.next = null;
                    return obj;
                }

                @Override
                public String toString() {
                    return "JsonIterator{" +
                            "fileName='" + fileName + '\'' +
                            ", next='" + next + '\'' +
                            ", lineCounter='" + lineCounter.get() + '\'' +
                            '}';
                }
            };
        }


    }
}
