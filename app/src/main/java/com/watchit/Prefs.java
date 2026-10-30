package com.watchit;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The few things the user chooses: which apps to watch, and the timer's size,
 * opacity and position. The service listens for changes, so a slider moved
 * in the settings reaches the floating timer straight away.
 */
final class Prefs {

    static final String APPS = "apps";
    static final String SIZE = "size";
    static final String OPACITY = "opacity";
    static final String X = "x";
    static final String Y = "y";

    /** Height of the bubble in dp. */
    static final int SIZE_MIN = 20, SIZE_MAX = 80, SIZE_DEFAULT = 36;
    /** How solid the bubble is, in percent. */
    static final int OPACITY_MIN = 30, OPACITY_DEFAULT = 100;
    /**
     * Where the middle of the bubble sits, as fractions of the screen clear of
     * the system bars: by default against the right edge, a third of the way down.
     */
    static final float X_DEFAULT = 1f, Y_DEFAULT = 0.3f;

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("watchit", Context.MODE_PRIVATE);
    }

    static Set<String> apps(Context c) {
        return new HashSet<String>(sp(c).getStringSet(APPS, Collections.<String>emptySet()));
    }

    static void setApp(Context c, String pkg, boolean watched) {
        Set<String> s = apps(c);
        if (watched) s.add(pkg);
        else s.remove(pkg);
        sp(c).edit().putStringSet(APPS, s).apply();
    }

    static int size(Context c) {
        return clamp(sp(c).getInt(SIZE, SIZE_DEFAULT), SIZE_MIN, SIZE_MAX);
    }

    static void setSize(Context c, int dp) {
        sp(c).edit().putInt(SIZE, clamp(dp, SIZE_MIN, SIZE_MAX)).apply();
    }

    static int opacity(Context c) {
        return clamp(sp(c).getInt(OPACITY, OPACITY_DEFAULT), OPACITY_MIN, 100);
    }

    static void setOpacity(Context c, int percent) {
        sp(c).edit().putInt(OPACITY, clamp(percent, OPACITY_MIN, 100)).apply();
    }

    static float x(Context c) {
        return Math.max(0f, Math.min(1f, sp(c).getFloat(X, X_DEFAULT)));
    }

    static float y(Context c) {
        return Math.max(0f, Math.min(1f, sp(c).getFloat(Y, Y_DEFAULT)));
    }

    static void setPosition(Context c, float x, float y) {
        sp(c).edit().putFloat(X, x).putFloat(Y, y).apply();
    }

    static void resetPosition(Context c) {
        sp(c).edit().remove(X).remove(Y).apply();
    }

    static void listen(Context c, SharedPreferences.OnSharedPreferenceChangeListener l) {
        sp(c).registerOnSharedPreferenceChangeListener(l);
    }

    static void unlisten(Context c, SharedPreferences.OnSharedPreferenceChangeListener l) {
        sp(c).unregisterOnSharedPreferenceChangeListener(l);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
