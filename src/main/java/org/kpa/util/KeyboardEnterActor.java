package org.kpa.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.kpa.util.Props.getProperty;


/**
 * Created by krucpav on 15.10.16.
 */
public class KeyboardEnterActor {
    private static final Logger logger = LoggerFactory.getLogger(KeyboardEnterActor.class);
    private static KeyboardEnterActor instance;
    private final List<Runnable> actors = new ArrayList<>();
    private final Thread thr;

    synchronized public static void stopCurrentThreadOnEnter() {
        logger.info("You can press {Enter} to make application exit.");
        final Thread thread = Thread.currentThread();
        listen(thread::interrupt);
    }

    synchronized public static void listen(Runnable runnable) {
        get();
        instance.actors.add(runnable);
    }

    private static KeyboardEnterActor get() {
        if (instance == null) {
            instance = new KeyboardEnterActor();
        }
        return instance;
    }

    public static void await() throws InterruptedException {
        get().join();
    }

    private KeyboardEnterActor() {
        File exitFile = new File(getProperty("exit_on_file", "Exit if file found.", ""));
        logger.info("Keyboard enter listener created. Exit file (will exit on presence of that file): {}", exitFile);
        thr = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1000);
                    if (System.in.available() > 0) {
                        logger.warn("Symbols are available in System.in.");
                        actors.forEach(InsecureConsumer.secure(Runnable::run));
                        Thread.currentThread().interrupt();
                    } else if (exitFile.exists()) {
                        logger.warn("Exit file found {}. call exit.", exitFile);
                        actors.forEach(InsecureConsumer.secure(Runnable::run));
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (IOException e) {
                logger.error("Error in io. Shutting down app.", e);
                System.exit(1);
            } catch (InterruptedException e) {
                logger.error("Interrupted.");
                Thread.currentThread().interrupt();
            }

        }, "keyb-actor");
        thr.setDaemon(true);
        thr.start();
    }

    public void join() throws InterruptedException {
        thr.join();
    }

    private static final AtomicBoolean set = new AtomicBoolean();

    public static void exitOnKeybEnter() {
        if (set.compareAndSet(false, true)) {
            logger.info("You can press {Enter} to make application exit.");
            listen(() -> {
                logger.info("Stop actor called. Exit(0).");
                System.exit(0);
            });
        }
    }
}
