#!/usr/bin/env bash
# Quick signed release build (skips tests). Wraps gradlew assembleRelease with the keystore the
# build expects (STASHLING_KEYSTORE_*/STASHLING_KEY_*).
#
# Usage:
#   ./release.sh --keystore /keys/stashling.jks --store-pass secret --alias upload --key-pass secret
#   ./release.sh --bundle --version-name 1.2.3 --version-code 10203   # reuses keystore from env vars
#
# Without flags it reads the four values from the environment, so you can export them once and just run ./release.sh.
set -euo pipefail
cd "$(dirname "$0")"

keystore="${STASHLING_KEYSTORE_FILE:-}"
store_pass="${STASHLING_KEYSTORE_PASSWORD:-}"
alias="${STASHLING_KEY_ALIAS:-}"
key_pass="${STASHLING_KEY_PASSWORD:-}"
version_name=""
version_code=""
bundle=0

while [ $# -gt 0 ]; do
  case "$1" in
    --keystore)     keystore="$2"; shift 2 ;;
    --store-pass)   store_pass="$2"; shift 2 ;;
    --alias)        alias="$2"; shift 2 ;;
    --key-pass)     key_pass="$2"; shift 2 ;;
    --version-name) version_name="$2"; shift 2 ;;
    --version-code) version_code="$2"; shift 2 ;;
    --bundle)       bundle=1; shift ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

# The build stays unsigned unless all four are set; fail early instead of shipping an unsigned artifact.
[ -n "$key_pass" ] || key_pass="$store_pass"
missing=""
[ -n "$keystore" ]   || missing="$missing keystore(--keystore/STASHLING_KEYSTORE_FILE)"
[ -n "$store_pass" ] || missing="$missing store-pass(--store-pass/STASHLING_KEYSTORE_PASSWORD)"
[ -n "$alias" ]      || missing="$missing alias(--alias/STASHLING_KEY_ALIAS)"
[ -n "$key_pass" ]   || missing="$missing key-pass(--key-pass/STASHLING_KEY_PASSWORD)"
if [ -n "$missing" ]; then echo "Missing signing input(s):$missing" >&2; exit 1; fi

# gradle file() is module-relative; pass an absolute path.
case "$keystore" in /*) : ;; *) keystore="$(cd "$(dirname "$keystore")" && pwd)/$(basename "$keystore")" ;; esac

export STASHLING_KEYSTORE_FILE="$keystore"
export STASHLING_KEYSTORE_PASSWORD="$store_pass"
export STASHLING_KEY_ALIAS="$alias"
export STASHLING_KEY_PASSWORD="$key_pass"
[ -n "$version_name" ] && export STASHLING_VERSION_NAME="$version_name"
[ -n "$version_code" ] && export STASHLING_VERSION_CODE="$version_code"

if [ "$bundle" -eq 1 ]; then task=":app:bundleRelease"; else task=":app:assembleRelease"; fi
# assembleRelease does not run unit tests, but exclude them explicitly so --bundle or future task graphs stay test-free.
./gradlew "$task" -x test -x lint

if [ "$bundle" -eq 1 ]; then out_dir="app/build/outputs/bundle/release"; else out_dir="app/build/outputs/apk/release"; fi
ls -1 "$out_dir"/*.a[ap][bk] 2>/dev/null | while read -r f; do echo "Built: $(pwd)/$f"; done
