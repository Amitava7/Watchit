package com.watchit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * Starts keeping time again as soon as the phone has started, and after the
 * app is updated. Also makes sure the keep-alive job is lined up.
 */
public final class BootReceiver extends BroadcastReceiver {

    /** Long enough for {@link Starter} to put its pixel up and start the service. */
    private static final long START_MS = 7000;

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            return;
        }
        KeepAlive.schedule(c);
        Starter.start(c);
        // The start may wait for a window to show; keep the process up meanwhile.
        final PendingResult pending = goAsync();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                pending.finish();
            }
        }, START_MS);
    }
}
