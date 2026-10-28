#!/usr/bin/env bash
set -euo pipefail
if ! command -v tesseract >/dev/null 2>&1 || ! tesseract --list-langs 2>/dev/null | grep -qx eng; then
  sudo apt-get update -qq
  sudo apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng
fi
tesseract --version 2>&1 | sed -n '1p'
tesseract --list-langs 2>/dev/null | grep -qx eng
