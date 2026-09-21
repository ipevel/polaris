// Convert foreground-only SVG (transparent bg + white star) to PNG
const sharp = require('sharp');
const path = require('path');
const fs = require('fs');

// Foreground-only SVG: no background rect, just the star glyph
// Paths scaled from 1024→108 (scale = 108/1024), translate(16,-6) applied
const svg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <g fill="#F4F7FF">
    <path d="M37.23,17.09 C41.45,35.23 41.45,71.51 37.23,90.91 C33.01,71.51 33.01,35.23 37.23,17.09 Z"/>
    <path d="M37.23,31.01 C53.05,31.01 74.46,36.49 74.46,45.77 C74.46,55.05 53.05,60.54 37.23,60.54 C50.94,59.06 68.77,54.21 68.77,45.77 C68.77,37.34 50.94,32.48 37.23,31.01 Z"/>
  </g>
</svg>`;

const outPath = path.join(__dirname, 'ic_launcher_foreground.png');

async function convert() {
  // 1024x1024 native resolution for maximum crispness (downscaled by launcher)
  const png = await sharp(Buffer.from(svg), { density: 384 })
    .resize(1024, 1024)
    .png()
    .toBuffer();
  fs.writeFileSync(outPath, png);
  console.log(`Generated: ${outPath} (${png.length} bytes)`);
}

convert().catch(e => { console.error(e.message); process.exit(1); });