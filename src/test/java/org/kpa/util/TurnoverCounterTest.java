package org.kpa.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class TurnoverCounterTest {

    @Test
    public void testTurnover() throws ValidationException {
        SimulatorDateSource ds = new SimulatorDateSource();
        TurnoverCounter counter = new TurnoverCounter(10_000, 1).setDateSource(ds);
        counter.addValue(1);
        ds.addMillis(5_000);
        assertEquals(.5, counter.getLastVal(), .001);
        counter.addValue(.5);
        ds.addMillis(10_000);
        assertEquals(.0, counter.getLastVal(), .001);
        counter.addValue(1);
    }

    @Test
    public void testTurnoverThrow() throws ValidationException {
        SimulatorDateSource ds = new SimulatorDateSource();
        TurnoverCounter counter = new TurnoverCounter(10_000, 1).setDateSource(ds);
        counter.addValue(1);
        ds.addMillis(5_000);
        assertFalse(counter.canAddValue(1.));
        try {
            counter.addValue(1.);
            fail("Should throw validation exception");
        } catch (ValidationException e) {

        }
        ds.addMillis(5_000);
        assertFalse(counter.canAddValue(1.));
        ds.addMillis(5_000);
        counter.addValue(1.);
    }

    @Test
    public void testTurnoverZero() throws ValidationException {
        SimulatorDateSource ds = new SimulatorDateSource();
        TurnoverCounter counter = new TurnoverCounter(0, 1).setDateSource(ds);
        for (int i = 0; i < 100; i++) counter.addValue(1);
    }

}