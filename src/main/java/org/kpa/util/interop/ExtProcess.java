package org.kpa.util.interop;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.function.Consumer;

public class ExtProcess implements AutoCloseable {
    private final Process process;

    private final Logger logger = LoggerFactory.getLogger(Python.class);
    private final Thread thread;

    public ExtProcess(File directory, Consumer<String> output, String... commands) {
        try {
            ProcessBuilder bldr = new ProcessBuilder(commands);
            if(directory!=null) bldr.directory(directory);
            process = bldr.start();
            BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream()), 10);
            BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()), 10);
            thread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        while (out.ready()) {
                            output.accept(out.readLine());
                        }
                        while (err.ready()) {
                            output.accept(err.readLine());
                        }
                        Thread.sleep(100);
                    } catch (IOException e) {
                        logger.error("Error working with python output process", e);
                        break;
                    } catch (InterruptedException e) {
                        logger.info("Thread interrupted");
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            thread.start();
            logger.info("Started command: '{}'", Joiner.on(" ").join(commands));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        process.destroy();
        long upTime = System.currentTimeMillis() + 30_000;
        while (process.isAlive()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (System.currentTimeMillis() > upTime) {
                logger.error("Process didn't stop in 30sec. Destroying forcibly.");
                process.destroyForcibly();
                break;
            }
        }
        thread.interrupt();
        logger.info("Python closed.");
        Preconditions.checkArgument(process.exitValue() == 0, "Exit value is not 0: %s", process.exitValue());
    }

    @Override
    public String toString() {
        return "ExtProcess{" +
                "process=" + process +
                '}';
    }
}
