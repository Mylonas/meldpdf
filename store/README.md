# Store assets for MeldPDF

Everything that goes into the Play listing, and the tooling that produces it.

## Files
- `icon-512.svg` — app icon source (512×512). **Replace the placeholder art.**
- `feature-graphic-1024x500.svg` — feature graphic source (1024×500). Should
  *extend* the icon's visual language, not just repeat the icon.
- `screenshots/` — phone screenshots. Normally you drop the `emulator.yml`
  artifact's `shots/*.png` here (Play needs 2–8).
- `make-upload-key.sh` / `push-secrets.sh` — signing key + CI secrets (see the
  android-app-dev skill's `references/signing-publishing.md`).

## Generate and validate the PNGs
```bash
npm install       # once: @resvg/resvg-js + sharp
npm run gen       # every *.svg here -> Play-ready 32-bit RGBA PNG
npm run validate  # dimensions, depth, opacity, size, naming
```
Generated `*.png` are git-ignored — they rebuild from the SVGs. Icons must be
opaque, so keep a full-frame background rect in the SVG.

## Listing text
Keep a paste-ready `listing.md` here (app name, short + full description, the
Data-safety answers) so filling the Console is copy-paste. See the skill's
`references/store-submission.md`.
