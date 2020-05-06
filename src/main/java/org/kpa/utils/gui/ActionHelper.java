package org.kpa.utils.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;

import static org.kpa.utils.gui.Parallel.swingLater;

// TODO move to utils
public class ActionHelper {
    private static final String KEYSTROKE_PROPERTY = "KEYSTROKE_PROPERTY";
    private static final Logger logger = LoggerFactory.getLogger(ActionHelper.class);

    public interface InsecureEventListener {
        void accept(ActionEvent event) throws Exception;
    }

    public static Action newAction(String name, KeyStroke keyStroke, String description,
                                   InsecureEventListener listener) {
        AbstractAction abstractAction = new AbstractAction(name) {
            {
                putValue(ACTION_COMMAND_KEY, name);
                putValue(SHORT_DESCRIPTION, String.format(description, keyStroke));
                putValue(KEYSTROKE_PROPERTY, keyStroke);
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                setEnabled(false);
                swingLater(() -> {
                    try {
                        listener.accept(e);
                    } catch (Exception e1) {
                        logger.error("Error running action [" + name + "]: " + e1.getMessage(), e1);
                    }
                }, task -> setEnabled(true));
            }
        };
        abstractAction.setEnabled(false);
        return abstractAction;
    }
}
