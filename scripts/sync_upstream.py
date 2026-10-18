"""Poll published upstream releases, pin their exact tag, and queue an Android release.

Runs with GITHUB_TOKEN in the downstream repository. Never merges desktop workflows.
The calling workflow invokes the reusable release workflow explicitly, since tags
created with GITHUB_TOKEN do not trigger another push workflow.
"""
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = "raspberrypi/rpi-imager"


def command(*args):
    return subprocess.check_output(list(args), cwd=ROOT, text=True).strip()


def api(path):
    return json.loads(command("gh", "api", path))


def android_tag(tag):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+-]{0,100}", tag):
        raise ValueError(f"Unsupported upstream tag: {tag!r}")
    return f"upstream/{tag}-android.1"


def choose_release(releases, current_tag):
    published = sorted((r for r in releases if not r["draft"] and not r["prerelease"]), key=lambda r: (r["published_at"], r["id"]))
    current = next((r for r in published if r["tag_name"] == current_tag), None)
    if current is None:
        raise RuntimeError("Pinned upstream release is absent; review release history before syncing")
    newer = [r for r in published if (r["published_at"], r["id"]) > (current["published_at"], current["id"])]
    return newer[0] if newer else current


def release_exists(repo, tag):
    # --jq avoids treating an authorization/network failure as an absent release.
    releases = json.loads(command("gh", "api", "--paginate", "--slurp", f"repos/{repo}/releases?per_page=100"))
    return any(r["tag_name"] == tag and not r["draft"] for page in releases for r in page)


def output(tag):
    if path := os.environ.get("GITHUB_OUTPUT"):
        with open(path, "a", encoding="utf-8") as stream:
            stream.write(f"tag={tag}\n")
    print(tag or "Already up to date")


def main():
    repo = os.environ["GITHUB_REPOSITORY"]
    values = dict(line.split("=", 1) for line in (ROOT / "upstream.properties").read_text().splitlines() if "=" in line)
    current_android = android_tag(values["tag"])
    remote_tags = command("git", "ls-remote", "--tags", "origin", f"refs/tags/{current_android}")
    # Retry a failed build before advancing the pointer to a newer source.
    if remote_tags and not release_exists(repo, current_android):
        output(current_android)
        return
    pages = json.loads(command("gh", "api", "--paginate", "--slurp", f"repos/{UPSTREAM}/releases?per_page=100"))
    release = choose_release([r for page in pages for r in page], values["tag"])
    tag = release["tag_name"]
    target = android_tag(tag)
    if release_exists(repo, target):
        output("")
        return
    command("git", "-C", "upstream", "fetch", "origin", f"refs/tags/{tag}:refs/tags/{tag}")
    command("git", "-C", "upstream", "checkout", "--detach", f"refs/tags/{tag}")
    commit = command("git", "-C", "upstream", "rev-parse", "HEAD")
    (ROOT / "upstream.properties").write_text(f"tag={tag}\ncommit={commit}\n")
    from prepare_upstream import prepare
    prepare()
    command("git", "add", "upstream", "upstream.properties", "app/src/main/assets")
    if command("git", "diff", "--cached", "--name-only"):
        command("git", "commit", "-m", f"chore: track Raspberry Pi Imager {tag}")
        command("git", "push", "origin", "HEAD:main")
    command("git", "tag", "-a", target, "-m", f"Android build of Raspberry Pi Imager {tag} ({commit})")
    command("git", "push", "origin", f"refs/tags/{target}")
    output(target)


if __name__ == "__main__":
    main()
