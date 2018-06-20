package org.kpa.util;

import org.supercsv.io.CsvMapReader;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class Csv {
    public static void toFile(String fileName, Stream<Map<String, Object>> stream) {
        try (CloseableFlushableConsumer<Map<String, Object>> var = writeTo(fileName)) {
            stream.forEach(var::accept);
        }
    }

    public static <T extends Map<String, Object>> CloseableFlushableConsumer<T> writeTo(String fileName) {
        return new CloseableFlushableConsumer<T>() {
            final CachedVal<CsvMapWriter> csvMapWriter = new CachedVal<>(() -> new CsvMapWriter(FileUtils.newBufferedWriter(fileName), CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE));
            private String[] cols = null;

            @Override
            public void close() {
                try {
                    csvMapWriter.get().close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public synchronized void accept(T obj) {
                try {
                    Map<String, Object> vals = obj;
                    if (cols == null) {
                        cols = vals.keySet().toArray(new String[0]);
                        csvMapWriter.get().writeHeader(cols);
                    }
                    csvMapWriter.get().write(vals, cols);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }

            @Override
            public void flush() throws IOException {
                csvMapWriter.get().flush();
            }
        };
    }

    public static AutoCloseableIterator<Map<String, String>> readFrom(String fileName) {
        return readFrom(fileName, CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE);
    }

    public static AutoCloseableIterator<Map<String, String>> readFrom(String fileName, CsvPreference preference) {
        try {
            return new AutoCloseableIterator<Map<String, String>>() {

                private final CsvMapReader csvMapReader = new CsvMapReader(FileUtils.newBufferedReader(fileName), preference);
                private final String[] header = csvMapReader.getHeader(true);
                private final AtomicBoolean closed = new AtomicBoolean();
                private Map<String, String> val;
                private final AtomicInteger counter = new AtomicInteger();

                @Override
                public boolean hasNext() {
                    try {
                        if (val == null && !closed.get()) {
                            counter.incrementAndGet();
                            val = csvMapReader.read(header);
                        }
                        if (val == null) {
                            close();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error in csv " +
                                fileName + ":" + counter.get() +
                                ". Msg:" + e.getMessage(), e);
                    }
                    return val != null;
                }

                @Override
                public Map<String, String> next() {
                    hasNext();
                    Map<String, String> val = this.val;
                    this.val = null;
                    return val;
                }

                @Override
                protected void finalize() {
                    close();
                }

                @Override
                public void close() {
                    try {
                        if (closed.compareAndSet(false, true)) {
                            csvMapReader.close();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> Iterable<? extends T> fromCsv(String fileName, BiFunction<Map<String, String>, FileRef, T> builder) {
        return fromCsv(fileName, CsvPreference.EXCEL_PREFERENCE, builder);
    }

    public static <T> Iterable<? extends T> fromCsv(String fileName, CsvPreference pref, BiFunction<Map<String, String>, FileRef, T> builder) {
        return () -> new Iterator<T>() {
            private final AtomicLong counter = new AtomicLong(-1);
            private final Iterator<Map<String, String>> csvIter = Csv.readFrom(fileName, pref);

            @Override
            public boolean hasNext() {
                return csvIter.hasNext();
            }

            @Override
            public T next() {
                T value;
                do {
                    if (!hasNext()) return null;
                    Map<String, String> next = null;
                    try {
                        next = csvIter.next();
                        counter.incrementAndGet();
                        value = builder.apply(next, new FileRef(Paths.get(fileName), counter.get()));
                    } catch (Exception e) {
                        throw new RuntimeException(String.format("Error parsing file: %s:%s valMap: %s. Msg: %s", fileName, counter.get(), next, e.getMessage()), e);
                    }
                } while (value == null);
                return value;
            }
        };
    }


}

