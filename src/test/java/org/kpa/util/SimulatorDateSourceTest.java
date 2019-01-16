package org.kpa.util;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.kpa.util.DateSourceHelper.nsToMs;

/**
 * Created by krucpav on 18.02.17.
 */
public class SimulatorDateSourceTest {
    @Test
    public void testSim() {
        SimulatorDateSource ds = new SimulatorDateSource();
        ds.addNanos(1000);
        System.out.println(ds.now());
        assertEquals(0, nsToMs(ds.nanos()));
        ds.addNanos(999_000);
        assertEquals(1, nsToMs(ds.nanos()));
    }

}