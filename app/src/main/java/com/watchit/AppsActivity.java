package com.watchit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The apps the timer appears in. Everything with a launcher icon is listed:
 * the ones already ticked first, then the most used today, so the apps eating
 * the day are at the top. A tick takes effect at once.
 */
public final class AppsActivity extends Activity {

    /** One row of the list. */
    private static final class App {
        String pkg;
        String name;
        Drawable icon;
        long used;
    }

    private static final class Holder {
        ImageView icon;
        TextView name;
        TextView detail;
        CheckBox check;
    }

    private final List<App> all = new ArrayList<App>();
    private final List<App> shown = new ArrayList<App>();
    private Set<String> chosen;
    private String query = "";
    private TextView count;
    private TextView note;
    private BaseAdapter adapter;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        chosen = Prefs.apps(this);

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 22);
        root.setPadding(pad, pad, pad, 0);
        root.addView(Ui.text(this, "Apps to watch", 28f, Ui.TEXT, true));
        TextView sub = Ui.text(this, "The timer only appears in the apps ticked here. "
                + "Today's time is next to each one.", 14f, Ui.DIM, false);
        sub.setPadding(0, Ui.dp(this, 6), 0, 0);
        root.addView(sub);
        count = Ui.text(this, "", 14f, Ui.GREEN, true);
        count.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 12));
        root.addView(count);

        EditText search = new EditText(this);
        search.setHint("Search apps");
        search.setHintTextColor(Ui.DIM);
        search.setTextColor(Ui.TEXT);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setBackground(Ui.rounded(Ui.PANEL, Ui.dp(this, 14)));
        int sp = Ui.dp(this, 14);
        search.setPadding(sp, Ui.dp(this, 10), sp, Ui.dp(this, 10));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable e) {
                query = e.toString().trim().toLowerCase(Locale.ROOT);
                filter();
            }
        });
        root.addView(search, Ui.lp(Ui.MATCH, Ui.WRAP));

        note = Ui.text(this, "Looking through your apps…", 15f, Ui.DIM, false);
        note.setPadding(0, Ui.dp(this, 16), 0, 0);
        root.addView(note);

        adapter = new BaseAdapter() {
            public int getCount() {
                return shown.size();
            }

            public Object getItem(int i) {
                return shown.get(i);
            }

            public long getItemId(int i) {
                return i;
            }

            public View getView(int i, View convert, ViewGroup parent) {
                View row = convert != null ? convert : row();
                bind((Holder) row.getTag(), shown.get(i));
                return row;
            }
        };
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int i, long id) {
                toggle(shown.get(i));
            }
        });
        root.addView(list, Ui.lp(Ui.MATCH, 0, 1f));

        setContentView(root);
        Ui.fitSystemWindows(root);
        updateCount();
        load();
    }

    private void load() {
        final Context app = getApplicationContext();
        final Set<String> picked = new HashSet<String>(chosen);
        new Thread(new Runnable() {
            public void run() {
                PackageManager pm = app.getPackageManager();
                long now = System.currentTimeMillis();
                Map<String, Long> totals = Usage.allowed(app)
                        ? new Usage(app).update(now).totals(now) : new HashMap<String, Long>();
                List<ResolveInfo> found = pm.queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                        PackageManager.ResolveInfoFlags.of(0));
                Set<String> seen = new HashSet<String>();
                final List<App> apps = new ArrayList<App>();
                for (ResolveInfo ri : found) {
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(app.getPackageName()) || !seen.add(pkg)) continue;
                    App a = new App();
                    a.pkg = pkg;
                    a.name = ri.loadLabel(pm).toString();
                    a.icon = ri.loadIcon(pm);
                    Long used = totals.get(pkg);
                    a.used = used == null ? 0 : used;
                    apps.add(a);
                }
                Collections.sort(apps, new Comparator<App>() {
                    public int compare(App a, App b) {
                        boolean ca = picked.contains(a.pkg), cb = picked.contains(b.pkg);
                        if (ca != cb) return ca ? -1 : 1;
                        if (a.used != b.used) return Long.compare(b.used, a.used);
                        return a.name.compareToIgnoreCase(b.name);
                    }
                });
                ui.post(new Runnable() {
                    public void run() {
                        if (isFinishing()) return;
                        all.clear();
                        all.addAll(apps);
                        filter();
                    }
                });
            }
        }, "apps").start();
    }

    private void filter() {
        shown.clear();
        for (App a : all) {
            if (query.isEmpty() || a.name.toLowerCase(Locale.ROOT).contains(query)) shown.add(a);
        }
        if (!all.isEmpty()) {
            note.setText("No app called that.");
            note.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        }
        adapter.notifyDataSetChanged();
    }

    private void toggle(App a) {
        boolean on = !chosen.contains(a.pkg);
        if (on) chosen.add(a.pkg);
        else chosen.remove(a.pkg);
        Prefs.setApp(this, a.pkg, on);
        adapter.notifyDataSetChanged();
        updateCount();
        if (on) WatchService.startIfReady(this);
    }

    private void updateCount() {
        int n = chosen.size();
        count.setText(n == 0 ? "Nothing ticked yet"
                : "Watching " + n + (n == 1 ? " app" : " apps"));
        count.setTextColor(n == 0 ? Ui.AMBER : Ui.GREEN);
    }

    private View row() {
        LinearLayout row = Ui.row(this);
        int p = Ui.dp(this, 10);
        row.setPadding(0, p, 0, p);
        Holder h = new Holder();
        h.icon = new ImageView(this);
        int s = Ui.dp(this, 40);
        LinearLayout.LayoutParams ilp = Ui.lp(s, s);
        ilp.rightMargin = Ui.dp(this, 14);
        row.addView(h.icon, ilp);
        LinearLayout texts = Ui.column(this);
        h.name = Ui.text(this, "", 16f, Ui.TEXT, false);
        h.detail = Ui.text(this, "", 13f, Ui.DIM, false);
        texts.addView(h.name);
        texts.addView(h.detail);
        row.addView(texts, Ui.lp(0, Ui.WRAP, 1f));
        // The row takes the tap, so the box only shows the state.
        h.check = new CheckBox(this);
        h.check.setClickable(false);
        h.check.setFocusable(false);
        h.check.setButtonTintList(ColorStateList.valueOf(Ui.ACCENT));
        row.addView(h.check);
        row.setTag(h);
        return row;
    }

    private void bind(Holder h, App a) {
        h.icon.setImageDrawable(a.icon);
        h.name.setText(a.name);
        if (a.used > 0) {
            h.detail.setText(Rules.spoken(a.used) + " today");
            h.detail.setTextColor(Rules.red(a.used) ? Ui.RED : Ui.DIM);
        } else {
            h.detail.setText("not opened today");
            h.detail.setTextColor(Ui.DIM);
        }
        h.check.setChecked(chosen.contains(a.pkg));
    }
}
