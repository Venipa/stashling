"""Privacy-safe screenshots of the dev app for the landing page and docs.

Images are blurred by the app itself (launched with --ez blur_images true). On top, every on-screen text that is not
the app's own UI copy (strings.xml) is blurred here: scene titles, performer and studio names never leave the device.

  python apps/docs/scripts/capture.py --launch home     # restart the dev app blurred, capture "home"
  python apps/docs/scripts/capture.py browse            # capture the current screen
  python apps/docs/scripts/capture.py --tap "Browse" x   # tap the node with that text/description first

Raw frames are never written: the redacted PNG lands in screenshots/landing/<name>.png (gitignored).
"""

import argparse
import html
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
RES = ROOT / "apps/android"
OUT = ROOT / "screenshots/landing"
APP = "net.venipa.stashling.dev"


def adb(*args: str, binary: bool = False):
    r = subprocess.run(["adb", *args], capture_output=True, check=True)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def ui_copy() -> list[re.Pattern]:
    """strings.xml values as full-match patterns; format args (%s, %1$d) match anything."""
    pats = []
    files = [f for f in RES.glob("*/src/main/res/values/strings*.xml")]
    for el in (e for f in files for e in ET.parse(f).getroot().iter()):
        if el.tag not in ("string", "item") or not (el.text or "").strip():
            continue
        text = html.unescape(el.text).replace("\\'", "'").replace('\\"', '"').strip()
        parts = re.split(r"%(?:\d+\$)?[sdf]", text)
        if len(re.findall(r"[A-Za-z]", "".join(parts))) < 3:
            continue  # "%1$s · %2$s" would let any name through
        pats.append(re.compile(".+".join(re.escape(p) for p in parts), re.S))
    return pats


def is_ui_copy(text: str, pats: list[re.Pattern]) -> bool:
    if re.search(r"[A-Za-z]\.[A-Za-z]|://|@", text):
        return False  # hostnames, URLs, accounts
    # Counts, dates and durations carry no identity.
    if re.fullmatch(r"[\d\s.,:·/%+\-–]*(scenes?|images?|performers?|studios?|tags?)?", text, re.I):
        return True
    return any(p.fullmatch(text) for p in pats)


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    root = ET.fromstring(adb("shell", "cat", "/sdcard/ui.xml"))
    for n in root.iter("node"):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds", "[0,0][0,0]")))
        yield (n.get("text") or n.get("content-desc") or "").strip(), (x1, y1, x2, y2)


def tap(label: str):
    for _ in range(4):  # the dump can land mid-transition
        for text, (x1, y1, x2, y2) in nodes():
            if text == label:
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(2.5)
                return
        time.sleep(1.5)
    sys.exit(f"no node with text {label!r}")


def capture(name: str):
    pats = ui_copy()
    redact = [b for t, b in nodes() if t and not is_ui_copy(t, pats)]
    OUT.mkdir(parents=True, exist_ok=True)
    out = OUT / f"{name}.png"
    # Blur in one magick pass from stdin, so no unredacted file is written.
    cmd = ["magick", "png:-"]
    for x1, y1, x2, y2 in redact:
        cmd += ["-region", f"{x2 - x1}x{y2 - y1}+{x1}+{y1}", "-blur", "0x18"]
    cmd += ["+region", str(out)]
    subprocess.run(cmd, input=adb("exec-out", "screencap", "-p", binary=True), check=True)
    print(f"{out} ({len(redact)} text regions blurred)")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("name")
    ap.add_argument("--launch", action="store_true", help="force-stop and start the dev app with blur_images")
    ap.add_argument("--tap", action="append", default=[], help="tap the node with this exact text first (repeatable)")
    ap.add_argument("--wait", type=float, default=0)
    a = ap.parse_args()
    if a.launch:
        # Demo mode: clean status bar (12:00, full battery, no notification icons).
        adb("shell", "settings", "put", "global", "sysui_demo_allowed", "1")
        for cmd in (["enter"], ["clock", "-e", "hhmm", "1200"], ["battery", "-e", "level", "100", "-e", "plugged", "false"],
                    ["network", "-e", "wifi", "show", "-e", "level", "4", "-e", "mobile", "hide"], ["notifications", "-e", "visible", "false"]):
            adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", *cmd)
        adb("shell", "am", "force-stop", APP)
        adb("shell", "am", "start", "-n", f"{APP}/net.venipa.stashling.MainActivity", "--ez", "blur_images", "true")
        time.sleep(6)
    for label in a.tap:
        tap(label)
    time.sleep(a.wait)
    capture(a.name)


if __name__ == "__main__":
    # Self-check of the text filter.
    p = [re.compile("Favorite performers"), re.compile(".+ scenes")]
    assert is_ui_copy("824 scenes", p) and is_ui_copy("30:35", p) and is_ui_copy("Favorite performers", p)
    assert not is_ui_copy("box.home.local · 2 presets", [re.compile(".+ · .+ presets")])
    assert not is_ui_copy("Haru Minami", p) and not is_ui_copy("Tushy · 2026-11-15 · 30:35", p)
    main()
