"""Check a running emulator's release UI at phone/tablet widths (no USB writes).

Usage: python scripts/emulator_smoke.py /path/to/adb emulator-5554
Install the APK first. Only explicitly named emulators are accepted.
"""
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

PACKAGE = "de.jtracht.piimager"
ROOT = Path(__file__).resolve().parents[1]


def main():
    adb, serial = sys.argv[1:3]
    if not serial.startswith("emulator-"):
        raise ValueError("Use an emulator, never a personal device")

    def run(*arguments):
        return subprocess.check_output([adb, "-s", serial, *arguments], text=True, encoding="utf-8", errors="replace").strip()

    def hierarchy():
        run("shell", "uiautomator", "dump", "/sdcard/pi-imager-smoke.xml")
        return ET.fromstring(run("shell", "cat", "/sdcard/pi-imager-smoke.xml"))

    screenshots = ROOT / "docs/screenshots"
    screenshots.mkdir(parents=True, exist_ok=True)
    try:
        run("shell", "cmd", "locale", "set-app-locales", PACKAGE, "--user", "0", "--locales", "de")
        run("shell", "wm", "density", "320")
        for width in (320, 360, 411, 600, 840):
            height = max(800, min(1200, width * 2))
            run("shell", "wm", "size", f"{width * 2}x{height * 2}")
            run("shell", "am", "force-stop", PACKAGE)
            run("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
            tree = hierarchy()
            assert any(n.get("text") == "Dein Pi startet hier." for n in tree.iter("node")), f"Title missing at {width}dp"
            run("shell", "screencap", "-p", "/sdcard/pi-imager-smoke.png")
            run("pull", "/sdcard/pi-imager-smoke.png", str(screenshots / f"width-{width}.png"))
            for _ in range(3):
                parents = {child: parent for parent in tree.iter() for child in parent}
                button_text = next((n for n in tree.iter("node") if n.get("text") == "Image schreiben"), None)
                if button_text is not None:
                    assert parents[button_text].get("enabled") == "false", "Writing must be disabled without a target and image"
                    break
                run("shell", "input", "swipe", str(width), str(height * 2 - 150), str(width), "250", "350")
                tree = hierarchy()
            else:
                raise AssertionError(f"Write controls unreachable at {width}dp")
            print(f"{width}dp: app launch, reachable write controls, missing-target guard passed")
    finally:
        run("shell", "wm", "size", "reset")
        run("shell", "wm", "density", "reset")


if __name__ == "__main__":
    main()
