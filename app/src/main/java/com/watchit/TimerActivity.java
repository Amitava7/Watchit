package com.watchit;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * How big and how solid the floating timer is. The preview draws the real
 * bubble in all three of its states over a light page and a dark one, so you
 * can settle on a size you can read at a glance that still stays out of the
 * way. A change reaches the floating timer at once.
 */
public final class TimerActivity extends Activity {

    private static final long[] SAMPLES = {12 * Rules.MINUTE, 45 * Rules.MINUTE, 65 * Rules.MINUTE};

    private final List<BubbleView> previews = new ArrayList<BubbleView>();
    private TextView sizeValue;
    private TextView opacityValue;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = Ui.column(this);
        int pad = Ui.dp(this, 22);
        root.setPadding(pad, pad, pad, pad);
        root.addView(Ui.text(this, "Floating timer", 28f, Ui.TEXT, true));
        TextView sub = Ui.text(this, "Drag the timer itself to move it anywhere. "
                + "It always stays fully on screen.", 14f, Ui.DIM, false);
        sub.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 16));
        root.addView(sub);

        LinearLayout preview = Ui.row(this);
        preview.setGravity(Gravity.TOP);
        preview.addView(page("Over a light app", 0xFFF4F6F8, 0xFF5B6B7A), Ui.lp(0, Ui.WRAP, 1f));
        preview.addView(page("Over a dark app", 0xFF000000, Ui.DIM), Ui.lp(0, Ui.WRAP, 1f));
        root.addView(preview, Ui.lp(Ui.MATCH, Ui.WRAP));

        TextView legend = Ui.text(this, "Green for the first 30 minutes in an app today, red "
                + "after that. Past an hour it grows into a question: say you need the app "
                + "and it shrinks back for another 15 minutes.", 14f, Ui.DIM, false);
        legend.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 8));
        root.addView(legend);

        sizeValue = Ui.text(this, "", 15f, Ui.TEXT, true);
        root.addView(label("Size", sizeValue));
        root.addView(slider(Prefs.SIZE_MIN, Prefs.SIZE_MAX, Prefs.size(this),
                new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                        if (fromUser) Prefs.setSize(TimerActivity.this, value);
                        restyle();
                    }

                    public void onStartTrackingTouch(SeekBar s) {
                    }

                    public void onStopTrackingTouch(SeekBar s) {
                    }
                }));

        opacityValue = Ui.text(this, "", 15f, Ui.TEXT, true);
        root.addView(label("Opacity", opacityValue));
        root.addView(slider(Prefs.OPACITY_MIN, 100, Prefs.opacity(this),
                new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                        if (fromUser) Prefs.setOpacity(TimerActivity.this, value);
                        restyle();
                    }

                    public void onStartTrackingTouch(SeekBar s) {
                    }

                    public void onStopTrackingTouch(SeekBar s) {
                    }
                }));

        Button reset = Ui.button(this, "Put it back where it started", Ui.BUTTON, Ui.TEXT);
        reset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.resetPosition(TimerActivity.this);
                Toast.makeText(TimerActivity.this, "Back against the right edge",
                        Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams rlp = Ui.lp(Ui.MATCH, Ui.WRAP);
        rlp.topMargin = Ui.dp(this, 22);
        root.addView(reset, rlp);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.addView(root);
        setContentView(scroll);
        Ui.fitSystemWindows(scroll);
        restyle();
    }

    /** One half of the preview: the three states of the bubble on a page of {@code bg}. */
    private View page(String caption, int bg, int fg) {
        LinearLayout page = Ui.column(this);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setBackgroundColor(bg);
        int p = Ui.dp(this, 14);
        page.setPadding(p, p, p, p);
        TextView c = Ui.text(this, caption, 12f, fg, false);
        c.setGravity(Gravity.CENTER);
        page.addView(c, Ui.lp(Ui.MATCH, Ui.WRAP));
        for (long used : SAMPLES) {
            BubbleView b = new BubbleView(this);
            b.show(used);
            LinearLayout.LayoutParams lp = Ui.lp(Ui.WRAP, Ui.WRAP);
            lp.topMargin = Ui.dp(this, 12);
            page.addView(b, lp);
            previews.add(b);
        }
        return page;
    }

    private View label(String name, TextView value) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
        row.addView(Ui.text(this, name, 15f, Ui.DIM, false), Ui.lp(0, Ui.WRAP, 1f));
        row.addView(value);
        return row;
    }

    private SeekBar slider(int min, int max, int value, SeekBar.OnSeekBarChangeListener l) {
        SeekBar s = new SeekBar(this);
        s.setMin(min);
        s.setMax(max);
        s.setProgress(value);
        s.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
        s.setThumbTintList(ColorStateList.valueOf(Ui.ACCENT));
        s.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.BUTTON));
        s.setOnSeekBarChangeListener(l);
        return s;
    }

    private void restyle() {
        int size = Prefs.size(this), opacity = Prefs.opacity(this);
        for (BubbleView b : previews) {
            b.setSizeDp(size);
            b.setOpacity(opacity);
        }
        sizeValue.setText(size + " dp");
        opacityValue.setText(opacity + "%");
    }
}
