# MeldPDF

`com.mikmy.meldpdf` — the native Android version of [meldpdf.com](https://meldpdf.com):
every PDF tool, running entirely on-device. Nothing is uploaded. Built with the
android-app-dev pipeline (Kotlin + Jetpack Compose, PDFBox-Android for document
work, Android's PdfRenderer for rasterisation, ML Kit for OCR).

## Tools

| Group | Tools |
| --- | --- |
| Organize | Merge · Split/Extract · Delete pages · Rotate · *Organize (soon)* |
| Convert | *Compress (soon)* · Images→PDF · PDF→JPG · PDF→PNG · *PDF→Word (soon)* |
| Edit & stamp | Page numbers · Watermark · *Sign (soon)* |
| Text | Extract text · *OCR (soon)* |
| Security | Metadata view/strip · Protect/Unlock (password) |

The pure page-range/format logic lives in `Rules.kt` and is unit-tested in CI;
all PDF operations live in `pdf/PdfEngine.kt`.

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

- `Rules.kt` — pure logic (page-range parsing, size formatting), JVM-unit-tested, no Android imports.
- `Tool.kt` — the tool catalog (mirrors the website's tool set).
- `pdf/PdfEngine.kt` — all PDF operations (PDFBox + PdfRenderer).
- `ui/` — Compose UI: `HomeScreen` (tool grid) and `ToolScreen` (pick → options → run → save/share).
- `MainActivity.kt` / `MeldApp.kt` — Compose host and PDFBox init.

Keep app rules in the pure layer so CI can validate them without an emulator.

## Releasing

See `PLAYSTORE.md` for store setup, and the android-app-dev skill's
`references/signing-publishing.md` and `references/store-submission.md`.

## Privacy

`PRIVACY.md` is the privacy policy (required by Play because the app collects the
advertising ID). Host it and paste the URL into the Play listing.
