package com.example.tteonam.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InviteCodesTest {
    @Test
    public void acceptsSixAlphanumericCharactersAndNormalizesCase() {
        assertTrue(InviteCodes.isValid("K7Q2M9"));
        assertEquals("K7Q2M9", InviteCodes.normalize(" k7q-2m9 "));
        assertTrue(InviteCodes.isValid("L2B3C4"));
    }

    @Test
    public void rejectsOtherFormats() {
        assertFalse(InviteCodes.isValid("12345"));
        assertFalse(InviteCodes.isValid("1234567"));
        assertFalse(InviteCodes.isValid("K7Q2M!"));
        for (String excluded : new String[]{"O2B3C4", "A0B3C4", "A2B1C4", "A2B3I4"}) {
            assertFalse(InviteCodes.isValid(excluded));
            assertNull(InviteCodes.normalize(excluded.toLowerCase(java.util.Locale.ROOT)));
        }
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
