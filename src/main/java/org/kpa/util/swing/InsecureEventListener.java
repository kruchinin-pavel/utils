package org.kpa.util.swing;

import java.awt.event.ActionEvent;

public interface InsecureEventListener {
    void accept(ActionEvent event) throws Exception;
}