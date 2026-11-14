# Changelog

All notable changes to MeldPDF are documented here. This project follows
[Semantic Versioning](https://semver.org/).

## [1.4.0] - 2026-11-14

### Added
- **Searchable PDF from OCR:** as well as the plain text, OCR now returns the
  document itself with the recognised words laid over each page as invisible
  text, so it can be searched, selected and copied in any PDF reader. A photo
  becomes a one-page searchable PDF. Rotated pages are handled.
- **Greek and Russian OCR:** choose the document language before running.
  These use Tesseract with bundled language data; English and other
  Latin-script languages still use ML Kit. All of it runs on the device.

### Changed
- The app is about 9 MB larger because of the Tesseract engine and the two
  language models.

## [1.3.0] - 2026-11-14

### Added
- **OCR scanned:** reads the text out of scanned PDFs and photos, on-device
  (ML Kit, Latin script). Multi-page PDFs come back with a heading per page;
  the first 60 pages are read. Copy the text or share it as a .txt file.
  This completes the tool set: all 17 tools now work.

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
