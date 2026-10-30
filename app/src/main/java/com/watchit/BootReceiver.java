package com.watchit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts keeping time again after a reboot, and after the app is updated. */
public final class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            WatchService.startIfReady(c);
        }
    }
}
