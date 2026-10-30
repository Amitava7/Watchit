#!/usr/bin/env bash
# Installs the release APK on a running emulator and checks the floating timer
# end to end: the screens open, an app is picked from the real list, and over
# that app the timer appears in green, can be dragged without ever leaving the
# screen, turns red after half an hour, grows into the question after an hour,
# and both answers do what they say. Nothing is waited out - the emulator's
# clock is moved forward instead. Fails on any crash.
#
# Buttons in the app are found by reading the view hierarchy. The timer is a
# window of its own that uiautomator cannot see, so it is found in dumpsys and
# its colours read from a screenshot by tools/overlay.py. Screenshots land in
# shots/ and the workflow uploads them.
set -euo pipefail

APK=$(ls apk/*.apk | head -1)
PKG=com.watchit
# The app it is told to watch. Not Settings: Android hides other apps'
# floating windows over Settings screens, so the timer never shows there.
WATCHED=com.android.deskclock
WATCHED_NAME=Clock
SHOTS=shots
mkdir -p "$SHOTS"

adb root
adb wait-for-device
sleep 5
# keep the screen on and unlocked for the whole run
adb shell svc power stayon true
adb shell wm dismiss-keyguard || true

fail() {
    echo "::error::$1"
    shot "failed"
    exit 1
}

shot() { adb exec-out screencap -p > "$SHOTS/$1.png"; }

ui() {
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    adb shell cat /sdcard/ui.xml 2>/dev/null || true
}

# Taps the middle of the first node matching an attribute, e.g. 'text="Next"'.
tap_node() {
    local match="$1" xy
    xy=$(ui | tr '<' '\n' | grep -F "$match" | head -1 \
        | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1 \
        | tr -c '0-9' ' ' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}')
    if [ -z "$xy" ]; then
        ui | tr '<' '\n' | grep -o 'text="[^"]*"' | grep -v 'text=""' || true
        fail "nothing on screen matches $match"
    fi
    echo "tapping $match at $xy"
    adb shell input tap $xy
    sleep 2
}

expect() {
    if ! ui | grep -qF "$1"; then
        shot "missing-${2:-text}"
        ui | tr '<' '\n' | grep -o 'text="[^"]*"' | grep -v 'text=""' || true
        fail "expected to see \"$1\""
    fi
}

crashed() {
    if adb logcat -d | grep -q "FATAL EXCEPTION"; then
        adb logcat -d | grep -A 40 "FATAL EXCEPTION"
        shot "$1-crash"
        fail "$1 crashed"
    fi
}

open_screen() {
    adb logcat -c
    # A fresh task each time: otherwise asking for the main screen again only
    # brings its task forward, with whatever was opened last still on top.
    adb shell am start -W --activity-clear-task -n "$PKG/.$1"
    sleep 4
    shot "$1"
    crashed "$1"
}

# ---- the clock ---------------------------------------------------------------
# Half an hour of use is made by moving the clock forward. Start at ten in the
# morning, local time, so the hour and a half added below never reaches midnight.
now() { adb shell date +%s | tr -d '\r'; }

near() {
    local d=$(( $(now) - $1 ))
    [ "${d#-}" -le 10 ]
}

set_clock() {  # set_clock <epoch seconds>
    adb shell cmd alarm set-time "$(( $1 * 1000 ))" >/dev/null 2>&1 || true
    near "$1" || adb shell date "@$1" >/dev/null 2>&1 || true
    near "$1" || fail "could not set the emulator clock to $1, it says $(now)"
    echo "clock: $(adb shell date | tr -d '\r')"
}

advance() {  # advance <minutes>
    set_clock $(( $(now) + $1 * 60 ))
}

adb shell settings put global auto_time 0
OFF=$(adb shell date +%z | tr -d '\r')                      # +0000, -0700, ...
OFF_S=$(( ${OFF:0:1}1 * (10#${OFF:1:2} * 3600 + 10#${OFF:3:2} * 60) ))
LOCAL=$(( $(now) + OFF_S ))
set_clock $(( LOCAL - LOCAL % 86400 + 10 * 3600 - OFF_S ))

# ---- the floating timer ------------------------------------------------------
frame() {  # "left top right bottom" of the timer, or nothing when it is not up
    adb shell dumpsys window windows | python3 tools/overlay.py frame || true
}

timer_on() {  # waits for the timer and leaves its frame in L T R B
    local f=""
    for _ in $(seq 1 10); do
        f=$(frame)
        [ -n "$f" ] && break
        sleep 1
    done
    [ -n "$f" ] || fail "$1: the floating timer is not on screen"
    read -r L T R B <<< "$f"
    echo "$1: timer at $L,$T - $R,$B"
}

timer_off() {
    for _ in $(seq 1 10); do
        [ -z "$(frame)" ] && return 0
        sleep 1
    done
    fail "$1: the floating timer is still up"
}

# Prints the timer as ASCII in the log, and leaves the percentage of each
# palette colour and the screen size in $SHOTS/<name>.txt. What the window
# manager says about the window goes in the log too, so a timer the system
# hid can be told apart from one that never drew.
look() {  # look <name>
    adb exec-out screencap > "$SHOTS/$1.raw"
    python3 tools/overlay.py look "$SHOTS/$1.raw" "$L" "$T" "$R" "$B" | tee "$SHOTS/$1.txt"
    adb shell dumpsys window windows | python3 tools/overlay.py block \
        | grep -E "no timer window|mHasSurface|ForceHide|isVisible|mViewVisibility|isOnScreen" || true
}

at_least() {  # at_least <name> <COLOUR> <percent>: that much of the timer is that colour
    awk -v k="PCT_$2" -v min="$3" '$1 == k {found = 1; ok = ($2 >= min)}
        END {exit !(found && ok)}' "$SHOTS/$1.txt"
}

press() {  # press <name> <colour>: taps that colour's button along the bottom of the card
    local xy
    xy=$(python3 tools/overlay.py spot "$SHOTS/$1.raw" "$L" $(( B - (B - T) / 4 )) "$R" "$B" "$2")
    echo "pressing the $2 button at $xy"
    adb shell input tap $xy
}

on_screen() {  # the timer lies fully on the screen of shot <name>
    local w h
    read -r w h < <(awk '$1 == "SCREEN" {print $2, $3}' "$SHOTS/$1.txt")
    [ "$L" -ge 0 ] && [ "$T" -ge 0 ] && [ "$R" -le "$w" ] && [ "$B" -le "$h" ] \
        || fail "$1: the timer reaches off the ${w}x${h} screen"
}

watched_app() {
    adb logcat -c
    adb shell am start -W -n "$WATCHED/.DeskClock"
}

# ---- the screens open before anything is granted ------------------------------
adb install -r "$APK"
for SCREEN in MainActivity AppsActivity TimerActivity; do
    open_screen "$SCREEN"
done
open_screen MainActivity
expect "Allow usage access" steps
expect "Allow display over other apps" steps
expect "Pick the apps to watch" steps

# The size screen previews the bubble in both of its colours, near the top.
open_screen TimerActivity
L=0; T=0; R=99999; B=1400
look TimerActivity
at_least TimerActivity GREEN 0.3 || fail "no green bubble in the preview"
at_least TimerActivity RED 0.3 || fail "no red bubble in the preview"

# ---- grant the two accesses, as the user would in Settings ---------------------
adb shell appops set "$PKG" GET_USAGE_STATS allow
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow

# ---- pick the app to watch from the real list, through its search box ---------
open_screen AppsActivity
sleep 2
tap_node 'class="android.widget.EditText"'
adb shell input text "$WATCHED_NAME"
sleep 2
tap_node 'class="android.widget.CheckBox"'
expect 'checked="true"' ticked
expect "Watching 1 app" ticked
shot AppsActivity-ticked
crashed AppsActivity-tick

# With everything in place the main screen says so and starts keeping time.
open_screen MainActivity
expect "Watching 1 app" status
adb shell dumpsys activity services "$PKG" | grep -q "isForeground=true" \
    || fail "the service is not running in the foreground"

# ---- a green timer floats over the watched app ---------------------------------
watched_app
timer_on green
look green
at_least green GREEN 25 || fail "the timer is not green"
on_screen green
BUBBLE_W=$(( R - L ))
shot green
crashed green

# ---- dragged up past the corner, it follows but stays on screen -----------------
FROM_L=$L; FROM_T=$T
adb shell input swipe $(( (L + R) / 2 )) $(( (T + B) / 2 )) 5 5 800
sleep 2
timer_on dragged
look dragged
on_screen dragged
[ "$L" -lt "$FROM_L" ] && [ "$T" -lt "$FROM_T" ] || fail "the timer did not follow the drag"
[ "$T" -gt 0 ] || fail "the timer went under the status bar"
shot dragged

# ---- half an hour in: red ------------------------------------------------------
advance 31
sleep 3
timer_on red
look red
at_least red RED 25 || fail "the timer did not turn red after 30 minutes"
shot red
crashed red

# ---- past the hour it grows into the question ------------------------------------
advance 30
sleep 3
timer_on asked
look asked
[ $(( R - L )) -gt $(( BUBBLE_W * 3 )) ] || fail "the timer did not grow after an hour"
at_least asked PANEL 25 || fail "the question card is not drawn"
at_least asked RED 1 || fail "the card does not show the time in red"
on_screen asked
shot asked
crashed asked

# "Yes, I do" shrinks it back to a red bubble.
press asked button
sleep 2
timer_on yes
look yes
[ $(( R - L )) -lt $(( BUBBLE_W * 3 )) ] || fail "the card stayed up after \"Yes, I do\""
at_least yes RED 25 || fail "the bubble is not red past the hour"
shot yes

# Fifteen minutes on it asks again, and "No, leave" goes to the home screen.
advance 16
sleep 3
timer_on asked-again
look asked-again
[ $(( R - L )) -gt $(( BUBBLE_W * 3 )) ] || fail "it did not ask again 15 minutes later"
press asked-again accent
sleep 3
timer_off left
if adb shell dumpsys activity activities | grep -E "ResumedActivity" | grep -q "$WATCHED"; then
    fail "\"No, leave\" did not leave the app"
fi
shot left
crashed left

# The main screen lists today's time in the watched app.
open_screen MainActivity
expect "Today" today
expect "text=\"$WATCHED_NAME\"" today

# ---- rotated, the card still fits on screen ----------------------------------------
watched_app
timer_on portrait
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 5
timer_on landscape
look landscape
on_screen landscape
shot landscape
crashed rotate
adb shell settings put system user_rotation 0
sleep 3
crashed rotate-back

# ---- nothing floats over the home screen ---------------------------------------------
adb shell input keyevent KEYCODE_HOME
sleep 2
timer_off home
crashed home

echo "the timer appeared, moved, stayed on screen, turned red, asked twice, and nothing crashed"
