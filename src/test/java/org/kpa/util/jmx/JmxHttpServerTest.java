package org.kpa.util.jmx;

import org.junit.Test;

import static org.junit.Assert.*;

public class JmxHttpServerTest {
    @Test
    public void testInitialization() throws InterruptedException {
        JmxHttpServer.initialize();
    }

}