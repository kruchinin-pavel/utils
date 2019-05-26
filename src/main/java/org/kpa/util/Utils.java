package org.kpa.util;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.supercsv.io.CsvMapReader;
import org.supercsv.prefs.CsvPreference;

import java.io.*;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;

public class Utils {
    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    public static AutoCloseableIterator<Map<String, String>> readCsv(String fileName) {
        return readCsv(fileName, CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE);
    }

    public static AutoCloseableIterator<Map<String, String>> readCsv(String fileName, CsvPreference pref) {
        try {
            Path path = FileUtils.path(fileName);
            InputStream is = Files.newInputStream(path);
            if (path.getFileName().toString().toLowerCase().endsWith(".gz")) is = new GZIPInputStream(is);
            return readCsv(new InputStreamReader(is), Integer.MAX_VALUE, pref);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static AutoCloseableIterator<Map<String, String>> readRomasCsv(String fileName) {
        AtomicLong lineCounter = new AtomicLong();
        AutoCloseableIterator<String> iter = stringIterator(fileName, 0, lineCounter);
        RomasCsv csv = new RomasCsv(iter.next());
        return new AutoCloseableIterator<Map<String, String>>() {
            @Override
            public void close() {
                iter.close();
            }

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public Map<String, String> next() {
                try {
                    return csv.parse(iter.next());
                } catch (Exception e) {
                    throw new RuntimeException("Error parsing file: " + fileName + " at line "
                            + lineCounter.get() + ": " + e.getMessage(), e);
                }
            }
        };
    }

    public static AutoCloseableIterator<Map<String, String>> readCsv(Reader reader, int maxRows) throws IOException {
        return readCsv(reader, maxRows, CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE);
    }

    public static AutoCloseableIterator<Map<String, String>> readCsv(Reader reader, int maxRows, CsvPreference pref) throws IOException {
        return new AutoCloseableIterator<Map<String, String>>() {
            private final AtomicLong counter = new AtomicLong();
            private final CsvMapReader csvMapReader = new CsvMapReader(reader, pref);
            private final String[] header = csvMapReader.getHeader(true);
            private final AtomicBoolean closed = new AtomicBoolean();
            private Map<String, String> val;

            @Override
            public boolean hasNext() {
                try {
                    if (counter.get() >= maxRows) return false;
                    if (val == null && !closed.get()) {
                        val = csvMapReader.read(header);
                    }
                    if (val == null) {
                        close();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return val != null;
            }

            @Override
            public Map<String, String> next() {
                hasNext();
                Map<String, String> val = this.val;
                this.val = null;
                counter.incrementAndGet();
                return val;
            }

            @Override
            public void close() {
                try {
                    if (closed.compareAndSet(false, true)) {
                        reader.close();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    public static void startInWrap(String[] args, InsecureConsumer<String[]> run) {
        try {
            logger.info("Starting with {}", localHostAndUserName());
            long ct = System.currentTimeMillis();
            run.accept(args);
            logger.info("Completed running for {} seconds.", (System.currentTimeMillis() - ct) / 1000);
        } catch (Throwable e) {
            logger.error("Error happened. Exit(1)", e);
            System.exit(1);
        }
    }


    public static String localHostAndUserName() {
        String s = fillUserAndHost("%s@%s:%s") + (logger.isDebugEnabled() ? ", debug log" : "");
        logger.debug("Debug control message");
        return s;
    }

    public static String fillUserAndHost(String fmt) {
        String hostName;
        String userDir = System.getProperty("user.dir");
        try {
            String userHome = System.getProperty("user.home");
            userDir = userDir.replace(userHome, "~" + File.separatorChar);
            userDir = userDir.replace("" + File.separatorChar + File.separatorChar, "" + File.separatorChar);
            java.net.InetAddress localMachine = null;
            localMachine = java.net.InetAddress.getLocalHost();
            hostName = localMachine.getHostName();
        } catch (Exception e) {
            logger.warn("Can't get hostname. Looking at /etc/hostname. Msg: {}", e.getMessage(), e);
            try {
                hostName = Files.lines(Paths.get("/etc/hostname")).filter(v -> !Strings.isNullOrEmpty(v))
                        .collect(Collectors.joining(","));
            } catch (IOException e1) {
                logger.warn("Can't get content of /etc/hostname. Hostname set unknown: {} ", e.getMessage(), e);
                hostName = "unknown";
            }
        }
        return String.format(fmt, System.getProperty("user.name"), hostName, userDir);
    }


    public static <T extends ChronoBased> Iterable<T> sorted(Iterable<T> iterable) {
        return stream(iterable).sorted().collect(Collectors.toList());
    }

    public static Iterable<ChronoBased> toChrono(Iterable<? extends ChronoBased> iterable) {
        if (iterable == null) return null;
        return () -> new Iterator<ChronoBased>() {
            private final Iterator<? extends ChronoBased> iterator = iterable.iterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public ChronoBased next() {
                return iterator.next();
            }
        };
    }

    public static <T> List<T> asList(Iterable<T> iterable) {
        return stream(iterable).collect(Collectors.toList());
    }

    public static <T> Stream<T> stream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    public static String valOrNull(String val) {
        return Strings.isNullOrEmpty(val) ? null : val;
    }

    public static <T, R> Iterable<R> convert(Iterable<T> rcs, Function<T, R> converter) {
        return convert(rcs, converter, 0);
    }

    public static <T, R> Iterable<R> convert(Iterable<T> rcs, Function<T, R> converter, int startFrom) {
        return () -> {
            Iterator<T> iteratorR = rcs.iterator();
            Iterator<R> iterator = new Iterator<R>() {
                @Override
                public boolean hasNext() {
                    return iteratorR.hasNext();
                }

                @Override
                public R next() {
                    return converter.apply(iteratorR.next());
                }
            };
            int index = 0;
            while (index < startFrom && iteratorR.hasNext()) {
                iteratorR.next();
                index++;
            }
            return iterator;
        };
    }

    public static List<Integer> getFreePorts(int count) {
        List<ServerSocket> sockets = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                sockets.add(new ServerSocket(0));
            }
            return sockets.stream().map(ServerSocket::getLocalPort).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            sockets.forEach(s -> {
                try {
                    s.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    public static AutoCloseableIterator<String> stringIterator(String fileName, int startFrom, AtomicLong _lineCounter) {
        Path path = FileUtils.path(fileName);
        BufferedReader reader = FileUtils.newBufferedReader(path);
        final AtomicLong lineCounter = _lineCounter == null ? new AtomicLong(startFrom - 1) : _lineCounter;
        AutoCloseableIterator<String> iter = new AutoCloseableIterator<String>() {
            private String string;

            @Override
            public void close() {
                try {
                    reader.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean hasNext() {
                while (string == null) {
                    try {
                        string = reader.readLine();
                        lineCounter.incrementAndGet();
                        if (string == null) break;
                        if (Strings.isNullOrEmpty(string.trim())) string = null;
                    } catch (IOException e) {
                        throw new RuntimeException();
                    }
                }
                return string != null;
            }

            @Override
            public String next() {
                hasNext();
                String string = this.string;
                this.string = null;
                return string;
            }
        };
        while (lineCounter.get() < startFrom && iter.hasNext()) {
            iter.next();
        }
        return iter;
    }

}
