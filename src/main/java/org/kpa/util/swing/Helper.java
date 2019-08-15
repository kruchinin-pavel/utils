package org.kpa.util.swing;

import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class Helper {
    private static final Logger logger = LoggerFactory.getLogger(Helper.class);
    private static AtomicInteger taskCounter = new AtomicInteger();

    public static Action newAction(String name, InsecureEventListener listener) {
        Action action = newAction(name, null, name, listener);
        action.setEnabled(true);
        return action;
    }

    public static void eventOnColumn(JTable tbl, MouseEvent evt, int columnIndex, Consumer<TableClickEvent> consumer) {
        int modelCol = tbl.columnAtPoint(evt.getPoint());
        int row = tbl.rowAtPoint(evt.getPoint());
        if ((columnIndex == -1 || modelCol == columnIndex) && row != -1) {
            Object valueAt = tbl.getValueAt(row, modelCol);
            consumer.accept(new TableClickEvent(row,modelCol,tbl,evt,valueAt));
        }
    }

    public static class TableClickEvent {
        public final int col;
        public final int row;
        public final JTable tbl;
        public final MouseEvent evt;
        public final Object value;

        public TableClickEvent(int row, int col, JTable tbl, MouseEvent evt, Object value) {
            this.col = col;
            this.row = row;
            this.tbl = tbl;
            this.evt = evt;
            this.value = value;
        }

        public Object getCurVal() {
            return tbl.getValueAt(row, col);
        }

        public Object getColVal(int col) {
            return tbl.getValueAt(row, col);
        }

        @Override
        public String toString() {
            return "TableClickEvent{" +
                    "col=" + col +
                    ", row=" + row +
                    ", value=" + value +
                    '}';
        }
    }


    public static Action newAction(String name, KeyStroke keyStroke, String description, InsecureEventListener listener) {
        AbstractAction abstractAction = new AbstractAction(name) {
            {
                putValue(ACTION_COMMAND_KEY, name);
                putValue(SHORT_DESCRIPTION, String.format(description, keyStroke));
//                putValue(KEYSTROKE_PROPERTY, keyStroke);
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                setEnabled(false);
                AtomicBoolean setEnabled = new AtomicBoolean(true);
                swingLater(() -> {
                    try {
                        listener.accept(e);
                    } catch (Exception e1) {
                        setEnabled.set(false);
                        logger.error("Error running action [" + name + "]: " + e1.getMessage(), e1);
                    }
                }, task -> setEnabled(setEnabled.get()));
            }
        };
        abstractAction.setEnabled(false);
        return abstractAction;
    }

    public static void swingLater(InsecureRunnable source, Consumer<InsecureRunnable> onComplete) {
        final LongRunWarning stack = new LongRunWarning();
        int i = taskCounter.incrementAndGet();
        if (i > 1000 && i % 100 == 0) {
            logger.warn("Huge event queue: {}. Stack={}", i, Throwables.getStackTraceAsString(stack));
        }
        SwingUtilities.invokeLater(() -> {
            long ct = System.nanoTime();
            try {
                swing(source);
                if (onComplete != null) onComplete.accept(source);
            } catch (Exception e) {
                logger.error("Error happened in swing thread: {}", e.getMessage(), e);
                if (onComplete != null) onComplete.accept(null);
            } finally {
                taskCounter.decrementAndGet();
                ct = (System.nanoTime() - ct) / 1_000_000;
                if (ct > 200) {
                    logger.warn("Long ({}ms.) run of: {}", ct, Throwables.getStackTraceAsString(stack));
                }
            }
        });
    }

    public static void swingLater(InsecureRunnable source) {
        swingLater(source, null);
    }


    public static void swing(InsecureRunnable source) {
        if (!SwingUtilities.isEventDispatchThread()) {
            swingLater(source);
        }
        try {
            source.run();
        } catch (Exception e) {
            logger.error("Error happened in swing thread: {}", e.getMessage(), e);
        }
    }

}
