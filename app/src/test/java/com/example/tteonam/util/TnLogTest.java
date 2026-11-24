package com.example.tteonam.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.ExecutionException;

public class TnLogTest {
    @Test
    public void tagsStartWithTn() {
        assertEquals("tn", TnLog.tag(null));
        assertEquals("tn-Upload", TnLog.tag(TnLog.UPLOAD));
        assertTrue(TnLog.tag("VeryLongAreaNameThatExceedsLimit").length() <= 23);
        assertTrue(TnLog.tag("VeryLongAreaNameThatExceedsLimit").startsWith("tn-"));
    }

    @Test
    public void shortIdKeepsOnlyPrefix() {
        assertEquals("abcdef…", TnLog.shortId("abcdefghijkl"));
        assertEquals("abc", TnLog.shortId("abc"));
        assertEquals("null", TnLog.shortId(null));
    }

    @Test
    public void describeUnwrapsExecutionException() {
        assertEquals("", TnLog.describe(null));
        assertEquals(" [IllegalStateException]",
                TnLog.describe(new ExecutionException(new IllegalStateException("x"))));
    }
}
