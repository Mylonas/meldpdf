# MeldPDF — Play Store listing pack

Copy-paste into Play Console. Package: `com.mikmy.meldpdf` (permanent).

## Basics
- **App name:** MeldPDF
- **App or game:** App
- **Category:** Productivity (alt: Tools)
- **Free / Paid:** Free (ad-supported)
- **Default language:** English (United States) – en-US

## Short description (≤80 chars)
Merge, split, compress, convert & sign PDFs — fast, private, all on-device.

## Full description (≤4000 chars)
MeldPDF puts a complete PDF toolkit in your pocket — and everything runs on your
device. Your files are never uploaded to a server, so your documents stay
private.

ORGANIZE
• Merge several PDFs into one
• Split or extract specific pages
• Delete pages you don't need
• Rotate pages
• Organize: reorder, rotate and delete individual pages, then save

CONVERT
• Compress a PDF to shrink its size for email or upload limits
• Images → PDF (combine JPG/PNG into one document)
• PDF → JPG and PDF → PNG (export every page as an image)
• PDF → Word (.docx) for editable text

EDIT & STAMP
• Add page numbers
• Add a text watermark
• Sign: draw your signature and place it anywhere on a page

TEXT & SECURITY
• Extract text from a PDF
• View and strip hidden metadata
• Password-protect a PDF, or unlock one you have the password for

Why MeldPDF:
• Private by design — every operation happens on-device, nothing is uploaded
• Fast and simple — pick a tool, choose your file, done
• No account, no sign-up

MeldPDF is the native Android version of meldpdf.com.

## App content declarations
- **Ads:** Yes, contains ads (Google AdMob).
- **Target audience:** 13+.
- **App access:** All functionality available without a login (tick "no special access").
- **Content rating:** complete questionnaire — no violence, no UGC, no user
  accounts. A utility app lands in the lowest brackets (Everyone).
- **Privacy policy URL:** host PRIVACY.md and paste the URL
  (e.g. https://meldpdf.com/app-privacy or a GitHub Pages URL).

## Data safety answers (AdMob → Advertising ID)
- Does the app collect or share user data? **Yes**
- Data type: **Device or other IDs → Advertising ID**
  - Collected: **Yes**  ·  Shared: **Yes**
  - Purpose: **Advertising or marketing**
  - Processed ephemerally: **No**  ·  User can request deletion: **No**
  - Encrypted in transit: **Yes**
- No other data types (no account, no files leave the device, no analytics of our own).
- (No in-app purchases in v1.0.0, so no "Purchase history".)

## Assets (generated)
- Icon 512×512: `store/icon-512.png`
- Feature graphic 1024×500: `store/feature-graphic-1024x500.png`
- Screenshots (2–8): from the emulator run's `*-emulator` artifact → `shots/*.png`

## Release checklist
1. Create the app in Play Console (name, App, Free, en-US).
2. Complete Main Store Listing (paste above + upload assets + ≥2 screenshots).
3. App content: ads=Yes, target 13+, data safety as above, privacy URL.
4. Create the Play service account, invite it with "Release apps to testing
   tracks", and add its JSON as the `PLAY_SERVICE_ACCOUNT_JSON` repo secret.
5. Run `store/push-secrets.sh` (signing + AdMob ids) — needs the MeldPDF AdMob
   App ID + interstitial unit id.
6. Run the **Publish to Play** workflow → track: internal, status: completed
   (internal accepts a draft app outright).
7. For a closed track later: add ≥12 testers, opted in for 14 days.
