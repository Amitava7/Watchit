package com.watchit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keeps time while the screen is on. Once a second it reads which app is in
 * front; if it is one being watched, the floating timer shows that app's time
 * today, and past an hour it grows into the question.
 *
 * Reading usage happens on a background thread; the window is only touched on
 * the main thread. With the screen off nothing ticks at all.
 */
public final class WatchService extends Service implements Overlay.Answers {

    private static final long TICK_MS = 1000;
    /** How often to check the two permissions are still switched on, in ticks. */
    private static final int CHECK_EVERY = 15;
    /** After "No, leave", how long to keep quiet while the home screen comes up. */
    private static final long LEAVING_MS = 3000;
    private static final int NOTIFICATION = 1;
    private static final String CHANNEL = "watching";

    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    private Handler worker;
    private Usage usage;                      // worker thread only
    private int ticks;                        // worker thread only
    private volatile boolean ticking;
    private volatile Set<String> watched = new HashSet<String>();

    private Overlay overlay;                  // main thread from here down
    private final Map<String, Long> answered = new HashMap<String, Long>();
    private final Map<String, String> labels = new HashMap<String, String>();
    private long day;
    private String shownPkg;
    private long shownUsed;
    private String leftPkg;
    private long leftAt;

    /** Usage access, display over other apps, and at least one app chosen. */
    static boolean ready(Context c) {
        return Usage.allowed(c) && Settings.canDrawOverlays(c) && !Prefs.apps(c).isEmpty();
    }

    static void startIfReady(Context c) {
        if (!ready(c)) return;
        try {
            c.startForegroundService(new Intent(c, WatchService.class));
        } catch (RuntimeException e) {
            // Not allowed from the background just now; opening the app starts it.
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        thread = new HandlerThread("watch");
        thread.start();
        worker = new Handler(thread.getLooper());
        usage = new Usage(this);
        overlay = new Overlay(this, this);
        watched = Prefs.apps(this);
        Prefs.listen(this, prefsChanged);
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screen, f, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForeground(NOTIFICATION, notification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } catch (RuntimeException e) {
            // Restarted from the background where a foreground service may not
            // start; the next time the app is opened it starts again.
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ready(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (getSystemService(PowerManager.class).isInteractive()) startTicking();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopTicking();
        overlay.release();
        Prefs.unlisten(this, prefsChanged);
        unregisterReceiver(screen);
        thread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ------------------------------------------------------------- ticking

    private void startTicking() {
        if (ticking) return;
        ticking = true;
        worker.post(tick);
    }

    private void stopTicking() {
        ticking = false;
        worker.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        public void run() {
            if (!ticking) return;
            if (++ticks % CHECK_EVERY == 0 && !(Usage.allowed(WatchService.this)
                    && Settings.canDrawOverlays(WatchService.this))) {
                main.post(new Runnable() {
                    public void run() {
                        stopSelf();
                    }
                });
                return;
            }
            final long now = System.currentTimeMillis();
            Ledger ledger = usage.update(now);
            String front = ledger.front();
            final String pkg = front != null && watched.contains(front) ? front : null;
            final long used = pkg == null ? 0 : ledger.today(pkg, now);
            final long dayStart = ledger.dayStart();
            main.post(new Runnable() {
                public void run() {
                    show(pkg, used, dayStart);
                }
            });
            if (ticking) worker.postDelayed(this, TICK_MS);
        }
    };

    private void show(String pkg, long used, long dayStart) {
        if (dayStart != day) {
            day = dayStart;
            answered.clear();   // a new day, a clean slate
        }
        boolean leaving = pkg != null && pkg.equals(leftPkg)
                && SystemClock.uptimeMillis() - leftAt < LEAVING_MS;
        if (!ticking || pkg == null || leaving) {
            overlay.hide();
            shownPkg = null;
            return;
        }
        shownPkg = pkg;
        shownUsed = used;
        Long said = answered.get(pkg);
        if (Rules.shouldAsk(used, said == null ? -1 : said)) {
            overlay.showCard(pkg, label(pkg), used);
        } else {
            overlay.showBubble(used);
        }
    }

    private String label(String pkg) {
        String l = labels.get(pkg);
        if (l == null) {
            PackageManager pm = getPackageManager();
            try {
                l = pm.getApplicationLabel(pm.getApplicationInfo(pkg,
                        PackageManager.ApplicationInfoFlags.of(0))).toString();
            } catch (PackageManager.NameNotFoundException e) {
                l = pkg;
            }
            labels.put(pkg, l);
        }
        return l;
    }

    // ------------------------------------------------------------- answers

    @Override
    public void needIt(String pkg) {
        answered.put(pkg, pkg.equals(shownPkg) ? shownUsed : Rules.ASK_AT);
        overlay.showBubble(shownUsed);
    }

    @Override
    public void leave(String pkg) {
        leftPkg = pkg;
        leftAt = SystemClock.uptimeMillis();
        overlay.hide();
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(home);
        } catch (RuntimeException ignored) {
            // no home screen to go to; the timer just stays out of the way
        }
    }

    // ------------------------------------------------------------- outside

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                stopTicking();
                overlay.hide();
            } else {
                startTicking();
            }
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefsChanged =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                    if (Prefs.APPS.equals(key)) {
                        watched = Prefs.apps(WatchService.this);
                        if (watched.isEmpty()) stopSelf();
                    } else if (Prefs.SIZE.equals(key) || Prefs.OPACITY.equals(key)) {
                        overlay.restyle();
                    } else {
                        overlay.moved();
                    }
                }
            };

    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Keeping time",
                NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        // Android only lists this under running apps unless notifications are
        // switched on for Watchit, which it never asks for.
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Keeping time")
                .setContentText("The timer floats over the apps you watch.")
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }
}
