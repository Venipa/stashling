# Stashling

Android app for browsing [StashDB](https://stashdb.org) (stash-box) with local bookmarks, image galleries and Whisparr v3
integration. Personal / homelab project: no Google Play services, no tracking, no billing.

Website and docs: <https://venipa.github.io/stashling> (built from `apps/docs`).

## Features

- Browse scenes, performers, studios and tags, with filters, search history and detail pages
- Library on the phone: bookmarks, favourites, hidden items, downloads; backup export / import
- Whisparr v3: add and search scenes with presets and tag rules, pick releases, follow downloads
- Privacy: Blur images, app lock (biometrics), secure screen (no screenshots, blank in recents)
- Material You (dynamic colour, OLED black), dark and light themes

Requires Android 12 or newer.

## Install

Download the signed APK from [GitHub Releases](https://github.com/Venipa/stashling/releases/latest). Updates must be
signed with the same key, so install new versions over the existing app.

## Development

Bun workspace:

- `apps/android/`: the Android app (Gradle, Kotlin, Jetpack Compose; run `./gradlew` from there)
- `apps/docs/`: landing page and docs (Fumadocs on Next.js, Bun runtime, static export); pages in `apps/docs/content/docs/`

Needs JDK 22, the Android SDK (platform 37, build-tools 36) and [Bun](https://bun.sh).

```sh
bun install
bun run docs:dev        # http://localhost:3000
bun run docs:build      # static site in apps/docs/out
bun run android:build   # debug APK (Git Bash / macOS)
bun run android:check   # what CI runs: assembleDebug testDebugUnitTest spotlessCheck
```

Screenshots for the site come from the dev app on an adb device:
`python apps/docs/scripts/capture.py --launch home`. The app blurs images (`blur_images` launch extra) and the script
blurs all text that is not the app's own UI copy, so no titles, names or hosts end up in the images.

## Releasing

Local: put the keystore and its values in `apps/android/keys/` (gitignored: `stashling-release.jks` and
`release.properties` with `STASHLING_KEYSTORE_FILE`, `STASHLING_KEYSTORE_PASSWORD`, `STASHLING_KEY_ALIAS`,
`STASHLING_KEY_PASSWORD`), then `apps/android/release.ps1 -VersionName 1.0.0 -VersionCode 1` (or `release.sh`).
Back up `apps/android/keys/`: updates must be signed with the same key.

## CI

- `ci.yml`: Android checks (`assembleDebug testDebugUnitTest spotlessCheck`) and the docs build on master and `feat/**`.
- `release.yml`: signed APK on a `v*` tag, attached to a **draft** GitHub Release with notes since the previous release of the
  same channel (stable or pre-release); review and publish it by hand. Also runs by hand (artifact only). Repository secrets:
  - `STASHLING_KEYSTORE_BASE64`: the keystore, base64 (`base64 -w0 apps/android/keys/stashling-release.jks`)
  - `STASHLING_KEYSTORE_PASSWORD`, `STASHLING_KEY_ALIAS`, `STASHLING_KEY_PASSWORD`
  - optional `GRADLE_ENCRYPTION_KEY` (lets setup-gradle restore the configuration cache)
- `pages.yml`: deploys the site to GitHub Pages on docs changes to master, by hand, and every night so the GitHub stars
  and latest release on the landing page stay fresh. One-time setup: Settings → Pages → Source "GitHub Actions".

## License

GPL-3.0-only. See [LICENSE](LICENSE).
