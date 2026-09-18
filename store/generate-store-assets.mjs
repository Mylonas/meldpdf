// Render every *.svg in a directory to a Play-ready PNG at the SVG's intrinsic
// size, re-encoded as 32-bit RGBA (what Play means by "32-bit PNG"). Give each
// SVG a full-frame background rect so the result is opaque even though it carries
// an alpha channel — that is what the validator checks for.
//
//   node generate-store-assets.mjs [dir]     (default dir: this script's folder)
//
// icon-512.svg -> icon-512.png (512x512), feature-graphic-1024x500.svg ->
// feature-graphic-1024x500.png (1024x500). Screenshots are captured by the
// emulator workflow, not generated here.
import { Resvg } from '@resvg/resvg-js';
import sharp from 'sharp';
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const dir = process.argv[2] || dirname(fileURLToPath(import.meta.url));
const svgs = readdirSync(dir).filter((f) => f.toLowerCase().endsWith('.svg'));

if (svgs.length === 0) {
  console.error(`No .svg files in ${dir}`);
  process.exit(1);
}

for (const svg of svgs) {
  const src = readFileSync(join(dir, svg), 'utf8');
  // fitTo "original" renders at the SVG's own width/height.
  const rendered = new Resvg(src, { fitTo: { mode: 'original' } }).render();
  const out = join(dir, svg.replace(/\.svg$/i, '.png'));
  // Round-trip through sharp to guarantee a 32-bit RGBA PNG.
  const png = await sharp(rendered.asPng()).ensureAlpha().png().toBuffer();
  writeFileSync(out, png);
  const meta = await sharp(png).metadata();
  console.log(`${svg} -> ${out.split(/[\\/]/).pop()}  ${meta.width}x${meta.height}  ${(png.length / 1024).toFixed(0)}KB`);
}
