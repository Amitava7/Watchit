# Watchit

A floating timer for the apps that eat the day. Pick the apps; while you are
in one, a small number floats on top with your minutes in it today. Green for
the first half hour, red after that, and past an hour it grows into a question:
do you really need to be using this?

Built the same way as Terraquiz: a plain Android app in Java with **no
libraries at all** — no AndroidX, no Kotlin runtime — so the whole thing is a
few dozen kilobytes. The APK is produced by GitHub Actions; there is nothing to
install locally.

## What it does

**The number.** Whole minutes spent in the app in front, today: `7`, `29`,
`1h05`. Under 30 minutes the bubble is green; from 30 it is red.

**Past an hour it asks.** The bubble grows into a card — *"1 h 02 min in
Instagram today. Do you really need to be using this?"* — with two answers.
**Yes, I do** shrinks it back to a red bubble and leaves you alone for another
15 minutes of use, then it asks again. **No, leave** takes you to the home
screen. Opening the app again after a "no" asks again straight away.

**Drag it anywhere.** Put a finger on the bubble and move it. It is kept inside
the part of the screen clear of the status bar, the navigation bar and the
camera cutout, so it can never be pushed off screen or under a bar. The spot is
remembered as a fraction of the screen, so after a rotation, a size change or a
wider number it is still where you left it, and still fully visible.

**Size and opacity.** The *Floating timer* screen has a size slider (20 to
80 dp tall) and an opacity slider (30 to 100 %), with a preview of the real
bubble in all three states over a light page and a dark one, so you can find
the size you can read at a glance that still stays out of the way. Changes
reach the floating timer at once. *Put it back where it started* returns it to
the right-hand edge.

**Only the apps you pick.** *Apps to watch* lists every app with a launcher
icon — the ticked ones first, then the ones you have spent most time in today,
with today's time next to each and a search box. The timer never appears
anywhere else, the home screen included.

## How it counts

An app's time runs from the moment one of its screens comes to the front until
it goes behind another app, the screen turns off, or the phone locks. The count
starts again at midnight.

The events come from Android's own usage log — the one the system's screen
time page is built from — so on the day you install it, or after a reboot,
today's totals already include everything before. A foreground service reads
the log once a second while the screen is on and not at all while it is off.

Nothing is stored apart from the apps you picked and how the timer looks.
There is no network code and no `INTERNET` permission, so nothing leaves the
phone.

## Setting it up

1. Install the APK and open Watchit.
2. **Allow usage access**: find Watchit in the list and switch it on.
3. **Allow display over other apps**: the same, on the next list.
4. **Choose apps** and tick the ones to watch.

The main screen shows what is still missing, says *Watching N apps* once it is
running, and lists today's time in each watched app.

If Android answers a switch with *Restricted setting*, which it can do for
apps installed outside the Play Store, open Watchit's App info, tap ⋮ and
choose *Allow restricted settings*, then try again. On a Galaxy, keep Watchit
out of *Sleeping apps* and *Deep sleeping apps* (Settings › Battery ›
Background usage limits), or One UI may stop the timer after a while.

| permission | why |
| --- | --- |
| Usage access (`PACKAGE_USAGE_STATS`) | which app is in front, and since when |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | the floating timer itself |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | keeping time while you are in other apps |
| `RECEIVE_BOOT_COMPLETED` | carrying on after a restart or an update |

## Getting the APK

Every push builds one. Open the **Actions** tab, pick the latest *Build APK*
run and download the `watchit-apk` artifact. Pushing a tag like `v1.0` also
publishes a GitHub release with the APK attached.

By default CI signs with a throwaway key generated for that run, which means
consecutive builds have different signatures — uninstall the old copy before
installing a new one. To get a stable signature, add four repository secrets:
`KEYSTORE_BASE64` (`base64 -w0 your.jks`), `KEY_ALIAS`, `KEY_PASSWORD` and
`STORE_PASSWORD`.

Target device is a Galaxy S24 Ultra, so `minSdk` is 34 and there is no
compatibility code for anything older.

## Why it is small

| what | how |
| --- | --- |
| No dependencies | Framework classes only. JUnit is used by the unit tests and never reaches the APK. |
| No XML layouts | Screens are built in code; the only resources are a theme, two colours and two vector icons. |
| One custom view | The bubble is drawn on a canvas, the same view in the overlay and in the preview. |
| R8 + one locale | Minified and shrunk, English only, no per-density copies, no dependency metadata block. |

The release APK comes out around 50 KB, and CI fails the build if it ever
passes 256 KB.

## What CI does

1. **Unit tests** — the time keeping (sessions, switching apps, screen off,
   midnight) and the rules (green under 30 minutes, the question at 60, the
   15 minutes "Yes, I do" buys, how the number is written).
2. **Build release APK** — assembles, signs, prints a size breakdown in the job
   summary and fails past the size budget.
3. **Launch on an emulator** — installs the APK on Android 14 and opens every
   screen; checks the size preview draws a green and a red bubble; grants the
   two accesses with `appops`, as a user would in Settings; ticks the Settings
   app through the app list's search box; then opens Settings and checks the
   timer itself. It must appear in green, follow a drag past the top-left
   corner without leaving the screen, turn red once the clock is moved on
   31 minutes, grow into the question at 61, shrink to a red bubble on
   *Yes, I do*, ask again 16 minutes later, go home and disappear on
   *No, leave*, still fit on screen in landscape, and stay off the home screen.
   Nothing is waited out — the emulator's clock is moved forward. The timer is
   a window of its own that uiautomator cannot see, so `tools/overlay.py`
   finds it in `dumpsys window` and reads its colours from a screenshot,
   printing it as ASCII in the log. Any crash fails the run, and the
   screenshots are uploaded.

## Layout

```
app/src/main/java/com/watchit/
  Ledger.java        today's time per app, from resume / pause / screen-off events
  Rules.java         red at 30 min, ask at 60, 15 more after "yes", the labels
  Usage.java         reads Android's usage log into the ledger, rolls over at midnight
  WatchService.java  foreground service: once a second, what is in front and for how long
  Overlay.java       the floating window: bubble or card, dragging, keeping it on screen
  BubbleView.java    draws the bubble, in the overlay and in the preview
  Prefs.java         the apps picked, size, opacity, position
  MainActivity.java  setup steps, status, today       AppsActivity.java  apps to watch
  TimerActivity.java size and opacity, with preview   BootReceiver.java  restart after a reboot
  Ui.java            colours and a few view factories
app/src/test/java/com/watchit/
  LedgerTest.java    RulesTest.java
tools/
  smoke_test.sh      drives the APK on an emulator
  overlay.py         finds the timer in dumpsys and reads its colours from a screenshot
```

## Building locally

Needs JDK 17 and an Android SDK with platform 35:

```bash
./gradlew testReleaseUnitTest   # the unit tests
./gradlew assembleRelease       # add -PwiStoreFile=... to sign it
```
