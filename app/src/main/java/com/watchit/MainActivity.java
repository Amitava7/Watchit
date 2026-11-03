package com.watchit;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Home: the steps still needed before the timer can work, whether it is
 * running, and today's time in every watched app. Starts the service whenever
 * everything is in place.
 */
public final class MainActivity extends Activity {

    private LinearLayout steps;
    private LinearLayout today;
    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 24);
        root.setPadding(pad, pad, pad, pad);

        root.addView(Ui.text(this, "Watchit", 40f, Ui.TEXT, true));
        TextView sub = Ui.text(this,
                "A small timer floats over the apps you pick and counts your minutes in "
                        + "each one today. Green for the first half hour, red after that, and "
                        + "past an hour it asks whether you really need to be there.",
                15f, Ui.DIM, false);
        sub.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 18));
        root.addView(sub);

        status = Ui.text(this, "", 16f, Ui.DIM, true);
        status.setPadding(0, 0, 0, Ui.dp(this, 14));
        root.addView(status);

        steps = Ui.column(this);
        root.addView(steps, Ui.lp(Ui.MATCH, Ui.WRAP));
        today = Ui.column(this);
        root.addView(today, Ui.lp(Ui.MATCH, Ui.WRAP));

        root.addView(bigButton("Apps to watch",
                "Choose which apps get a timer.", AppsActivity.class));
        root.addView(bigButton("Floating timer",
                "Size, opacity, and where it sits.", TimerActivity.class));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.addView(root);
        setContentView(scroll);
        Ui.fitSystemWindows(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** Permissions can change behind our back in Settings, so this runs on every return. */
    private void refresh() {
        boolean usage = Usage.allowed(this);
        boolean overlay = Settings.canDrawOverlays(this);
        Set<String> apps = Prefs.apps(this);

        steps.removeAllViews();
        if (!usage) {
            steps.addView(step("Allow usage access",
                    "So Watchit can tell which app is open and for how long. "
                            + "Find Watchit in the list and switch it on.",
                    "Open usage access", Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
        if (!overlay) {
            steps.addView(step("Allow display over other apps",
                    "So the timer can float on top of whatever you are using. "
                            + "Find Watchit in the list and switch it on.",
                    "Open the setting", Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
        if (apps.isEmpty()) {
            LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, "Pick the apps to watch", 18f, Ui.ACCENT, true));
            TextView d = Ui.text(this, "The timer only shows up in the apps you choose, "
                    + "so it never follows you around everything else.", 14f, Ui.DIM, false);
            d.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
            card.addView(d);
            Button b = Ui.button(this, "Choose apps", Ui.ACCENT, Ui.INK);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, AppsActivity.class));
                }
            });
            card.addView(b, Ui.lp(Ui.WRAP, Ui.WRAP));
            steps.addView(card);
        }

        int left = (usage ? 0 : 1) + (overlay ? 0 : 1) + (apps.isEmpty() ? 1 : 0);
        if (left == 0) {
            status.setTextColor(Ui.GREEN);
            status.setText("● Watching " + apps.size() + (apps.size() == 1 ? " app" : " apps"));
            WatchService.startIfReady(this);
        } else {
            status.setTextColor(Ui.AMBER);
            status.setText("Not watching yet: " + left + (left == 1 ? " step" : " steps") + " to go");
        }
        loadToday(apps, usage);
    }

    private View step(String title, String detail, String action, final String settingsAction) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, title, 18f, Ui.ACCENT, true));
        TextView d = Ui.text(this, detail, 14f, Ui.DIM, false);
        d.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
        card.addView(d);
        Button b = Ui.button(this, action, Ui.ACCENT, Ui.INK);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openSettings(settingsAction);
            }
        });
        card.addView(b, Ui.lp(Ui.WRAP, Ui.WRAP));
        return card;
    }

    /** Straight to Watchit's own switch where the phone supports it, else the list. */
    private void openSettings(String action) {
        try {
            startActivity(new Intent(action, Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(action));
        }
    }

    /** One watched app on the "Today" card. */
    private static final class Spent {
        final String name;
        final long used;

        Spent(String name, long used) {
            this.name = name;
            this.used = used;
        }
    }

    private void loadToday(final Set<String> apps, final boolean usage) {
        if (!usage || apps.isEmpty()) {
            today.removeAllViews();
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                final List<Spent> rows = new ArrayList<Spent>();
                long now = System.currentTimeMillis();
                Ledger l = new Usage(getApplicationContext()).update(now);
                PackageManager pm = getPackageManager();
                for (String pkg : apps) {
                    try {
                        CharSequence name = pm.getApplicationLabel(pm.getApplicationInfo(pkg,
                                PackageManager.ApplicationInfoFlags.of(0)));
                        rows.add(new Spent(name.toString(), l.today(pkg, now)));
                    } catch (PackageManager.NameNotFoundException e) {
                        // uninstalled since it was picked
                    }
                }
                Collections.sort(rows, new Comparator<Spent>() {
                    public int compare(Spent a, Spent b) {
                        return Long.compare(b.used, a.used);
                    }
                });
                ui.post(new Runnable() {
                    public void run() {
                        if (!isFinishing()) showToday(rows);
                    }
                });
            }
        }, "today").start();
    }

    private void showToday(List<Spent> rows) {
        today.removeAllViews();
        if (rows.isEmpty()) return;
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, "Today", 18f, Ui.ACCENT, true));
        for (Spent s : rows) {
            LinearLayout row = Ui.row(this);
            row.setPadding(0, Ui.dp(this, 8), 0, 0);
            row.addView(Ui.text(this, s.name, 15f, Ui.TEXT, false), Ui.lp(0, Ui.WRAP, 1f));
            row.addView(Ui.text(this, s.used == 0 ? "not opened" : Rules.spoken(s.used), 15f,
                    s.used == 0 ? Ui.DIM : Rules.red(s.used) ? Ui.RED : Ui.GREEN, true));
            card.addView(row);
        }
        today.addView(card);
    }

    private View bigButton(String label, String detail, final Class<?> target) {
        LinearLayout card = Ui.card(this);
        int p = Ui.dp(this, 18);
        card.setPadding(p, p, p, p);
        card.addView(Ui.text(this, label, 21f, Ui.ACCENT, true));
        TextView d = Ui.text(this, detail, 14f, Ui.DIM, false);
        d.setPadding(0, Ui.dp(this, 4), 0, 0);
        card.addView(d);
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, target));
            }
        });
        return card;
    }
}
