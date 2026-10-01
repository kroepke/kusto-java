#!/usr/bin/env python3
"""Sparse, shallow checkouts of the pinned corpus repos into build/corpora/<name>/.

Idempotent: re-running at the same pin is a no-op fetch + checkout.
"""
import json, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LICENSE_FILES = ["LICENSE", "LICENSE.md", "LICENSE-CODE", "LICENSE.txt"]


def git(repo, *args, check=True):
    return subprocess.run(["git", "-C", str(repo), *args], check=check,
                          capture_output=True, text=True)


def fetch(entry):
    dest = ROOT / "build" / "corpora" / entry["name"]
    dest.mkdir(parents=True, exist_ok=True)
    if not (dest / ".git").exists():
        subprocess.run(["git", "init", "-q", str(dest)], check=True)
    if git(dest, "remote", "get-url", "origin", check=False).returncode != 0:
        git(dest, "remote", "add", "origin", entry["url"])
    git(dest, "config", "core.autocrlf", "false")
    git(dest, "config", "core.sparseCheckout", "true")
    git(dest, "config", "core.sparseCheckoutCone", "false")
    # Sparse patterns: top-level license files + the pinned paths.
    pats = ["/" + n for n in LICENSE_FILES] + ["/" + p for p in entry["sparsePaths"]]
    info = dest / ".git" / "info"
    info.mkdir(exist_ok=True)
    (info / "sparse-checkout").write_text("\n".join(pats) + "\n", encoding="utf-8")
    sha = entry["commit"]
    have = git(dest, "rev-parse", "-q", "--verify", "HEAD", check=False).stdout.strip()
    if have == sha:
        print(f"{entry['name']}: already at {sha}")
        git(dest, "checkout", "-q", "-f", sha)  # re-apply sparse patterns
        return
    print(f"{entry['name']}: fetching {sha} ...", flush=True)
    r = subprocess.run(["git", "-C", str(dest), "fetch", "--depth", "1",
                        "--filter=blob:none", "origin", sha])
    if r.returncode != 0:
        sys.exit(f"fetch failed for {entry['name']}")
    git(dest, "checkout", "-q", "-f", sha)
    print(f"{entry['name']}: checked out {sha}")


def main():
    cfg = json.loads((ROOT / "porting" / "corpora.json").read_text(encoding="utf-8"))
    for e in cfg["repos"]:
        fetch(e)


if __name__ == "__main__":
    main()
