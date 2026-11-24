package com.example.tteonam.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InviteCodesTest {
    @Test
    public void acceptsSixDigitsAndKeepsLeadingZero() {
        assertTrue(InviteCodes.isValid("012345"));
        assertEquals("012345", InviteCodes.normalize(" 012-345 "));
    }

    @Test
    public void rejectsOtherFormats() {
        assertFalse(InviteCodes.isValid("12345"));
        assertFalse(InviteCodes.isValid("1234567"));
        assertFalse(InviteCodes.isValid("K7Q2M9"));
        assertNull(InviteCodes.normalize(null));
        assertNull(InviteCodes.normalize("https://example.com"));
    }

    @Test
    public void remainingTextRoundsUpToMinutes() {
        assertEquals("30분 후 만료", InviteCodes.remainingText(30 * 60_000L, 0));
        assertEquals("1분 후 만료", InviteCodes.remainingText(1_000L, 0));
        assertEquals("코드 만료됨", InviteCodes.remainingText(0, 0));
    }
}
