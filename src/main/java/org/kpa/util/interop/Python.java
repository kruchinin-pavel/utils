package org.kpa.util.interop;


import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import org.kpa.util.Json;
import org.kpa.util.interop.msgs.Bye;
import org.kpa.util.interop.msgs.Close;
import org.kpa.util.interop.msgs.Message;
import org.kpa.util.interop.msgs.TestRequest;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.kpa.util.Utils.getFreePorts;

public class Python<T> implements AutoCloseable {
    private final Zmq zmq;
    private final ExtProcess process;
    private Consumer<T> msgConsumer;
    private volatile long isAliveTmout = -1;
    private final Thread thread;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object monitor = new Object();
    private Class<T> userMessagesClass;

    private Python(String scriptPath, String pythonAddr, int javaPort, Consumer<String> pythonOutput) {
        if (scriptPath != null) {
            File dir = Paths.get(scriptPath).getParent().toFile();
            File script = Paths.get(scriptPath).getFileName().toFile();
            process = new ExtProcess(dir,
                    pythonOutput,
                    "python3.6",
                    script.toString(),
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
                        Object msg = tryRead(str);
                        if (msg instanceof Bye) isAliveTmout = -1;
                        else isAliveTmout = System.currentTimeMillis() + 3000;
                        if (userMessagesClass.isAssignableFrom(msg.getClass())) {
                            this.msgConsumer.accept((T) msg);
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

    public void setMsgConsumer(Class<T> userMessagesClass, Consumer<T> msgConsumer) {
        this.userMessagesClass = userMessagesClass;
        this.msgConsumer = msgConsumer;
    }


    private Object tryRead(String str) {
        try {
            return Json.readObject(str, Message.class);
        } catch (Exception e) {
            if (userMessagesClass == null) {
                throw e;
            }
            return Json.readObject(str, userMessagesClass);
        }
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

    public static <R> Python<R> connectToExternal(int javaPort, String pythonAddr) {
        return new Python<>(null, pythonAddr, javaPort, null);
    }

    public static <R> Python<R> createSubprocess(String scriptPath, Consumer<String> pythonOutput) {
        List<Integer> ports = getFreePorts(2);
        return new Python<>(scriptPath, Integer.toString(ports.get(0)), ports.get(1), pythonOutput);
    }
}
