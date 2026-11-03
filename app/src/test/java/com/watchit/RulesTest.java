package com.watchit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RulesTest {

    private static final long MIN = Rules.MINUTE;
    private static final long SEC = 1000;

    @Test
    public void greenUnderHalfAnHourRedFromIt() {
        assertFalse(Rules.red(0));
        assertFalse(Rules.red(29 * MIN + 59 * SEC));
        assertTrue(Rules.red(30 * MIN));
        assertTrue(Rules.red(3 * 60 * MIN));
    }

    @Test
    public void asksOnceAnHourIsUp() {
        assertFalse(Rules.shouldAsk(59 * MIN + 59 * SEC, -1));
        assertTrue(Rules.shouldAsk(60 * MIN, -1));
        assertTrue(Rules.shouldAsk(4 * 60 * MIN, -1));
    }

    @Test
    public void sayingYesBuysFifteenMinutes() {
        long answered = 62 * MIN;
        assertFalse(Rules.shouldAsk(62 * MIN, answered));
        assertFalse(Rules.shouldAsk(76 * MIN + 59 * SEC, answered));
        assertTrue(Rules.shouldAsk(77 * MIN, answered));
    }

    @Test
    public void bubbleShowsMinutesThenHours() {
        assertEquals("0", Rules.label(0));
        assertEquals("0", Rules.label(59 * SEC));
        assertEquals("1", Rules.label(61 * SEC));
        assertEquals("29", Rules.label(29 * MIN + 59 * SEC));
        assertEquals("59", Rules.label(59 * MIN + 59 * SEC));
        assertEquals("1h00", Rules.label(60 * MIN));
        assertEquals("1h05", Rules.label(65 * MIN));
        assertEquals("2h05", Rules.label(125 * MIN));
        assertEquals("12h40", Rules.label(760 * MIN));
        assertEquals("0", Rules.label(-5 * SEC));
    }

    @Test
    public void spokenForm() {
        assertEquals("under a minute", Rules.spoken(30 * SEC));
        assertEquals("1 min", Rules.spoken(MIN));
        assertEquals("42 min", Rules.spoken(42 * MIN + 10 * SEC));
        assertEquals("1 h 00 min", Rules.spoken(60 * MIN));
        assertEquals("1 h 05 min", Rules.spoken(65 * MIN));
    }
}
