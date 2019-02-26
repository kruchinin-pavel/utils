package org.kpa.util.swing;

import ch.qos.logback.classic.PatternLayout;

import static org.junit.Assert.*;

public class GuiAppenderTest {
    public static void main(String[] args) throws InterruptedException {
        GuiAppender appender = new GuiAppender();
        appender.logMesage("Test error message.");
    }

}