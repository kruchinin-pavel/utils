package org.kpa.util.camel;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileFilter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.support.TypeConverterSupport;
import org.apache.commons.io.FilenameUtils;
import org.kpa.util.InsecureConsumer;
import org.kpa.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

public class BackgroundCamelFileService<T> implements AutoCloseable {
    public static final String CS = Charset.defaultCharset().toString();
    private final Class<T> clazz;
    private DefaultCamelContext ctx;
    public static final String FNAME = "name";
    private static final Logger logger = LoggerFactory.getLogger(BackgroundCamelFileService.class);
    private final AtomicLong counter = new AtomicLong();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private AtomicLong runUpTime = new AtomicLong(Long.MAX_VALUE);
    private AtomicLong idleUpTime = new AtomicLong(Long.MAX_VALUE);
    private long idleTimeoutMillis = -1;
    private Function<GenericFile, Boolean> defaultFilter;
    private Function<GenericFile, Boolean> fileFilter = f -> true;
    public static final String URI = "file://%s?" +
            "filter=#fileFilter&" +
            "maxMessagesPerPoll=1&" +
            "sendEmptyMessageWhenIdle=true&" +
            "readLock=markerFile&" +
            "shuffle=true&" +
            "readLockDeleteOrphanLockFiles=false&" +
            "charset=" + Charset.defaultCharset();

    public long getCounter() {
        return counter.get();
    }

