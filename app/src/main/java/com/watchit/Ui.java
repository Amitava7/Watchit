package com.watchit;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Colours, a few view factories, and window-inset handling. No XML layouts. */
final class Ui {

    static final int BG = 0xFF0B1118;
    static final int PANEL = 0xFF121C26;
    static final int BUTTON = 0xFF1E2E3D;   // reads as a button against PANEL
    static final int TEXT = 0xFFE7EFF6;
    static final int DIM = 0xFF9BB0C2;
    static final int GREEN = 0xFF2FBF71;
    static final int RED = 0xFFE5544B;
    static final int AMBER = 0xFFF0B429;
    static final int ACCENT = 0xFF3D9BE9;
    static final int INK = 0xFF06121C;     // text on GREEN, RED and ACCENT

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, String s, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    static Button button(Context c, String label, int bg, int fg) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        b.setTextColor(fg);
        b.setBackground(pill(bg));
        b.setPadding(dp(c, 18), dp(c, 10), dp(c, 18), dp(c, 10));
        b.setStateListAnimator(null);
        b.setMinimumHeight(dp(c, 48));
        return b;
    }

    private static StateListDrawable pill(int bg) {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, rounded(dim(bg), 999f));
        sl.addState(new int[]{-android.R.attr.state_enabled}, rounded(dim(bg), 999f));
        sl.addState(new int[0], rounded(bg, 999f));
        return sl;
    }

    static GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private static int dim(int c) {
        return Color.argb(Color.alpha(c), Color.red(c) * 2 / 3, Color.green(c) * 2 / 3,
                Color.blue(c) * 2 / 3);
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    /** A PANEL-coloured block with the usual padding and a gap below it. */
    static LinearLayout card(Context c) {
        LinearLayout card = column(c);
        card.setBackgroundColor(PANEL);
        int p = dp(c, 16);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = lp(MATCH, WRAP);
        lp.bottomMargin = dp(c, 14);
        card.setLayoutParams(lp);
        return card;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    /**
     * Apps targeting Android 15 draw behind the system bars, so pad the root
     * view by whatever the bars and the keyboard cover.
     */
    static void fitSystemWindows(final View root) {
        final int l = root.getPaddingLeft(), t = root.getPaddingTop();
        final int r = root.getPaddingRight(), b = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                v.setPadding(l + bars.left, t + bars.top, r + bars.right,
                        b + Math.max(bars.bottom, ime.bottom));
                return insets;
            }
        });
        root.requestApplyInsets();
    }
}
