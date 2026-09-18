# MeldPDF

`com.mikmy.meldpdf` — an Android app built with the android-app-dev pipeline.

## Building

There is no local Android SDK requirement — everything runs in GitHub Actions:

| Workflow | Trigger | What it does |
| --- | --- | --- |
| **Build APK** (`android.yml`) | every push / PR | unit tests, lint, debug+release APK, release AAB |
| **Emulator smoke test** (`emulator.yml`) | push to master/dev | boots an emulator, plays the app, captures screenshots + logcat |
| **Signed release bundle** (`release.yml`) | manual | signed AAB for manual upload |
| **Publish to Play** (`publish.yml`) | manual | signed AAB straight to an internal/closed track |
| **Play status** (`play-status.yml`) | manual | read-only check of track/release state via the Play API |

To build locally instead, install Android Studio and run `./gradlew assembleDebug`.

## Architecture

- `app/src/main/java/com/mikmy/meldpdf/Rules.kt` — pure logic, JVM-unit-tested, no Android imports.
- `app/src/main/java/com/mikmy/meldpdf/MainActivity.kt` — the launch surface.

Keep game/app rules in the pure layer so CI can validate them without an emulator.

## Releasing

See `PLAYSTORE.md` for store setup, and the android-app-dev skill's
`references/signing-publishing.md` and `references/store-submission.md`.

## Privacy

`PRIVACY.md` is the privacy policy (required by Play because the app collects the
advertising ID). Host it and paste the URL into the Play listing.
