#!/usr/bin/env bash
set -euo pipefail

# GitHub's Ubuntu image may omit FFmpeg. Use a pinned wheel instead of a large,
# occasionally stalled apt index update. Pillow has its own scalable font
# fallback when the image does not carry DejaVu.
if command -v ffmpeg >/dev/null 2>&1; then
  ffmpeg -version | sed -n '1p'
  exit 0
fi

python -m pip install --only-binary=:all: imageio-ffmpeg==0.6.0
ffmpeg_exe="$(python -c 'import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())')"
test -x "$ffmpeg_exe"
tool_dir="${RUNNER_TEMP:?}/stickerdock-media-tools"
mkdir -p "$tool_dir"
ln -s "$ffmpeg_exe" "$tool_dir/ffmpeg"
echo "$tool_dir" >> "${GITHUB_PATH:?}"
"$tool_dir/ffmpeg" -version | sed -n '1p'
