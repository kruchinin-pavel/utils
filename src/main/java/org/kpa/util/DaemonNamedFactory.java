package org.kpa.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Created with IntelliJ IDEA.
 * User: krucpav
 * Date: 15.10.13
 * Time: 23:18
 * To change this template use File | Settings | File Templates.
 */
public class DaemonNamedFactory implements ThreadFactory {
    private static final Logger log = LoggerFactory.getLogger(DaemonNamedFactory.class);
    private String name;
    private final boolean isDaemon;
    private AtomicInteger cnt = new AtomicInteger();

    public DaemonNamedFactory(String name) {
        this(name, true);
    }

    public DaemonNamedFactory(String name, boolean isDaemon) {
        this.name = name;
        this.isDaemon = isDaemon;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        thread.setDaemon(isDaemon);
        thread.setName(name + "-" + cnt.incrementAndGet());
        return thread;
    }

    public static ExecutorService newSingleThreadExecutor(String name) {
        return Executors.newSingleThreadExecutor(new DaemonNamedFactory(name));
    }

    public static ScheduledExecutorService newScheduledExecutorService(String name){
        return Executors.newScheduledThreadPool(1, new DaemonNamedFactory(name));
    }

    public static ExecutorService newCachedThreadPool(String name) {
        return Executors.newCachedThreadPool(new DaemonNamedFactory(name));
    }

    public static ExecutorService newCachedThreadPool(String name, boolean isDaemon) {
        return Executors.newCachedThreadPool(new DaemonNamedFactory(name, isDaemon));
    }

    public static void schedule(Runnable processor, String name, long delay_millis) {
        schedule(processor, name, delay_millis, e -> {
            log.error("Uncaught error. STOP!!! {}", e.getMessage(), e);
            System.exit(1);
        });
    }

    public static void schedule(Runnable processor, String name, long delay_millis, Consumer<Throwable> handler) {
        TurnoverCounter errCounter = new TurnoverCounter(60_000, 1);
        schedule(() -> {
            try {
                if (errCounter.canAddValue(1.)) {
                    processor.run();
                } else {
                    log.warn("Call ignored as errCounter not yet enabled: {}", errCounter.getLastVal());
                }
            } catch (Exception e) {
                errCounter.addValueSec(1.);
                if (handler != null) handler.accept(e);
            }
            return true;
        }, name, delay_millis);
    }

    public static void schedule(Callable<Boolean> processor, String name, long delay_millis) {
        ScheduledExecutorService service = DaemonNamedFactory.newScheduledExecutorService(name);
        service.scheduleAtFixedRate(() -> {
            try {
                if (!processor.call()) {
                    service.shutdown();
                }
            } catch (Exception e) {
                log.error("Uncaught error in {} during processing. STOP!!!.Msg: ", name, e.getMessage(), e);
                System.exit(1);
            }
        }, delay_millis, delay_millis, TimeUnit.MILLISECONDS);
    }
}
