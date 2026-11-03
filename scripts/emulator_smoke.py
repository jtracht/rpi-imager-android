"""Check a running emulator's step-by-step UI at phone/tablet widths (no USB writes).

Usage: python scripts/emulator_smoke.py /path/to/adb emulator-5554
Install the APK first. Only explicitly named emulators are accepted.
"""
from pathlib import Path
import re
import subprocess
import sys
import time
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

    def button(tree, text):
        """Compose puts the label in a child node; the clickable, enable-able node is its parent."""
        parents = {child: parent for parent in tree.iter() for child in parent}
        label = next((n for n in tree.iter("node") if n.get("text") == text), None)
        assert label is not None, f"{text!r} not found"
        return parents[label]

    def tap(node):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
        run("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))

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
            # Choosing a model moves on by itself; the model step has no Next button.
            assert not any(n.get("text") == "Weiter" for n in tree.iter("node")), f"Model step must not show Next at {width}dp"
            for _ in range(10):
                options = [n for n in tree.iter("node") if n.get("text") == "Alle Modelle"]
                if options:
                    break
                time.sleep(1)
                tree = hierarchy()
            else:
                raise AssertionError(f"Model options missing at {width}dp")
            # The option card is the last match; wide layouts also list the model in the step rail.
            tap(options[-1])
            # The step transition is animated; slow software-rendered emulators need a moment.
            for _ in range(6):
                time.sleep(1)
                tree = hierarchy()
                if any(n.get("text") == "Betriebssystem wählen" for n in tree.iter("node")):
                    break
            else:
                raise AssertionError(f"Image step missing at {width}dp")
            assert button(tree, "Weiter").get("enabled") == "false", "Later steps must stay locked without an image"
            assert not any(n.get("text") == "Image schreiben" for n in tree.iter("node")), "Write control visible too early"
            # The catalog is listed in the step; the local image is offered below it.
            for _ in range(15):
                if any((n.get("text") or "").startswith("Raspberry Pi OS") for n in tree.iter("node")):
                    break
                time.sleep(1)
                tree = hierarchy()
            else:
                raise AssertionError(f"Catalog not listed in the image step at {width}dp")
            for _ in range(10):
                texts = {n.get("text") for n in tree.iter("node")}
                if "Lokales Image auswählen" in texts:
                    break
                run("shell", "input", "swipe", str(width), str(height * 2 - 400), str(width), "500", "300")
                tree = hierarchy()
            else:
                raise AssertionError(f"Local image option missing below the catalog at {width}dp")
            print(f"{width}dp: launch, model selection advances, catalog with local option, missing-image guard passed")
    finally:
        run("shell", "wm", "size", "reset")
        run("shell", "wm", "density", "reset")


if __name__ == "__main__":
    main()
