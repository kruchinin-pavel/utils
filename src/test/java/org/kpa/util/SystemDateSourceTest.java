package org.kpa.util;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.Assert.*;

public class SystemDateSourceTest {
    @Test
    public void testDS() {
        SystemDateSource sds = SystemDateSource.getInstance();
        long l = Duration.between(LocalDateTime.now(), sds.now()).toMillis();
        System.out.println(l);
        assertTrue(Math.abs(l) < 3000);
    }

}