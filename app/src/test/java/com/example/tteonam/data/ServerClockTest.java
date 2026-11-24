package com.example.tteonam.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class ServerClockTest {
    private static final long DAY = 24L * 60 * 60 * 1000;

    @After
    public void tearDown() {
        ServerClock.reset();
    }

    @Test
    public void unknownClockUsesDeviceTime() {
        assertFalse(ServerClock.isKnown());
        assertEquals(0L, ServerClock.offsetMs());
    }

    @Test
    public void deviceFourDaysBehindIsCorrected() {
        long device = 1_000_000_000_000L;
        ServerClock.observe(device + 4 * DAY, device);
        assertTrue(ServerClock.isKnown());
        assertEquals(4 * DAY, ServerClock.offsetMs());
        long diff = ServerClock.now() - System.currentTimeMillis();
        assertTrue(Math.abs(diff - 4 * DAY) < 1_000L);
    }

    @Test
    public void subSecondDifferenceIsTreatedAsInSync() {
        long device = 1_000_000_000_000L;
        ServerClock.observe(device - 900L, device);
        assertEquals(0L, ServerClock.offsetMs());
    }
}
