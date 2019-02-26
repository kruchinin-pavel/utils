package org.kpa.util.swing;

import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created with IntelliJ IDEA.
 * User: krucpav
 * Date: 6/2/13
 * Time: 8:15 PM
 * To change this template use File | Settings | File Templates.
 */
public class GuiAppender<E> extends UnsynchronizedAppenderBase<E> {
    private final Logger log = LoggerFactory.getLogger(GuiAppender.class);
    private boolean printedErr = false;
    protected Layout<E> layout;

    public void setLayout(Layout<E> layout) {
        this.layout = layout;
    }

    @Override
    public void start() {
        try {
            getForm();
            super.start();
        } catch (Exception e) {
            log.error("Didn't manage to create GUI appender form: {}", e.getMessage(), e);
        }
    }

    @Override
    protected void append(E eventObject) {
        logMesage(layout.doLayout(eventObject));
    }

    public void logMesage(String messageToSend) {
        SwingUtilities.invokeLater(() -> {
            try {
                logError(messageToSend);
            } catch (Throwable e) {
                if (!printedErr) {
                    printedErr = true;
                    System.err.println(
                            String.format("Didn't manage to show log '%s'. Error message: %s",
                                    messageToSend, e.getMessage()));
                }
            }
        });
    }

    private static final AtomicReference<LoggingEventForm> logFormRef = new AtomicReference<>();

    public static void logError(String event) throws IOException {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Must be called from withing swing event dispatcher thread");
        }
        LoggingEventForm form = getForm();
        form.addMessage(event);
        if (form.shouldBeOnTop() || !form.isVisible()) {
//            List<PivotFrame> pivotFrames = getMyFrames();
            form.setLocationRelativeTo(null);
            form.toFront();
            form.setVisible(true);
        }
    }

    private static LoggingEventForm getForm() {
        LoggingEventForm form = logFormRef.get();
        if (form == null) {
            form = new LoggingEventForm();
            form.pack();
            if (!logFormRef.compareAndSet(null, form)) {
                form = logFormRef.get();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> logFormRef.get().dispose(), "GUIAppenderDispose"));
            }
        }
        return form;
    }

}
