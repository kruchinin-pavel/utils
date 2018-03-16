package org.kpa.util;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.supercsv.io.CsvMapReader;
import org.supercsv.prefs.CsvPreference;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Utils {
    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    public static AutoCloseableIterator<Map<String, String>> readCsv(String fileName) {
        try {
            return readCsv(new InputStreamReader(Files.newInputStream(FileUtils.path(fileName))), Integer.MAX_VALUE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static AutoCloseableIterator<Map<String, String>> readCsv(Reader reader, int maxRows) throws IOException {
        return new AutoCloseableIterator<Map<String, String>>() {
            private final AtomicLong counter = new AtomicLong();
            private final CsvMapReader csvMapReader = new CsvMapReader(reader, CsvPreference.EXCEL_NORTH_EUROPE_PREFERENCE);
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
        String s = fillUserAndHost("user=%s on host=%s") + ", debug enabled = " + logger.isDebugEnabled();
        logger.debug("Debug control message");
        return s;
    }

    public static String fillUserAndHost(String fmt) {
        String hostName;
        try {
            java.net.InetAddress localMachine = null;
            localMachine = java.net.InetAddress.getLocalHost();
            hostName = localMachine.getHostName();
        } catch (UnknownHostException e) {
            hostName = "unknown";
        }
        return String.format(fmt, System.getProperty("user.name"), hostName);
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

    public static <T> Stream<T> stream(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    public static String valOrNull(String val) {
        return Strings.isNullOrEmpty(val) ? null : val;
    }
}
