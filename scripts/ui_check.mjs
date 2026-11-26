// Headless UI check: load every page of the QTT UI, fail on console errors, page errors or failed requests.
//
//   NODE_PATH=<dir with puppeteer-core> node scripts/ui_check.mjs [baseUrl] [screenshotDir]
//
// Requires puppeteer-core and a local Chrome (CHROME_PATH, default: macOS Google Chrome).
// Expects a datasource and route to exist (it creates its own via the API and removes them afterwards).
import { createRequire } from 'module';
import fs from 'fs';

const require = createRequire(process.env.NODE_PATH ? process.env.NODE_PATH + '/' : import.meta.url);
const puppeteer = require('puppeteer-core');

const base = process.argv[2] || 'https://localhost:18443';
const shots = process.argv[3];
const chrome = process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const api = `${base}/queryrest/api`;
process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';

const suffix = Math.random().toString(16).slice(2, 8);
const ds = `ui-ds-${suffix}`;
const route = `ui-route-${suffix}`;

async function call(method, path, body, headers = {}) {
  const r = await fetch(api + path, { method, body, headers: { Accept: 'application/json', ...headers } });
  return r.status;
}

async function setup() {
  await call('POST', '/datasources', JSON.stringify({
    dataSourceId: ds, timeOutSeconds: '30', maxQueryHeaderLength: '8192', username: 'u', password: 'p',
    url: 'http://wiremock:8080', validateCertificate: false,
  }), { 'Content-Type': 'application/json' });
  await call('POST', `/datasources/${ds}/healthcheck`);
  const form = new FormData();
  form.set('freemarker', new Blob(['CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 5']));
  form.set('layers', new Blob(['http://cambridgesemantics.com/Layer/67c99b630d494b4da6dc5a0a11cb717e']));
  const q = new URLSearchParams({ routeParams: 'httpMethodRestrict=GET,POST', dataSourceId: ds, description: 'ui',
    graphMartUri: 'http://cambridgesemantics.com/Graphmart/93794a1ba2a24246a0f33c4340858d80' });
  const s = await call('POST', `/routes/${route}?${q}`, form);
  if (s !== 201) throw new Error(`route create failed: ${s}`);
}

async function teardown() {
  await call('DELETE', `/routes/${route}`);
  await call('DELETE', `/datasources/${ds}`);
}

const pages = [
  ['metrics', '#/metrics', 'Metrics'],
  ['datasources', '#/datasources', ds],
  ['config-datasource', `#/datasources/config-datasource/${ds}`, ds],
  ['add-datasource', '#/datasources/add-datasource', null],
  ['routes', '#/routes', route],
  ['config-route', `#/routes/config-route/${route}`, route],
  ['settings', '#/settings', null],
];

const problems = [];
await setup();
const browser = await puppeteer.launch({ executablePath: chrome, headless: true,
  args: ['--ignore-certificate-errors', '--no-sandbox'] });
try {
  for (const [name, hash, expectText] of pages) {
    const page = await browser.newPage();
    await page.setViewport({ width: 1440, height: 900 });
    const errs = [];
    page.on('console', m => { if (m.type() === 'error') errs.push(`console: ${m.text()}`); });
    page.on('pageerror', e => errs.push(`pageerror: ${e.message}`));
    page.on('requestfailed', r => errs.push(`requestfailed: ${r.url()} ${r.failure()?.errorText}`));
    page.on('response', r => { if (r.status() >= 400) errs.push(`HTTP ${r.status()}: ${r.url()}`); });
    await page.goto(`${base}/${hash}`, { waitUntil: 'networkidle0', timeout: 60000 });
    await new Promise(r => setTimeout(r, 1500));
    const text = await page.evaluate(() => document.body.innerText);
    if (expectText && !text.includes(expectText)) errs.push(`expected text not found: ${expectText}`);
    if (shots) {
      fs.mkdirSync(shots, { recursive: true });
      await page.screenshot({ path: `${shots}/${name}.png`, fullPage: true });
    }
    console.log(`${errs.length ? 'FAIL' : 'PASS'} ${name}${errs.length ? '\n    ' + errs.join('\n    ') : ''}`);
    problems.push(...errs.map(e => `${name}: ${e}`));
    await page.close();
  }
} finally {
  await browser.close();
  await teardown();
}
console.log(`ui check: ${pages.length - new Set(problems.map(p => p.split(':')[0])).size}/${pages.length} pages clean`);
process.exit(problems.length ? 1 : 0);
