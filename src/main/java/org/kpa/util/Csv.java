package org.kpa.util;

import org.supercsv.io.CsvMapReader;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;

import java.io.BufferedWriter;
import java.io.EOFException;
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
        toFile(fileName, CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE, stream);
    }

    public static void toFile(String fileName, CsvPreference preference, Stream<Map<String, Object>> stream) {
        try (CloseableFlushableConsumer<Map<String, Object>> var = writeTo(fileName, preference)) {
            stream.forEach(var::accept);
        }
    }

    public static <T extends Map<String, Object>> CloseableFlushableConsumer<T> writeTo(String fileName, CsvPreference preference) {

        return new CloseableFlushableConsumer<T>() {
            final BufferedWriter writer = FileUtils.newBufferedWriter(fileName);
            final CsvMapWriter csvMapWriter = new CsvMapWriter(writer, preference);
            private String[] cols = null;

            @Override
            public void close() {
                try {
                    flush();
                    csvMapWriter.close();
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
                        csvMapWriter.writeHeader(cols);
                    }
                    csvMapWriter.write(vals, cols);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }

            @Override
            public void flush() throws IOException {
                writer.flush();
                csvMapWriter.flush();
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
                    } catch (EOFException e) {
                        close();
                        return false;
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
                        // ignore as we just closing resource
                    }
                }
            };
        } catch (EOFException e) {
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> Iterable<? extends T> fromCsv(String fileName, BiFunction<Map<String, String>, FileRef, T> builder) {
        return fromCsv(fileName, CsvPreference.EXCEL_PREFERENCE, builder);
    }

    public static <T> Iterable<? extends T> fromCsv(String fileName, CsvPreference pref, BiFunction<Map<String, String>, FileRef, T> builder) {
        final Iterator<Map<String, String>> csvIter = Csv.readFrom(fileName, pref);
        if (csvIter == null) return null;
        return () -> new Iterator<T>() {
            private final AtomicLong counter = new AtomicLong(-1);

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

