package com.example.tteonam;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class TripDateTest {
    @Test
    public void pickerUtcMidnightBecomesLocalMidnightOfSameDate() {
        TimeZone seoul = TimeZone.getTimeZone("Asia/Seoul");
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(2026, Calendar.DECEMBER, 1);

        long local = GroupCreateActivity.localStartOfDay(utc.getTimeInMillis(), seoul);

        Calendar check = Calendar.getInstance(seoul);
        check.setTimeInMillis(local);
        assertEquals(2026, check.get(Calendar.YEAR));
        assertEquals(Calendar.DECEMBER, check.get(Calendar.MONTH));
        assertEquals(1, check.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, check.get(Calendar.HOUR_OF_DAY));
        // 서울 00:00 = 전날 UTC 15:00
        assertEquals(utc.getTimeInMillis() - 9L * 60 * 60 * 1000, local);
    }
}
