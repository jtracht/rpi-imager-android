"""Run the isolated background-preparation probe on an explicitly named emulator.

Usage: python scripts/emulator_preparation.py /path/to/adb emulator-5554
Requires matching debug app and androidTest APKs installed. No network or USB writes.
"""
from pathlib import Path
import subprocess
import sys


def main():
    adb, serial = sys.argv[1:3]
    if not serial.startswith("emulator-"):
        raise ValueError("This test only supports explicitly named emulators")
    base = [adb, "-s", serial]
    subprocess.run(base + ["shell", "pm", "grant", "de.jtracht.piimager", "android.permission.POST_NOTIFICATIONS"], check=True)
    subprocess.run(base + ["shell", "am", "force-stop", "de.jtracht.piimager"], check=True)
    result = subprocess.run(base + ["shell", "am", "instrument", "-w", "-r", "de.jtracht.piimager.test/de.jtracht.piimager.PreparationProbe"],
                            capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=100)
    log = result.stdout + result.stderr
    folder = Path(__file__).resolve().parents[1] / "app/build/reports/preparation-probe"
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "result.txt").write_text(log, encoding="utf-8")
    print(log)
    if result.returncode or "INSTRUMENTATION_CODE: -1" not in log or "PASS: background preparation" not in log:
        raise RuntimeError("Android preparation integration probe failed")


if __name__ == "__main__":
    main()
