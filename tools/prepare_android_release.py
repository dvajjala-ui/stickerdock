"""Package source-bound Android downloads after build/signature verification."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil


def prepare(apk, bundle, output, source, run_url):
    build = Path("app/build.gradle.kts").read_text(encoding="utf-8")
    version = re.search(r'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"', build).group(1)
    code = int(re.search(r"versionCode\s*=\s*(\d+)", build).group(1))
    if not re.fullmatch(r"[a-f0-9]{40}", source):
        raise ValueError("Use the exact source commit SHA")
    output.mkdir(parents=True, exist_ok=True)
    files = {}
    for path, name in ((apk, "StickerDock.apk"), (bundle, "StickerDock.aab")):
        target = output / name
        shutil.copyfile(path, target)
        files[name] = {"sha256": hashlib.sha256(target.read_bytes()).hexdigest(), "bytes": target.stat().st_size}
    metadata = {"version": version, "version_code": code, "source_sha": source,
                "built_at": datetime.now(timezone.utc).isoformat(), "checks_url": run_url,
                "apk_url": "https://github.com/dvajjala-ui/stickerdock/releases/latest/download/StickerDock.apk",
                "admin_url": "https://stickerdock.vercel.app/admin", "files": files,
                "acceptance": "Signed APK emulator acceptance must pass before publication; physical WhatsApp/Business validation remains separate."}
    (output / "release.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    hashes = {**{name: item["sha256"] for name, item in files.items()},
              "release.json": hashlib.sha256((output / "release.json").read_bytes()).hexdigest()}
    (output / "SHA256SUMS.txt").write_text("".join(f"{digest}  {name}\n" for name, digest in hashes.items()), encoding="utf-8")
    (output / "release-notes.md").write_text(
        f"Android {version} (build {code})\n\n"
        "- Bounded static and animated grid posters, playback on opening a sticker, and background playback cleanup.\n"
        "- Streaming GIF/video compression, low-storage save protection, and wrapping large-text actions.\n"
        "- Daily Factory schema enforcement and persisted bounded recovery for invalid AI responses.\n\n"
        f"Source: `{source}`. [Build and acceptance evidence]({run_url}).\n\n"
        "The signed APK passes native unit checks and AOSP emulator acceptance before publication. "
        "Real-phone WhatsApp/Business acceptance remains separate. "
        "Use the same signing identity to update an existing release installation.\n\n"
        "[Admin website](https://stickerdock.vercel.app/admin) · "
        "[Checksums](https://github.com/dvajjala-ui/stickerdock/releases/latest/download/SHA256SUMS.txt)\n",
        encoding="utf-8")
    return version


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("work/release"))
    args = parser.parse_args()
    version = prepare(args.apk, args.bundle, args.output, os.environ["GITHUB_SHA"],
                      f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as handle:
            handle.write(f"version={version}\n")
    print(f"Prepared Android {version}")
