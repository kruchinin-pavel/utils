package org.kpa.util;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class TurnoverCounterTest {

    @Test
    public void testTurnover() throws ValidationException {
        AtomicLong millis = new AtomicLong();
        TurnoverCounter counter = new TurnoverCounter(10_000, 1).setMillis(millis::get);
        counter.addValue(1);
        millis.addAndGet(5_000);
        assertEquals(.5, counter.getLastVal(), .001);
        counter.addValue(.5);
        millis.addAndGet(10_000);
        assertEquals(.0, counter.getLastVal(), .001);
        counter.addValue(1);
    }

    @Test
    public void testTurnoverLinear() throws ValidationException {
        AtomicLong millis = new AtomicLong();
        TurnoverCounter counter = new TurnoverCounter(60_000, 60, true).setMillis(millis::get);
        counter.addValue(60);
        millis.addAndGet(1_000);
        assertEquals(59., counter.getLastVal(), .001);
        counter.setValue(30);
        millis.addAndGet(1_000);
        assertEquals(29., counter.getLastVal(), .001);

        millis.addAndGet(14_000);
        assertEquals(15., counter.getLastVal(), .001);

        millis.addAndGet(30_000);
        assertEquals(0., counter.getLastVal(), .001);
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