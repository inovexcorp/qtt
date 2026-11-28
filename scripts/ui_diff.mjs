// Visual review: compare two sets of ui_check.mjs screenshots and write an HTML side-by-side with a diff overlay.
//
//   NODE_PATH=<dir with puppeteer-core> node scripts/ui_diff.mjs <baselineDir> <currentDir> <report.html>
//
// Compares every PNG present in either directory (by file name). The pixel comparison runs in headless Chrome
// (canvas), so the only dependency is puppeteer-core, as for ui_check.mjs. A pixel counts as changed when any channel
// differs by more than 32. Prints one line per screenshot with the changed share; always exits 0 (styling may change
// on purpose, so this is reviewed, not gated), except on usage errors.
import { createRequire } from 'module';
import fs from 'fs';
import path from 'path';

const require = createRequire(process.env.NODE_PATH ? process.env.NODE_PATH + '/' : import.meta.url);
const puppeteer = require('puppeteer-core');

const [baseDir, curDir, out] = process.argv.slice(2);
if (!baseDir || !curDir || !out) {
  console.error('usage: ui_diff.mjs <baselineDir> <currentDir> <report.html>');
  process.exit(2);
}
const chrome = process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const pngs = dir => fs.existsSync(dir) ? fs.readdirSync(dir).filter(f => f.endsWith('.png')) : [];
const names = [...new Set([...pngs(baseDir), ...pngs(curDir)])].sort();
const dataUrl = file => `data:image/png;base64,${fs.readFileSync(file).toString('base64')}`;

const browser = await puppeteer.launch({ executablePath: chrome, headless: true, args: ['--no-sandbox'] });
const rows = [];
try {
  const page = await browser.newPage();
  for (const name of names) {
    const a = path.join(baseDir, name), b = path.join(curDir, name);
    if (!fs.existsSync(a) || !fs.existsSync(b)) {
      rows.push({ name, status: fs.existsSync(a) ? 'missing now' : 'new', a: fs.existsSync(a) && dataUrl(a),
        b: fs.existsSync(b) && dataUrl(b) });
      continue;
    }
    const result = await page.evaluate(async (srcA, srcB) => {
      const load = src => new Promise((ok, fail) => { const i = new Image(); i.onload = () => ok(i); i.onerror = fail; i.src = src; });
      const [ia, ib] = await Promise.all([load(srcA), load(srcB)]);
      const w = Math.max(ia.width, ib.width), h = Math.max(ia.height, ib.height);
      const pixels = img => {
        const c = new OffscreenCanvas(w, h), x = c.getContext('2d');
        x.fillStyle = '#fff'; x.fillRect(0, 0, w, h); x.drawImage(img, 0, 0);
        return x.getImageData(0, 0, w, h).data;
      };
      const pa = pixels(ia), pb = pixels(ib);
      const c = new OffscreenCanvas(w, h), x = c.getContext('2d');
      const diff = x.createImageData(w, h);
      let changed = 0;
      for (let i = 0; i < pa.length; i += 4) {
        const d = Math.max(Math.abs(pa[i] - pb[i]), Math.abs(pa[i + 1] - pb[i + 1]), Math.abs(pa[i + 2] - pb[i + 2]));
        if (d > 32) {
          changed++;
          diff.data.set([255, 0, 80, 255], i);
        } else { // faded copy of the current screenshot, for orientation
          const g = 235 + (pb[i] + pb[i + 1] + pb[i + 2]) / 3 / 255 * 20;
          diff.data.set([g, g, g, 255], i);
        }
      }
      x.putImageData(diff, 0, 0);
      const blob = await c.convertToBlob({ type: 'image/png' });
      const buf = new Uint8Array(await blob.arrayBuffer());
      let bin = '';
      for (let i = 0; i < buf.length; i += 0x8000) bin += String.fromCharCode(...buf.subarray(i, i + 0x8000));
      return { changed, total: w * h, size: [ia.width, ia.height, ib.width, ib.height], diff: 'data:image/png;base64,' + btoa(bin) };
    }, dataUrl(a), dataUrl(b));
    const share = result.changed / result.total;
    rows.push({ name, status: share === 0 ? 'identical' : `${(share * 100).toFixed(2)}% changed`, share, a: dataUrl(a),
      b: dataUrl(b), diff: result.diff, size: result.size });
  }
} finally {
  await browser.close();
}

for (const r of rows) console.log(`${r.status.padEnd(16)} ${r.name}`);
const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const img = src => src ? `<a href="${src}" target="_blank"><img src="${src}"></a>` : '<div class="none">—</div>';
fs.mkdirSync(path.dirname(path.resolve(out)), { recursive: true });
fs.writeFileSync(out, `<!doctype html><meta charset="utf-8"><title>UI diff</title>
<style>
  body { font: 14px system-ui, sans-serif; margin: 16px; background: #fafafa; color: #222 }
  h2 { font-size: 15px; margin: 28px 0 6px } .meta { color: #666; font-weight: normal }
  .row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px }
  .row img { width: 100%; border: 1px solid #ddd; background: #fff } .none { padding: 40px; text-align: center; color: #999 }
  .label { font-size: 12px; color: #666; margin-bottom: 2px }
</style>
<h1>UI diff</h1>
<p>Baseline <code>${esc(baseDir)}</code> → current <code>${esc(curDir)}</code>. Red: pixels that changed.</p>
${rows.map(r => `<h2>${esc(r.name)} <span class="meta">${esc(r.status)}${r.size && (r.size[1] !== r.size[3])
    ? ` · height ${r.size[1]} → ${r.size[3]} px` : ''}</span></h2>
<div class="row"><div><div class="label">baseline</div>${img(r.a)}</div><div><div class="label">current</div>${img(r.b)}</div>
<div><div class="label">diff</div>${img(r.diff)}</div></div>`).join('\n')}
`);
console.log(`report: ${out}`);
