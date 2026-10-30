package com.watchit;

import java.util.HashMap;
import java.util.Map;

/**
 * Adds up how long each app has been in front today.
 *
 * It is fed the system's activity events in time order. One app at a time is
 * in front: from the moment one of its activities resumes until that app
 * pauses, another app resumes, or the screen goes off. Moving between two
 * screens of the same app pauses one and resumes the next a few milliseconds
 * apart, which costs nothing worth counting.
 *
 * Only time since the start of the day counts, so an app opened before
 * midnight adds just the part after it. No Android classes in here, so the
 * unit tests can drive it with made-up events.
 */
final class Ledger {

    private long dayStart;
    private final Map<String, Long> done = new HashMap<String, Long>();  // closed sessions, ms
    private String front;   // the app in front, or null
    private long since;     // when it came to the front

    Ledger(long dayStart) {
        this.dayStart = dayStart;
    }

    long dayStart() {
        return dayStart;
    }

    /** The app in front right now, or null when none is (home screen counts as an app). */
    String front() {
        return front;
    }

    void resumed(long t, String pkg) {
        if (pkg.equals(front)) return;   // another screen of the same app
        // Whatever was in front never said it paused (it crashed, or shares
        // the screen in split view): either way it is not the one in use now.
        if (front != null) close(t);
        front = pkg;
        since = t;
    }

    void paused(long t, String pkg) {
        if (pkg.equals(front)) close(t);
    }

    /** Screen off, lock screen up or shutting down: nothing is in use any more. */
    void stopped(long t) {
        if (front != null) close(t);
    }

    /** Midnight: today's totals start again from nothing, the app in front carries on. */
    void newDay(long start) {
        dayStart = start;
        done.clear();
    }

    /** Time {@code pkg} has spent in front today, as of {@code now}. */
    long today(String pkg, long now) {
        Long d = done.get(pkg);
        long t = d == null ? 0 : d;
        if (pkg.equals(front)) t += counted(since, now);
        return t;
    }

    /** Every app used today, with its time as of {@code now}. */
    Map<String, Long> totals(long now) {
        Map<String, Long> out = new HashMap<String, Long>(done);
        if (front != null) out.put(front, today(front, now));
        return out;
    }

    private void close(long t) {
        long add = counted(since, t);
        if (add > 0) {
            Long d = done.get(front);
            done.put(front, (d == null ? 0 : d) + add);
        }
        front = null;
    }

    /** The part of [from, to) that falls inside today. */
    private long counted(long from, long to) {
        return Math.max(0, to - Math.max(from, dayStart));
    }
}