    public long awaitCounter(long val) {
        while (counter.get() < val && !isStopped()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return counter.get();
    }

    private final InsecureConsumer<T> consumer;

    public boolean isStopped() {
        return stopped.get();
    }

    public BackgroundCamelFileService(Class<T> clazz) {
        this(clazz, null);
    }

    private final AtomicReference<T> currentTask = new AtomicReference<>();

    public BackgroundCamelFileService(Class<T> clazz, InsecureConsumer<T> consumer) {
        this.clazz = clazz;
        this.consumer = consumer;
        defaultFilter = file -> !isLockedOrDir(file) && "json".equals(FilenameUtils.getExtension(file.getFileName()));
        Thread thread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    if (stopped.get()) {
                        break;
                    }
                    long ct = System.currentTimeMillis();
                    if (ct > idleUpTime.get()) {
                        logger.info("idleUpTime finished.");
                        break;
                    }
                    if (ct > runUpTime.get()) {
                        logger.info("runUpTime finished.");
                        break;
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                logger.info("Shutdown service");
                close();
            }
        });
        thread.setDaemon(true);
        thread.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            close();
            await();
        }));
    }

    public void start(String eventInputDir, String eventOutputDir) {
        try {

            SimpleRegistry registry = new SimpleRegistry();
            registry.put("fileFilter", (GenericFileFilter) file -> defaultFilter.apply(file) && fileFilter.apply(file));
            ctx = new DefaultCamelContext(registry);
            ctx.setStreamCaching(true);

            ctx.getTypeConverterRegistry().addTypeConverter(clazz, String.class, new TypeConverterSupport() {
                @Override
                public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
                    return Json.readObject(value.toString(), type);
                }
            });
            ctx.getTypeConverterRegistry().addTypeConverter(String.class, clazz, new TypeConverterSupport() {
                @Override
                public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
                    return (T) Json.writeObject(value);
                }
            });

            ctx.addRoutes(new RouteBuilder() {
                @SuppressWarnings("unchecked")
                @Override
                public void configure() {

                    RouteDefinition route = from(String.format(URI, new File(eventInputDir).getAbsolutePath()));
                    route.doTry()
                            .convertBodyTo(String.class, CS)
                            .convertBodyTo(clazz, CS)
                            .process(BackgroundCamelFileService.this::eventCome)
                            .doCatch(Exception.class)
                            .process(BackgroundCamelFileService.this::exceptionCome)
                            .end();
                    if (eventOutputDir != null) {
                        route.choice()
                                .when(body().isNull()).stop()
                                .otherwise()
                                .convertBodyTo(String.class, CS)
                                .to("file://" + new File(eventOutputDir).getAbsolutePath() + "?" +
                                        "fileName=${in.header." + FNAME + "}&" +
                                        "charset=" + Charset.defaultCharset());
                    }
                }
            });
            ctx.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void exceptionCome(Exchange exchange) {
        Throwable caused = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        if (caused == null) {
            logger.warn("Empty exception routed to here.");
        }
        Throwable rootCause = Throwables.getRootCause(caused);
        if ("reader must be specified".equalsIgnoreCase(rootCause.getMessage()) || (rootCause instanceof FileNotFoundException)) {
            logger.warn("Looks like file disappeared while reading: " + caused.getMessage());
            exchange.getIn().setBody(null);
        } else {
            logger.error("Unexpected error caught. Stop running: {}", caused.getMessage(), caused);
            close();
        }
    }

    public void setFileFilter(Function<GenericFile, Boolean> fileFilter) {
        Preconditions.checkNotNull(fileFilter);
        this.fileFilter = fileFilter;
    }


    protected void eventCome(Exchange exch) {
        long ct = System.currentTimeMillis();
        T incomeEvent = null;
        try {
            incomeEvent = exch.getIn().getBody(clazz);
            if (incomeEvent == null) {
                if (idleUpTime.get() == Long.MAX_VALUE) {
                    eventCome((T) null);
                    fetchIdleTimout();
                }
                return;
            }
            Preconditions.checkArgument(currentTask.compareAndSet(null, incomeEvent));
            idleUpTime.set(Long.MAX_VALUE);
            logger.info("Processing input event {} of class {}", incomeEvent, incomeEvent.getClass());
            eventCome(incomeEvent);
            logger.info("Processed in {}ms. of input event {} of class {}", System.currentTimeMillis() - ct, incomeEvent.getClass());
        } catch (Exception e) {
            logger.error("Error processing incoming event: ", e);
        } finally {
            if (incomeEvent != null) {
                Preconditions.checkArgument(currentTask.compareAndSet(incomeEvent, null));
            }
            counter.incrementAndGet();
        }
    }

    private void fetchIdleTimout() {
        if (idleTimeoutMillis > 0) {
            idleUpTime.set(System.currentTimeMillis() + idleTimeoutMillis);
            logger.info("Null event come. Updating idleUpTime: {}", new Date(idleUpTime.get()));
        } else idleUpTime.set(Long.MAX_VALUE);
    }

    protected void eventCome(T val) throws Exception {
        if (consumer != null) {
            consumer.accept(val);
        } else {
            logger.warn("Unconsumed event: {}", val);
        }
    }

    @Override
    public void close() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        logger.info("Stop is called. Stacktrace: {}", Throwables.getStackTraceAsString(new RuntimeException()));
        new Thread(() -> {
            try {
                if (ctx != null) {
                    ctx.shutdown();
                    T lastTask = currentTask.get();
                    if (lastTask != null && lastTask instanceof Interruptable) {
                        ((Interruptable) lastTask).interrupt();
                    }
                    await();
                    logger.info("CamelContext shutted down");
                }
            } catch (Exception e) {
                logger.error("Error stopping context: {}", e.getMessage(), e);
            }
        }).start();
    }


    public void runTillMinutesOfHour(int minutes) {
        int millisToRun = millisTillMinutesOfHour(LocalDateTime.now(), minutes);
        Preconditions.checkArgument(millisToRun > 0, "Seconds to run are <=0 (%s)till proposed minutes of hour: %s", millisToRun, minutes);
        runTimeoutMillis(millisToRun);
    }

    public static int millisTillMinutesOfHour(LocalDateTime now, int minutesOfHour) {
        LocalDateTime proposed = LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), now.getHour(), minutesOfHour);
        int seconds = (int) Duration.between(now, proposed).getSeconds();
        if (seconds <= 0) return -1;
        return seconds * 1000;
    }

    public void runTimeoutMillis(int millisToRun) {
        if (millisToRun > 0) {
            long ct = System.currentTimeMillis();
            runUpTime.set(ct + millisToRun);
            logger.info("Setting {} millis to run", millisToRun);
        }
    }

    public void setIdleTimeoutMillis(long idleTimeoutMillis) {
        this.idleTimeoutMillis = idleTimeoutMillis;
        idleUpTime.set(Long.MAX_VALUE);
    }

    public void await() {
        try {
            waitUntilVar(() -> ctx.isStopped() ? -1 : 100, 100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException e) {
            // nothing to do
        } finally {
            close();
        }
    }

    public static boolean isLockedOrDir(GenericFile file) {
        Path path = Paths.get(file.getAbsoluteFilePath());
        return Files.isDirectory(path) || isLock(path) || isLock(Paths.get(path.toString() + ".camelLock"));
    }

    private static boolean isLock(Path lockFile) {
        String extension = FilenameUtils.getExtension(lockFile.toString());
        if (!extension.equalsIgnoreCase("camelLock")) {
            return false;
        }
        try {
            if (Files.exists(lockFile) && Files.getLastModifiedTime(lockFile).toMillis() < System.currentTimeMillis() - 12 * 60 * 60_000) {
                logger.info("Found pretty old camel lock file. Trying to delete it: {}", lockFile);
                Files.delete(lockFile);
                return true;
            }
        } catch (IOException e) {
            throw new RuntimeException("Error checking file: " + lockFile, e);
        }
        logger.debug("camelLock fil./e {} exists={}", lockFile, Files.exists(lockFile));

        return Files.exists(lockFile);
    }

    public static void waitUntilVar(Supplier<Integer> checker, long waitMillis) throws InterruptedException, TimeoutException {
        do {
            if (waitMillis == 0) {
                waitMillis = 10;
            }
            Thread.sleep(waitMillis);
        } while ((waitMillis = checker.get()) >= 0);
    }


}
