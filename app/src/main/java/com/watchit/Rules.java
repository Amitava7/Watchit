package com.watchit;

/**
 * What the timer shows and when it speaks up. No Android classes in here, so
 * the unit tests can pin the thresholds down.
 *
 * All of it works on one number: how long an app has been in front today.
 */
final class Rules {

    static final long MINUTE = 60_000L;

    /** Under this the timer is green, from here on it is red. */
    static final long RED_AT = 30 * MINUTE;

    /** From here the timer grows and asks whether you really need the app. */
    static final long ASK_AT = 60 * MINUTE;

    /** Answer "I need it" and it leaves you alone for this much more use. */
    static final long ASK_AGAIN_AFTER = 15 * MINUTE;

    private Rules() {
    }

    static boolean red(long used) {
        return used >= RED_AT;
    }

    /**
     * Whether to ask right now. {@code answeredAt} is how much use there was
     * when the question was last answered with "I need it", or -1 if it has
     * not been answered today.
     */
    static boolean shouldAsk(long used, long answeredAt) {
        if (used < ASK_AT) return false;
        return answeredAt < 0 || used >= answeredAt + ASK_AGAIN_AFTER;
    }

    /** The number in the bubble: whole minutes, then "1h05" past the hour. */
    static String label(long used) {
        long min = Math.max(0, used) / MINUTE;
        if (min < 60) return String.valueOf(min);
        long m = min % 60;
        return (min / 60) + "h" + (m < 10 ? "0" : "") + m;
    }

    /** Written out for sentences and lists: "under a minute", "42 min", "1 h 05 min". */
    static String spoken(long used) {
        long min = Math.max(0, used) / MINUTE;
        if (min == 0) return "under a minute";
        if (min < 60) return min + " min";
        long m = min % 60;
        return (min / 60) + " h " + (m < 10 ? "0" : "") + m + " min";
    }
}
