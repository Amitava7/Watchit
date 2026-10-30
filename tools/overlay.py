#!/usr/bin/env python3
"""Find the floating timer on screen and say what colour it is.

The timer is a window of its own, which uiautomator cannot see, so the smoke
test finds it in `dumpsys window windows` by its title and reads its pixels out
of a raw screencap. CI cannot show pictures, so the region is also printed as
ASCII in the log.

  adb shell dumpsys window windows | python3 tools/overlay.py frame
  adb exec-out screencap > s.raw
  python3 tools/overlay.py look s.raw 810 612 1060 862
  python3 tools/overlay.py spot s.raw 810 612 1060 862 accent

`frame` prints "left top right bottom" and exits 1 when the timer is not on
screen. `look` prints the region as ASCII, then "SCREEN <width> <height>" and
one "PCT_<COLOUR> <percent>" line per colour for the shell to assert on. `spot` prints the centre of the
pixels of one colour, which is how the test finds a button on the card.
"""

import re
import struct
import sys

TITLE = "Watchit timer"   # WindowManager.LayoutParams title set in Overlay.java

# The app's palette, from Ui.java, plus white for the page behind. Anything
# far from all of these is called "other".
PALETTE = [
    ("green", (0x2F, 0xBF, 0x71), "G"),
    ("red", (0xE5, 0x54, 0x4B), "R"),
    ("panel", (0x12, 0x1C, 0x26), ","),
    ("ink", (0x06, 0x12, 0x1C), "#"),
    ("text", (0xE7, 0xEF, 0xF6), "T"),
    ("dim", (0x9B, 0xB0, 0xC2), "d"),
    ("accent", (0x3D, 0x9B, 0xE9), "B"),
    ("button", (0x1E, 0x2E, 0x3D), "b"),
    ("white", (0xFF, 0xFF, 0xFF), " "),
]


def frame(dump):
    """The timer window's frame from `dumpsys window windows`, or None."""
    for block in re.split(r"\n(?=\s*Window #\d+ )", dump):
        head = block.strip().split("\n", 1)[0]
        # a window on its way out is still listed for its exit animation
        if TITLE not in head or "EXITING" in head:
            continue
        for key in (r"\bframe=", r"mFrame="):
            m = re.search(key + r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", block)
            if m:
                return [int(v) for v in m.groups()]
        # a dumpsys layout this does not know: show it rather than guess
        sys.stderr.write(block[:4000] + "\n")
        sys.exit(2)
    return None


def read_screencap(path):
    """screencap writes width, height, format, [colourspace], then RGBA."""
    with open(path, "rb") as fh:
        data = fh.read()
    w, h, _fmt = struct.unpack("<III", data[:12])
    for header in (12, 16):
        if len(data) - header == w * h * 4:
            return w, h, header, data
    sys.exit("not a screencap dump: %dx%d does not match %d bytes" % (w, h, len(data)))


def classify(r, g, b):
    best, best_d = None, 1 << 30
    for name, (pr, pg, pb), ch in PALETTE:
        d = (r - pr) ** 2 + (g - pg) ** 2 + (b - pb) ** 2
        if d < best_d:
            best, best_d = (name, ch), d
    return best if best_d <= 2600 else ("other", "?")


def pixels(path, box, stride=1):
    """Yields (x, y, name, char) for the pixels of box, clipped to the screen."""
    w, h, off, data = read_screencap(path)
    l, t, r, b = max(0, box[0]), max(0, box[1]), min(w, box[2]), min(h, box[3])
    for y in range(t, b, stride):
        for x in range(l, r, stride):
            i = off + (y * w + x) * 4
            name, ch = classify(data[i], data[i + 1], data[i + 2])
            yield x, y, name, ch


def look(path, box):
    w, h = read_screencap(path)[:2]
    l, t, r, b = max(0, box[0]), max(0, box[1]), min(w, box[2]), min(h, box[3])
    cols = max(1, min(48, (r - l) // 4))
    rows = max(1, min(24, (b - t) // 8))
    grid = [[" "] * cols for _ in range(rows)]
    # a few hundred thousand samples are plenty, even for a whole screen
    stride = max(1, int(((r - l) * (b - t) / 250000.0) ** 0.5))
    counts = {}
    for x, y, name, ch in pixels(path, box, stride):
        counts[name] = counts.get(name, 0) + 1
        cx, cy = (x - l) * cols // (r - l), (y - t) * rows // (b - t)
        grid[cy][cx] = ch
    print("region %d,%d - %d,%d" % (l, t, r, b))
    for line in grid:
        print("|" + "".join(line) + "|")
    total = float(sum(counts.values())) or 1.0
    parts = sorted(counts.items(), key=lambda kv: -kv[1])
    print(" ".join("%s=%.1f%%" % (k, 100 * v / total) for k, v in parts))
    print("SCREEN %d %d" % (w, h))
    for name, _rgb, _ch in PALETTE + [("other", None, None)]:
        print("PCT_%s %.1f" % (name.upper(), 100 * counts.get(name, 0) / total))


def spot(path, box, colour):
    xs, ys = [], []
    for x, y, name, _ch in pixels(path, box):
        if name == colour:
            xs.append(x)
            ys.append(y)
    if not xs:
        sys.exit("no %s pixels in the region" % colour)
    print("%d %d" % (sum(xs) // len(xs), sum(ys) // len(ys)))


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    cmd = sys.argv[1]
    if cmd == "frame":
        f = frame(sys.stdin.read())
        if f is None:
            sys.exit(1)
        print("%d %d %d %d" % tuple(f))
    elif cmd == "look":
        look(sys.argv[2], [int(v) for v in sys.argv[3:7]])
    elif cmd == "spot":
        spot(sys.argv[2], [int(v) for v in sys.argv[3:7]], sys.argv[7])
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
