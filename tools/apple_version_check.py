"""Check the built iPhone bundle against the version declared in project.yml."""

from __future__ import annotations

import argparse
from pathlib import Path
import plistlib
import re


def declared_version(project: Path) -> tuple[str, str]:
    source = project.read_text(encoding="utf-8")
    values = []
    for key in ("MARKETING_VERSION", "CURRENT_PROJECT_VERSION"):
        matches = re.findall(rf"^\s{{4}}{key}:\s*['\"]?([0-9]+(?:\.[0-9]+)*)['\"]?\s*$", source, re.MULTILINE)
        if len(matches) != 1:
            raise ValueError(f"Expected exactly one numeric {key} in {project}")
        values.append(matches[0])
    return values[0], values[1]


def verify(project: Path, built_plist: Path) -> tuple[str, str]:
    expected = declared_version(project)
    with built_plist.open("rb") as stream:
        actual_info = plistlib.load(stream)
    actual = (str(actual_info.get("CFBundleShortVersionString", "")),
              str(actual_info.get("CFBundleVersion", "")))
    if actual != expected:
        raise ValueError(f"Built iPhone version {actual} does not match project.yml {expected}")
    return actual


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, default=Path("ios/project.yml"))
    parser.add_argument("--plist", type=Path, required=True)
    args = parser.parse_args()
    version, build = verify(args.project, args.plist)
    print(f"Verified iPhone {version} build {build} from {args.plist}")


if __name__ == "__main__":
    main()
