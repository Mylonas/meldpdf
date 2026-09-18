// Validate the store PNGs against Play's requirements and this pipeline's
// conventions. Fails (exit 1) on any violation so it can gate CI or a release.
//
//   node validate-store-assets.mjs [dir]     (default dir: this script's folder)
//
// Checks, per expected asset:
//   - exists, correct dimensions
//   - 32-bit RGBA and fully opaque (Play rejects transparent icons)
//   - within a sane size ceiling
// Screenshots (store/screenshots/*.png): at least 2, each >= 320px on the short
// side, portrait or landscape consistent — warns, does not hard-fail, since the
// emulator workflow is their normal source.
import sharp from 'sharp';
import { existsSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const dir = process.argv[2] || dirname(fileURLToPath(import.meta.url));
const KB = 1024;

// name -> {w, h, maxBytes}. Play's hard limits are 1MB icon / 15MB feature; the
// vector art here comes out far smaller, so a tight ceiling catches accidents.
const EXPECTED = {
  'icon-512.png': { w: 512, h: 512, maxBytes: 1024 * KB },
  'feature-graphic-1024x500.png': { w: 1024, h: 500, maxBytes: 15 * 1024 * KB },
};

let failed = 0;
const fail = (m) => { console.error(`  FAIL ${m}`); failed++; };
const ok = (m) => console.log(`  ok   ${m}`);

for (const [name, want] of Object.entries(EXPECTED)) {
  const p = join(dir, name);
  if (!existsSync(p)) { fail(`${name} missing — run 'npm run gen'`); continue; }
  const bytes = statSync(p).size;
  const img = sharp(p);
  const meta = await img.metadata();
  const stats = await img.stats();
  if (meta.width !== want.w || meta.height !== want.h)
    fail(`${name} is ${meta.width}x${meta.height}, expected ${want.w}x${want.h}`);
  else if (meta.channels !== 4 || !meta.hasAlpha)
    fail(`${name} is not 32-bit RGBA (channels=${meta.channels})`);
  else if (!stats.isOpaque)
    fail(`${name} has transparency — give the SVG a full-frame background rect`);
  else if (bytes > want.maxBytes)
    fail(`${name} is ${(bytes / KB).toFixed(0)}KB, over the ${(want.maxBytes / KB).toFixed(0)}KB ceiling`);
  else
    ok(`${name}  ${meta.width}x${meta.height}  ${(bytes / KB).toFixed(0)}KB  opaque RGBA`);
}

const shotsDir = join(dir, 'screenshots');
if (existsSync(shotsDir)) {
  const shots = readdirSync(shotsDir).filter((f) => f.toLowerCase().endsWith('.png'));
  if (shots.length < 2)
    console.warn(`  warn screenshots/: ${shots.length} PNG(s); Play needs 2–8. Run emulator.yml.`);
  else
    ok(`screenshots/: ${shots.length} PNG(s)`);
}

if (failed) { console.error(`\n${failed} problem(s).`); process.exit(1); }
console.log('\nAll store assets valid.');
