package com.watchit;

import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The window floating over other apps: the bubble, or the card it grows into
 * once an app has had an hour today.
 *
 * The position is kept as the bubble's centre, as fractions of the part of
 * the screen clear of the status bar, the navigation bar and the camera
 * cutout. Whatever is shown is clamped into that area - after a drag, a
 * rotation, a size change or a wider number - so it is always fully on screen.
 */
final class Overlay {

    /** What was pressed on the card. */
    interface Answers {
        void needIt(String pkg);

        void leave(String pkg);
    }

    private static final int NONE = 0, BUBBLE = 1, CARD = 2;

    private final Context windowContext;
    private final Context ctx;
    private final WindowManager wm;
    private final Answers answers;
    private final WindowManager.LayoutParams lp;
    private final FrameLayout root;
    private final BubbleView bubble;
    private final TextView cardTime;
    private final LinearLayout card;
    private final int slop;
    private final ComponentCallbacks displayChanges;
    private int mode = NONE;
    private boolean attached;
    private String cardPkg;

    Overlay(Context service, Answers answers) {
        this.answers = answers;
        // A window context follows the display's own size, density and
        // rotation, which a service's context does not.
        Display display = service.getSystemService(DisplayManager.class)
                .getDisplay(Display.DEFAULT_DISPLAY);
        windowContext = service.createWindowContext(display,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        ctx = new ContextThemeWrapper(windowContext, R.style.AppTheme);
        wm = windowContext.getSystemService(WindowManager.class);
        slop = ViewConfiguration.get(ctx).getScaledTouchSlop();

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        // x and y are plain screen coordinates; safeArea() keeps clear of the bars.
        lp.setFitInsetsTypes(0);
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setTitle("Watchit timer");

        root = new FrameLayout(ctx);
        bubble = new BubbleView(ctx);
        bubble.setOnTouchListener(new Drag());
        cardTime = Ui.text(ctx, "", 15f, Ui.RED, true);
        card = buildCard();
        restyle();

        displayChanges = new ComponentCallbacks() {
            public void onConfigurationChanged(Configuration c) {
                if (attached) place();
            }

            @SuppressWarnings("deprecation")   // still part of the interface
            public void onLowMemory() {
            }
        };
        windowContext.registerComponentCallbacks(displayChanges);
    }

    /** The small timer showing {@code used}; takes over from the card if it was up. */
    void showBubble(long used) {
        boolean resized = bubble.show(used);
        if (mode != BUBBLE) {
            setContent(bubble, FrameLayout.LayoutParams.WRAP_CONTENT);
            mode = BUBBLE;
            cardPkg = null;
            resized = true;
        }
        if (resized || !attached) place();
    }

    /** The timer grown into a card, asking whether {@code app} is really needed. */
    void showCard(String pkg, String app, long used) {
        String time = Rules.spoken(used) + " in " + app + " today";
        if (!time.contentEquals(cardTime.getText())) cardTime.setText(time);
        if (mode == CARD && pkg.equals(cardPkg) && attached) return;
        setContent(card, cardWidth(safeArea()));
        mode = CARD;
        cardPkg = pkg;
        place();
        card.setAlpha(0f);
        card.setScaleX(0.8f);
        card.setScaleY(0.8f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start();
    }

    void hide() {
        if (attached) {
            try {
                wm.removeView(root);
            } catch (RuntimeException ignored) {
                // already taken down along with the permission
            }
            attached = false;
        }
        mode = NONE;
        cardPkg = null;
    }

    /** Size or opacity changed in the settings. */
    void restyle() {
        bubble.setSizeDp(Prefs.size(ctx));
        bubble.setOpacity(Prefs.opacity(ctx));
        if (attached && mode == BUBBLE) place();
    }

    /** The saved position changed: put back to the default, or just dragged. */
    void moved() {
        if (attached) place();
    }

    void release() {
        hide();
        windowContext.unregisterComponentCallbacks(displayChanges);
    }

    private LinearLayout buildCard() {
        LinearLayout c = Ui.column(ctx);
        GradientDrawable bg = Ui.rounded(Ui.PANEL, Ui.dp(ctx, 22));
        bg.setStroke(Ui.dp(ctx, 2), Ui.RED);
        c.setBackground(bg);
        int p = Ui.dp(ctx, 18);
        c.setPadding(p, p, p, p);
        c.addView(cardTime);
        TextView q = Ui.text(ctx, "Do you really need to be using this?", 21f, Ui.TEXT, true);
        q.setPadding(0, Ui.dp(ctx, 6), 0, 0);
        c.addView(q);
        TextView note = Ui.text(ctx, "Say yes and it asks again after another "
                + Rules.ASK_AGAIN_AFTER / Rules.MINUTE + " minutes.", 13f, Ui.DIM, false);
        note.setPadding(0, Ui.dp(ctx, 6), 0, 0);
        c.addView(note);

        LinearLayout buttons = Ui.row(ctx);
        buttons.setPadding(0, Ui.dp(ctx, 14), 0, 0);
        Button stay = Ui.button(ctx, "Yes, I do", Ui.BUTTON, Ui.TEXT);
        Button leave = Ui.button(ctx, "No, leave", Ui.ACCENT, Ui.INK);
        stay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (cardPkg != null) answers.needIt(cardPkg);
            }
        });
        leave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (cardPkg != null) answers.leave(cardPkg);
            }
        });
        buttons.addView(stay, Ui.lp(0, Ui.WRAP, 1f));
        buttons.addView(new View(ctx), Ui.lp(Ui.dp(ctx, 10), 1));
        buttons.addView(leave, Ui.lp(0, Ui.WRAP, 1f));
        c.addView(buttons, Ui.lp(Ui.MATCH, Ui.WRAP));
        return c;
    }

    private int cardWidth(Rect safe) {
        return Math.min(Ui.dp(ctx, 320), safe.width());
    }

    private void setContent(View v, int width) {
        root.removeAllViews();
        root.addView(v, new FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT));
    }

    /** Puts the window where the saved position says, clamped into the safe area. */
    private void place() {
        Rect s = safeArea();
        if (mode == CARD) {
            FrameLayout.LayoutParams clp = (FrameLayout.LayoutParams) card.getLayoutParams();
            clp.width = cardWidth(s);
            card.setLayoutParams(clp);
        }
        root.measure(View.MeasureSpec.makeMeasureSpec(s.width(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(s.height(), View.MeasureSpec.AT_MOST));
        int w = root.getMeasuredWidth(), h = root.getMeasuredHeight();
        lp.x = clamp(Math.round(s.left + Prefs.x(ctx) * s.width() - w / 2f), s.left, s.right - w);
        lp.y = clamp(Math.round(s.top + Prefs.y(ctx) * s.height() - h / 2f), s.top, s.bottom - h);
        apply();
    }

    private void apply() {
        try {
            if (attached) {
                wm.updateViewLayout(root, lp);
            } else {
                wm.addView(root, lp);
                attached = true;
            }
        } catch (RuntimeException e) {
            // "Display over other apps" was switched off; the service notices and stops.
            attached = false;
        }
    }

    /** The screen minus the status bar, navigation bar and cutout, whether shown or not. */
    private Rect safeArea() {
        WindowMetrics m = wm.getCurrentWindowMetrics();
        Insets bars = m.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        Rect r = new Rect(m.getBounds());
        r.left += bars.left;
        r.top += bars.top;
        r.right -= bars.right;
        r.bottom -= bars.bottom;
        return r;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Moves the bubble with the finger and remembers where it was let go. */
    private final class Drag implements View.OnTouchListener {
        private float downX, downY;
        private int fromX, fromY;
        private Rect area;
        private boolean dragging;

        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    fromX = lp.x;
                    fromY = lp.y;
                    area = safeArea();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!dragging && dx * dx + dy * dy < slop * slop) return true;
                    dragging = true;
                    lp.x = clamp(fromX + Math.round(dx), area.left, area.right - root.getWidth());
                    lp.y = clamp(fromY + Math.round(dy), area.top, area.bottom - root.getHeight());
                    apply();
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging) remember();
                    else v.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) remember();
                    return true;
                default:
                    return false;
            }
        }

        private void remember() {
            Rect s = safeArea();
            Prefs.setPosition(ctx,
                    (lp.x + root.getWidth() / 2f - s.left) / Math.max(1, s.width()),
                    (lp.y + root.getHeight() / 2f - s.top) / Math.max(1, s.height()));
        }
    }
}
