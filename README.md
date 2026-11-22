# Stashling

Android app for browsing [StashDB](https://stashdb.org) (stash-box) with local bookmarks, image galleries and Whisparr v3 integration (request, search, pick releases, track downloads).

Personal / homelab project. No Google Play services, no tracking, no billing.

## Releasing

Local: put the keystore and its values in `keys/` (gitignored: `keys/stashling-release.jks` and `keys/release.properties` with
`STASHLING_KEYSTORE_FILE`, `STASHLING_KEYSTORE_PASSWORD`, `STASHLING_KEY_ALIAS`, `STASHLING_KEY_PASSWORD`), then
`./release.ps1 -VersionName 1.0.0 -VersionCode 1`. Back up `keys/`: updates must be signed with the same key.

CI: `.github/workflows/release.yml` builds the signed APK on a `v*` tag (and attaches it to a GitHub Release) or by hand.
Repository secrets it needs:

- `STASHLING_KEYSTORE_BASE64`: the keystore file, base64 (`base64 -w0 keys/stashling-release.jks`)
- `STASHLING_KEYSTORE_PASSWORD`, `STASHLING_KEY_ALIAS`, `STASHLING_KEY_PASSWORD`
- optional `GRADLE_ENCRYPTION_KEY` (lets setup-gradle restore the configuration cache)

`.github/workflows/ci.yml` runs `assembleDebug testDebugUnitTest spotlessCheck` on master and `feat/**`.

## License

GPL-3.0-only. See [LICENSE](LICENSE).
