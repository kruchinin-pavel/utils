package org.kpa.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created with IntelliJ IDEA.
 * User: krucpav
 * Date: 15.10.13
 * Time: 23:18
 * To change this template use File | Settings | File Templates.
 */
public class DaemonNamedFactory implements ThreadFactory {
    private String name;
    private AtomicInteger cnt = new AtomicInteger();

    public DaemonNamedFactory(String name) {
        this.name = name;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        thread.setName(name + "-" + cnt.incrementAndGet());
        return thread;
    }

    public static ExecutorService newSingleThreadExecutor(String name) {
        return Executors.newSingleThreadExecutor(new DaemonNamedFactory(name));
    }

    public static ScheduledExecutorService newScheduledExecutorService(String name) {
        return Executors.newScheduledThreadPool(1, new DaemonNamedFactory(name));
    }

    public static ExecutorService newCachedThreadPool(String name) {
        return Executors.newCachedThreadPool(new DaemonNamedFactory(name));
    }
}
