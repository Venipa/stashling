#!/usr/bin/env bash
# Renders public/og.png (1200x630 share image) from scripts/og/og.html with headless Chrome + ImageMagick.
# Run from apps/docs in Git Bash or on macOS/Linux; re-run after new screenshots (scripts/capture.py).
set -euo pipefail
cd "$(dirname "$0")/../.."
CHROME=${CHROME:-"/c/Program Files/Google/Chrome/Application/chrome.exe"}
tmp=$(mktemp -d)
path() { if command -v cygpath >/dev/null; then cygpath -m "$1"; else echo "$1"; fi; }
"$CHROME" --headless=new --disable-gpu --hide-scrollbars --force-device-scale-factor=1 --allow-file-access-from-files \
  --window-size=1200,630 --virtual-time-budget=2000 --screenshot="$(path "$tmp")/raw.png" "file:///$(path "$PWD")/scripts/og/og.html" 2>/dev/null
magick "$tmp/raw.png" -crop 1200x630+0+0 +repage -strip PNG24:public/og.png
rm -rf "$tmp"
magick identify public/og.png
