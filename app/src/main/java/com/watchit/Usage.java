package com.watchit;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.SystemClock;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

/**
 * Reads the system's record of which activity is in front and feeds it to a
 * {@link Ledger}. This is the same event log the system's own screen time
 * screen is built from, so today's totals include everything from before the
 * service started - after a reboot, or on the day the app is installed.
 *
 * Each update reads only the events since the last one. Not thread safe: use
 * one instance per thread.
 */
final class Usage {

    /**
     * How far before midnight to start reading on a fresh start, so an app
     * already open at midnight is known to be open. Only time after midnight
     * is counted.
     */
    private static final long LOOKBACK = 3 * 60 * Rules.MINUTE;

    /** A change of the clock bigger than this counts as one, as it does for the system. */
    private static final long CLOCK_CHANGE = 2000;

    private final UsageStatsManager usm;
    private final UsageEvents.Event event = new UsageEvents.Event();
    private Ledger ledger;
    private long nextDay;
    /** Newest event timestamp handled; the next read starts here. */
    private long seenAt;
    /** Events already handled at exactly {@link #seenAt}, so a re-read skips them. */
    private final Set<String> seenThen = new HashSet<String>();
    /** The clock and the time since boot at the last update, to notice the clock being set. */
    private long lastNow, lastElapsed;

    Usage(Context c) {
        usm = c.getSystemService(UsageStatsManager.class);
    }

    /** Whether the user has switched on usage access for this app. */
    static boolean allowed(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        int mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), c.getPackageName());
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return c.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Brings the ledger up to {@code now}, starting a fresh day at midnight. */
    Ledger update(long now) {
        long elapsed = SystemClock.elapsedRealtime();
        if (ledger != null) {
            // When the clock is set, the system moves every event it has stored
            // by the same amount, so the next read would meet the ones already
            // counted all over again. Move the read position along with them.
            // The app in front keeps its start: the bubble follows the clock.
            long change = (now - lastNow) - (elapsed - lastElapsed);
            if (Math.abs(change) > CLOCK_CHANGE) seenAt += change;
        }
        lastNow = now;
        lastElapsed = elapsed;
        // First call, the clock was put back, or more than a day went by unseen.
        // An event newer than now can only mean the clock went backwards.
        if (ledger == null || now < seenAt || now < ledger.dayStart()
                || now >= dayAfter(nextDay)) {
            start(now);
        }
        if (now >= nextDay) {
            read(nextDay);
            ledger.newDay(nextDay);
            nextDay = dayAfter(nextDay);
        }
        read(now);
        return ledger;
    }

    private void start(long now) {
        long day = startOfDay(now);
        ledger = new Ledger(day);
        nextDay = dayAfter(day);
        seenAt = day - LOOKBACK;
        seenThen.clear();
    }

    private void read(long until) {
        if (until <= seenAt) return;
        UsageEvents events = usm.queryEvents(seenAt, until);
        if (events == null) return;   // locked before the first unlock, or no access
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            long t = event.getTimeStamp();
            if (t < seenAt) continue;
            // Reads overlap at seenAt, because an event stamped there may have
            // been logged just after the last read. Skip the ones handled.
            String id = event.getEventType() + " " + event.getPackageName() + " "
                    + event.getClassName();
            if (t == seenAt) {
                if (!seenThen.add(id)) continue;
            } else {
                seenAt = t;
                seenThen.clear();
                seenThen.add(id);
            }
            switch (event.getEventType()) {
                case UsageEvents.Event.ACTIVITY_RESUMED:
                    ledger.resumed(t, event.getPackageName());
                    break;
                case UsageEvents.Event.ACTIVITY_PAUSED:
                    ledger.paused(t, event.getPackageName());
                    break;
                case UsageEvents.Event.SCREEN_NON_INTERACTIVE:
                case UsageEvents.Event.KEYGUARD_SHOWN:
                case UsageEvents.Event.DEVICE_SHUTDOWN:
                    ledger.stopped(t);
                    break;
                default:
                    break;
            }
        }
    }

    static long startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** The next midnight. Not always 24 hours away: clocks change. */
    private static long dayAfter(long dayStart) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(dayStart);
        c.add(Calendar.DAY_OF_YEAR, 1);
        return startOfDay(c.getTimeInMillis());
    }
}
