package com.watchit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.Map;

import org.junit.Test;

public class LedgerTest {

    private static final long MIN = Rules.MINUTE;
    private static final long DAY = 24 * 60 * MIN;
    /** Some midnight; the ledger only cares about differences from it. */
    private static final long D = 1_790_000_000_000L;
    private static final long TEN_AM = D + 10 * 60 * MIN;

    @Test
    public void countsOneSession() {
        Ledger l = new Ledger(D);
        l.resumed(TEN_AM, "insta");
        l.paused(TEN_AM + 20 * MIN, "insta");
        assertNull(l.front());
        assertEquals(20 * MIN, l.today("insta", TEN_AM + 60 * MIN));
    }

    @Test
    public void countsTheOpenSessionUpToNow() {
        Ledger l = new Ledger(D);
        l.resumed(TEN_AM, "insta");
        assertEquals("insta", l.front());
        assertEquals(7 * MIN, l.today("insta", TEN_AM + 7 * MIN));
        assertEquals(8 * MIN, l.today("insta", TEN_AM + 8 * MIN));
    }

    @Test
    public void addsSessionsUp() {
        Ledger l = new Ledger(D);
        l.resumed(TEN_AM, "insta");
        l.paused(TEN_AM + 10 * MIN, "insta");
        l.resumed(TEN_AM + 30 * MIN, "insta");
        l.paused(TEN_AM + 45 * MIN, "insta");
        l.resumed(TEN_AM + 50 * MIN, "insta");
        assertEquals(25 * MIN + 3 * MIN, l.today("insta", TEN_AM + 53 * MIN));
    }

    @Test
    public void movingBetweenScreensOfOneAppKeepsCounting() {
        Ledger l = new Ledger(D);
        l.resumed(TEN_AM, "insta");
        // a new screen: the old one pauses, the new one resumes a moment later
        l.paused(TEN_AM + 5 * MIN, "insta");
        l.resumed(TEN_AM + 5 * MIN + 40, "insta");
        assertEquals("insta", l.front());
        assertEquals(10 * MIN - 40, l.today("insta", TEN_AM + 10 * MIN));
    }

    @Test
    public void switchingAppsMovesTheClock() {
        Ledger l = new Ledger(D);
        l.resumed(TEN_AM, "insta");
        l.paused(TEN_AM + 5 * MIN, "insta");
        l.resumed(TEN_AM + 5 * MIN, "youtube");
        assertEquals("youtube", l.front());
        assertEquals(5 * MIN, l.today("insta", TEN_AM + 12 * MIN));
        assertEquals(7 * MIN, l.today("youtube", TEN_AM + 12 * MIN));
    }

    @Test
    public void anAppThatNeverPausedStopsWhenAnotherResumes() {
        Ledger l = new Ledger(D);
        l.resumed(TEN_AM, "insta");
        l.resumed(TEN_AM + 3 * MIN, "launcher");
        assertEquals("launcher", l.front());
        assertEquals(3 * MIN, l.today("insta", TEN_AM + 60 * MIN));
    }

    @Test
    public void aPauseFromAnAppBehindIsIgnored() {
        Ledger l = new Ledger(D);
        l.resumed(TEN_AM, "insta");
        l.paused(TEN_AM + 2 * MIN, "youtube");
        assertEquals("insta", l.front());
        assertEquals(5 * MIN, l.today("insta", TEN_AM + 5 * MIN));
    }

    @Test
    public void screenOffStopsTheClock() {
        Ledger l = new Ledger(D);
        l.resumed(TEN_AM, "insta");
        l.stopped(TEN_AM + 4 * MIN);
        assertNull(l.front());
        assertEquals(4 * MIN, l.today("insta", TEN_AM + 30 * MIN));
        // a second screen-off changes nothing
        l.stopped(TEN_AM + 9 * MIN);
        assertEquals(4 * MIN, l.today("insta", TEN_AM + 30 * MIN));
    }

    @Test
    public void onlyTheTimeAfterMidnightCounts() {
        Ledger l = new Ledger(D);
        l.resumed(D - 10 * MIN, "insta");
        assertEquals(3 * MIN, l.today("insta", D + 3 * MIN));
        l.paused(D + 5 * MIN, "insta");
        assertEquals(5 * MIN, l.today("insta", D + 60 * MIN));
    }

    @Test
    public void yesterdayCountsForNothing() {
        Ledger l = new Ledger(D);
        l.resumed(D - 50 * MIN, "insta");
        l.paused(D - 20 * MIN, "insta");
        assertEquals(0, l.today("insta", D + 60 * MIN));
        assertFalse(l.totals(D + 60 * MIN).containsKey("insta"));
    }

    @Test
    public void aNewDayStartsFromNothingButKeepsTheAppInFront() {
        Ledger l = new Ledger(D);
        l.resumed(D + 9 * 60 * MIN, "insta");
        l.paused(D + 10 * 60 * MIN, "insta");
        l.resumed(D + DAY - 5 * MIN, "youtube");
        l.newDay(D + DAY);
        assertEquals(D + DAY, l.dayStart());
        assertEquals(0, l.today("insta", D + DAY + 10 * MIN));
        assertEquals("youtube", l.front());
        assertEquals(10 * MIN, l.today("youtube", D + DAY + 10 * MIN));
    }

    @Test
    public void totalsIncludeTheAppInFront() {
        Ledger l = new Ledger(D);
        l.resumed(TEN_AM, "insta");
        l.paused(TEN_AM + 10 * MIN, "insta");
        l.resumed(TEN_AM + 10 * MIN, "youtube");
        Map<String, Long> t = l.totals(TEN_AM + 25 * MIN);
        assertEquals(2, t.size());
        assertEquals(10 * MIN, (long) t.get("insta"));
        assertEquals(15 * MIN, (long) t.get("youtube"));
    }

    @Test
    public void neverGoesNegative() {
        Ledger l = new Ledger(D);
        l.resumed(TEN_AM, "insta");
        // "now" read a moment before the event's own timestamp
        assertEquals(0, l.today("insta", TEN_AM - 30));
    }
}
