# Changelog

All notable changes to MeldPDF are documented here. This project follows
[Semantic Versioning](https://semver.org/).

## [1.2.1] - 2026-11-11

### Added
- "Privacy choices" link at the bottom of the home screen reopens the
  ad-consent form, shown only where consent applies (EEA, UK, Switzerland).

### Changed
- Build tools and libraries brought up to date: Android Gradle Plugin 9.5,
  Kotlin 2.4 with the Compose compiler plugin, Compose 2026.09, Google Mobile
  Ads SDK 1.6. Compiles against API 37; still targets API 36.

## [1.0.0] - 2026-09-19

First release — a native Android version of [meldpdf.com](https://meldpdf.com).
Every PDF operation runs on-device; nothing is uploaded.

### Tools
- **Organize:** merge, split/extract pages, delete pages, rotate, organize
  (reorder/rotate/delete individual pages).
- **Convert:** compress, images → PDF, PDF → JPG, PDF → PNG, PDF → Word (.docx).
- **Edit & stamp:** page numbers, watermark, sign (draw and place a signature).
- **Text:** extract text.
- **Security:** view & strip metadata, password protect / unlock.

### Notes
- OCR for scanned PDFs is planned for a future update.
- Ad-supported via Google AdMob, with EEA/UK consent (UMP).
