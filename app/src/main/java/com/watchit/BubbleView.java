package com.watchit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * The floating number: a pill in green or red with today's minutes in it.
 * The same view is drawn in the settings preview, so what you size there is
 * exactly what floats over your apps.
 */
final class BubbleView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private String label = "";
    private int height;

    BubbleView(Context c) {
        super(c);
        ink.setColor(Ui.INK);
        ink.setTextAlign(Paint.Align.CENTER);
        ink.setTypeface(Typeface.DEFAULT_BOLD);
        // A thin dark rim keeps the edge visible over a page of the same colour.
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(0x59000000);
        setSizeDp(Prefs.SIZE_DEFAULT);
        show(0);
    }

    void setSizeDp(int dp) {
        height = Ui.dp(getContext(), dp);
        ink.setTextSize(height * 0.46f);
        edge.setStrokeWidth(Math.max(1f, height / 30f));
        requestLayout();
        invalidate();
    }

    void setOpacity(int percent) {
        setAlpha(percent / 100f);
    }

    /** Shows {@code used} of today. Returns true when the bubble changed width. */
    boolean show(long used) {
        String l = Rules.label(used);
        int color = Rules.red(used) ? Ui.RED : Ui.GREEN;
        if (l.equals(label) && color == fill.getColor()) return false;
        boolean wider = l.length() != label.length();
        label = l;
        fill.setColor(color);
        setContentDescription(Rules.spoken(used) + " today");
        if (wider) requestLayout();
        invalidate();
        return wider;
    }

    /** A tap does nothing, but the drag handler reports it so accessibility sees it. */
    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // A circle up to two digits, a pill for "1h05".
        int w = Math.round(ink.measureText(label) + height * 0.45f);
        setMeasuredDimension(Math.max(height, w), height);
    }

    @Override
    protected void onDraw(Canvas c) {
        float inset = edge.getStrokeWidth() / 2f;
        box.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float r = box.height() / 2f;
        c.drawRoundRect(box, r, r, fill);
        c.drawRoundRect(box, r, r, edge);
        Paint.FontMetrics fm = ink.getFontMetrics();
        c.drawText(label, getWidth() / 2f, getHeight() / 2f - (fm.ascent + fm.descent) / 2f, ink);
    }
}
