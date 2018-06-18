package org.kpa.util.interop;


import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import org.kpa.util.Json;
import org.kpa.util.interop.msgs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.kpa.util.Utils.getFreePorts;

public class Python implements AutoCloseable {
    private final Zmq zmq;
    private final ExtProcess process;
    private final Logger logger = LoggerFactory.getLogger(Python.class);
    private Deque<Object> replies = new LinkedBlockingDeque<>();
    private volatile long isAliveTmout = -1;
    private final Thread thread;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object monitor = new Object();

    private Python(String scriptPath, String pythonAddr, int javaPort) {
        if (scriptPath != null) {
            File dir = Paths.get(scriptPath).getParent().toFile();
            process = new ExtProcess(dir,
                    outStr -> logger.info(">> {}", outStr),
                    "python3.6",
                    scriptPath,
                    pythonAddr,
                    "tcp://localhost:" + javaPort);
            pythonAddr = "tcp://localhost:" + pythonAddr;
        } else {
            process = null;
        }
        zmq = Zmq.pubSub(javaPort, pythonAddr);
        thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                String str = zmq.tryReceive(0);
                if (!Strings.isNullOrEmpty(str)) {
                    try {
                        Message msg = Json.readObject(str, Message.class);
                        if (msg instanceof Bye) isAliveTmout = -1;
                        else isAliveTmout = System.currentTimeMillis() + 3000;
                        if (!(msg instanceof Heartbeat)) {
                            replies.add(str);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Error processing incoming event: " + str, e);
                    }
                }
                if (isAliveTmout - System.currentTimeMillis() < 1000) {
                    zmq.send(Json.writeObject(new TestRequest()));
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        thread.start();
    }

    public long getIsAliveTmout() {
        return isAliveTmout;
    }

    public boolean isAlive() {
        return isAliveTmout > System.currentTimeMillis();
    }

    public boolean send(Message str) {
        return zmq.send(Json.writeObject(str));
    }

    public boolean send(String str) {
        return zmq.send(str);
    }

    public String listen() {
        return zmq.tryReceive(30_000);
    }

    public void waitForLive() throws TimeoutException, InterruptedException {
        await(() -> isAlive(), 10_000);
    }

    public void waitForNotLive() throws TimeoutException, InterruptedException {
        await(() -> !isAlive(), 10_000);
    }

    private void await(Supplier<Boolean> condition, long timeoutMillis) throws InterruptedException, TimeoutException {
        long upTime = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < upTime) {
            if (condition.get()) {
                return;
            }
            synchronized (monitor) {
                monitor.wait(100);
            }
        }
        throw new TimeoutException();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        send(new Close());
        try {
            waitForNotLive();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        thread.interrupt();
        zmq.close();
        if (process != null) process.close();
    }

    @Override
    public String toString() {
        return "Python{" +
                "process=" + process +
                ", alive=" + isAlive() +
                '}';
    }

    public static Python connectToExternal(int javaPort, String pythonAddr) {
        return new Python(null, pythonAddr, javaPort);
    }

    public static Python createSubprocess(String scriptPath) {
        List<Integer> ports = getFreePorts(2);
        return new Python(scriptPath, Integer.toString(ports.get(0)), ports.get(1));
    }
}
