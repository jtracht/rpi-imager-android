"""Validate the pinned upstream and expose its config and attribution to Android."""
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def prepare():
    values = dict(line.split("=", 1) for line in (ROOT / "upstream.properties").read_text().splitlines() if "=" in line)
    actual = subprocess.check_output(["git", "-C", str(ROOT / "upstream"), "rev-parse", "HEAD"], text=True).strip()
    if actual != values["commit"]:
        raise RuntimeError("Upstream submodule differs from recorded commit")
    config = (ROOT / "upstream/src/config.h").read_text()
    match = re.search(r'#define\s+OSLIST_URL\s+"(https://[^"\s]+)"', config)
    if not match or "QCryptographicHash::Sha256" not in config:
        raise RuntimeError("Upstream catalog contract changed; Android adapter needs review")
    assets = ROOT / "app/src/main/assets"
    assets.mkdir(parents=True, exist_ok=True)
    (assets / "upstream.json").write_text(json.dumps({**values, "catalog_url": match[1]}, indent=2) + "\n")
    (assets / "UPSTREAM-LICENSE.txt").write_bytes((ROOT / "upstream/license.txt").read_bytes())
    return values


if __name__ == "__main__":
    print(json.dumps(prepare()))
