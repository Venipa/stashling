# Stashling

Android app for browsing [StashDB](https://stashdb.org) (stash-box) with local bookmarks, image galleries and Whisparr v3 integration (request, search, pick releases, track downloads).

Personal / homelab project. No Google Play services, no tracking, no billing.


## Layout

Bun workspace:

- `apps/android/`: the Android app (Gradle; run `./gradlew` from there)
- `apps/docs/`: docs and landing page (Fumadocs on Next.js, Bun runtime)

```sh
bun install
bun run docs:dev        # http://localhost:3000
bun run docs:build
bun run android:build   # debug APK (Git Bash / macOS)
bun run android:check   # what CI runs
```

The site deploys to GitHub Pages (`.github/workflows/pages.yml`) on docs changes to master and every night, so the stars
and latest release on the landing page stay fresh. One-time setup: Settings → Pages → Source "GitHub Actions".
Screenshots for the site: `python apps/docs/scripts/capture.py --launch home` (dev app on adb; images and non-UI text blurred).

## Releasing

Local: put the keystore and its values in `apps/android/keys/` (gitignored: `apps/android/keys/stashling-release.jks` and `apps/android/keys/release.properties` with
`STASHLING_KEYSTORE_FILE`, `STASHLING_KEYSTORE_PASSWORD`, `STASHLING_KEY_ALIAS`, `STASHLING_KEY_PASSWORD`), then
`apps/android/release.ps1 -VersionName 1.0.0 -VersionCode 1`. Back up `apps/android/keys/`: updates must be signed with the same key.

CI: `.github/workflows/release.yml` builds the signed APK on a `v*` tag (and attaches it to a GitHub Release) or by hand.
Repository secrets it needs:

- `STASHLING_KEYSTORE_BASE64`: the keystore file, base64 (`base64 -w0 apps/android/keys/stashling-release.jks`)
- `STASHLING_KEYSTORE_PASSWORD`, `STASHLING_KEY_ALIAS`, `STASHLING_KEY_PASSWORD`
- optional `GRADLE_ENCRYPTION_KEY` (lets setup-gradle restore the configuration cache)

`.github/workflows/ci.yml` runs `assembleDebug testDebugUnitTest spotlessCheck` and the docs build on master and `feat/**`.

## License

GPL-3.0-only. See [LICENSE](LICENSE).
