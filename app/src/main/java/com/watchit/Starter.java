package com.watchit;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * Gets the service into the foreground from wherever it is asked for: the
 * app's own screens, a reboot, the keep-alive job, or the system bringing a
 * killed service back.
 *
 * From the background, Android 15 lets an app that draws over other apps
 * start a foreground service only while one of its floating windows is on
 * screen. So when a start is refused, a one-pixel transparent window goes up,
 * the start is tried again once it is showing, and the window comes down a
 * few seconds later.
 */
final class Starter {

    /** Time for the pixel's window to reach the screen before trying again. */
    private static final long SHOWN_MS = 800;
    /** How long the pixel stays up: long enough for the service to go foreground. */
    private static final long PIXEL_MS = 5000;

    private Starter() {
    }

    /** Starts the service if everything is in place and it is not already running. */
    static void start(Context c) {
        if (WatchService.running || !WatchService.ready(c)) return;
        if (tryStart(c)) return;
        final Context app = c.getApplicationContext();
        withPixel(app, new Runnable() {
            public void run() {
                tryStart(app);
            }
        });
    }

    private static boolean tryStart(Context c) {
        try {
            c.startForegroundService(new Intent(c, WatchService.class));
            return true;
        } catch (RuntimeException e) {
            return false;   // not allowed from the background just now
        }
    }

    /**
     * Puts a one-pixel transparent window on screen and runs {@code then} once
     * it is showing. Returns false, running nothing, when no window can be on
     * screen: no permission to draw over apps, or the screen is off.
     */
    static boolean withPixel(Context c, final Runnable then) {
        if (!Settings.canDrawOverlays(c) || !c.getSystemService(PowerManager.class).isInteractive()) {
            return false;
        }
        Display display = c.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        Context wc = c.createWindowContext(display,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        final WindowManager wm = wc.getSystemService(WindowManager.class);
        final View pixel = new View(wc);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.setTitle("Watchit start");
        try {
            wm.addView(pixel, lp);
        } catch (RuntimeException e) {
            return false;
        }
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(then, SHOWN_MS);
        h.postDelayed(new Runnable() {
            public void run() {
                try {
                    wm.removeView(pixel);
                } catch (RuntimeException ignored) {
                    // already gone
                }
            }
        }, PIXEL_MS);
        return true;
    }
}
