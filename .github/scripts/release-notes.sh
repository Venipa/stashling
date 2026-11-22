#!/usr/bin/env bash
# Release notes for <tag> from the commit subjects since the previous release of the same channel:
# a stable tag (v1.2.0) starts after the previous stable tag, a pre-release (v1.2.0-beta.1) after the previous pre-release.
# Conventional-commit subjects are grouped; docs/ci/chore/build/test/style commits are left out.
# Usage: release-notes.sh <tag> [owner/repo]   (prints markdown)
set -euo pipefail
tag="$1"
repo="${2:-${GITHUB_REPOSITORY:-}}"

case "$tag" in
  *-*) prev=$(git describe --tags --abbrev=0 --match 'v*-*' "$tag^" 2>/dev/null || true) ;;
  *)   prev=$(git describe --tags --abbrev=0 --match 'v*' --exclude 'v*-*' "$tag^" 2>/dev/null || true) ;;
esac
range="${prev:+$prev..}$tag"

subjects=$(git log --no-merges --format='%s' "$range")
type_re='^([a-z]+)(\(([^)]*)\))?!?: (.*)$'

section() { # <title> <type regex>
  local lines
  lines=$(printf '%s\n' "$subjects" | grep -E "^($2)(\(|!|:)" | sed -E "s/$type_re/- **\3** \4/; s/- \*\*\*\* /- /" || true)
  [ -n "$lines" ] && printf '## %s\n\n%s\n\n' "$1" "$lines"
  return 0
}

{
  section "Features" "feat"
  section "Fixes" "fix"
  section "Other changes" "perf|refactor|revert"
  # Subjects without a conventional type still count.
  untyped=$(printf '%s\n' "$subjects" | grep -Ev '^[a-z]+(\([^)]*\))?!?: ' | grep -v '^$' | sed 's/^/- /' || true)
  [ -n "$untyped" ] && printf '## Changes\n\n%s\n\n' "$untyped"
  if [ -n "$repo" ]; then
    if [ -n "$prev" ]; then
      printf '**Full Changelog**: https://github.com/%s/compare/%s...%s\n' "$repo" "$prev" "$tag"
    else
      printf '**Full Changelog**: https://github.com/%s/commits/%s\n' "$repo" "$tag"
    fi
  fi
}
