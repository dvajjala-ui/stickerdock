#!/usr/bin/env python3
"""Retired Pinterest archiver entry point.

Pinterest is a live, read-only reference connection. Keep this module as an
explicit failure for old local commands; scheduled health checks use
``python -m tools.pinterest_connection`` instead.
"""
from __future__ import annotations


def main() -> None:
    raise SystemExit(
        "Pin archiving is retired. Use python -m tools.pinterest_connection "
        "for official connection checks."
    )


if __name__ == "__main__":
    main()
