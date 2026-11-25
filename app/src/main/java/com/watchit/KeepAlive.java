package com.watchit;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * The watchdog. Every 15 minutes, and a minute after the service went away
 * when it should not have, it checks the timer is running and starts it if
 * not. Jobs outlive the app's process, reboots, and "Stop" in the list of
 * running apps - though not "Force stop", which cancels them until the app is
 * opened again.
 */
public final class KeepAlive extends JobService {

    static final int EVERY_15_MIN = 1;
    static final int SOON = 2;

    /** Long enough for {@link Starter} to put its pixel up and start the service. */
    private static final long START_MS = 7000;

    /** The periodic check. Scheduling it again would restart its clock, so only once. */
    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js.getPendingJob(EVERY_15_MIN) != null) return;
        js.schedule(new JobInfo.Builder(EVERY_15_MIN, new ComponentName(c, KeepAlive.class))
                .setPeriodic(15 * Rules.MINUTE)
                .setPersisted(true)
                .build());
    }

    /** A one-off check about a minute from now. */
    static void soon(Context c) {
        c.getSystemService(JobScheduler.class).schedule(
                new JobInfo.Builder(SOON, new ComponentName(c, KeepAlive.class))
                        .setMinimumLatency(Rules.MINUTE)
                        .setOverrideDeadline(5 * Rules.MINUTE)
                        .setPersisted(true)
                        .build());
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        if (WatchService.running || !WatchService.ready(this)) return false;
        Starter.start(this);
        // The start may wait for a window to show; hold the job open meanwhile.
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                jobFinished(params, false);
            }
        }, START_MS);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
