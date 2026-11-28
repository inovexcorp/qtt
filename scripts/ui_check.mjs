// Headless UI check: load every page of the QTT UI, fail on console errors, page errors or failed requests.
//
//   NODE_PATH=<dir with puppeteer-core> node scripts/ui_check.mjs [baseUrl] [screenshotDir]
//
// Requires puppeteer-core and a local Chrome (CHROME_PATH, default: macOS Google Chrome).
// Expects a datasource and route to exist (it creates its own via the API and removes them afterwards).
//
// It also checks query access end to end: a route's endpoint panel and access setting, the access marker in the routes
// list, and an API key created, used against /query and revoked on the Settings page (Query access section) (QTT_QUERY_URL, default
// http://localhost:18082/query).
//
// QTT_OIDC=true: the stack requires sign-in (scripts/stack.sh with QTT_OIDC=true). Pages are walked signed in as
// "admin" through the Keycloak login form; then a viewer's controls, the no-access page, signing in again after the
// session ends (back to the same page) and signing out are checked.
import { createRequire } from 'module';
import fs from 'fs';

const require = createRequire(process.env.NODE_PATH ? process.env.NODE_PATH + '/' : import.meta.url);
const puppeteer = require('puppeteer-core');

const base = process.argv[2] || 'https://localhost:18443';
const shots = process.argv[3];
const chrome = process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const api = `${base}/queryrest/api`;
process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';
const oidc = process.env.QTT_OIDC === 'true';
const realm = process.env.QTT_OIDC_REALM || 'http://localhost:18180/realms/qtt';

async function serviceToken() {
  const r = await fetch(`${realm}/protocol/openid-connect/token`, { method: 'POST',
    body: new URLSearchParams({ grant_type: 'client_credentials', client_id: 'qtt-ci', client_secret: 'qtt-ci-test-secret' }) });
  if (r.status !== 200) throw new Error(`token request failed: ${r.status}`);
  return (await r.json()).access_token;
}
const auth = oidc ? { Authorization: `Bearer ${await serviceToken()}` } : {};

/** Signs in through the Keycloak form in this page's browser context; ends on the QTT page that was asked for. */
async function signIn(page, user, hash = '#/metrics') {
  await page.goto(`${base}/${hash}`, { waitUntil: 'networkidle0', timeout: 60000 });
  if (!page.url().startsWith(realm)) throw new Error(`expected the Keycloak login page, got ${page.url()}`);
  await page.type('#username', user);
  await page.type('#password', `${user}-pw`);
  await Promise.all([page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 60000 }), page.click('#kc-login')]);
  if (!page.url().startsWith(base)) throw new Error(`sign-in as ${user} did not return to QTT: ${page.url()}`);
}

const visible = (page, text) => page.evaluate(t => [...document.querySelectorAll('button, a')]
  .some(b => b.offsetParent !== null && b.innerText.replace(/\s+/g, ' ').includes(t)), text);

const suffix = Math.random().toString(16).slice(2, 8);
const ds = `ui-ds-${suffix}`;
const route = `ui-route-${suffix}`;

async function call(method, path, body, headers = {}) {
  const r = await fetch(api + path, { method, body, headers: { Accept: 'application/json', ...auth, ...headers } });
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
  if (oidc) {
    const page = await browser.newPage();
    await signIn(page, 'admin');
    await page.close();
  }
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

  // Template editors: a syntax error is marked while typing (before saving), and the mark clears once fixed.
  // add-route uses ngx-monaco-editor's own Monaco instance, config-route the bundled one; check both.
  // [name, page, buttons/tabs to click (in order) to reveal the editor]
  const editors = [
    ['config-route editor', `#/routes/config-route/${route}`, ['tab:Template Editor']],
    ['add-route editor', '#/routes', ['.add-button', '.edit-btn']],
  ];
  const selectAll = process.platform === 'darwin' ? 'Meta' : 'Control';
  for (const [name, hash, openSteps] of editors) {
    const page = await browser.newPage();
    await page.setViewport({ width: 1440, height: 900 });
    const errs = [];
    page.on('pageerror', e => errs.push(`pageerror: ${e.message}`));
    try {
      await page.goto(`${base}/${hash}`, { waitUntil: 'networkidle0', timeout: 60000 });
      for (const step of openSteps) {
        if (step.startsWith('tab:')) {
          const label = step.slice(4);
          await page.evaluate(l => [...document.querySelectorAll('[role="tab"]')]
            .find(t => t.innerText.trim() === l)?.click(), label);
        } else {
          await page.waitForSelector(step, { visible: true, timeout: 10000 });
          // Dialogs scroll inside themselves (the add-route form is taller than the viewport with caching on).
          await page.$eval(step, el => el.scrollIntoView({ block: 'center' }));
          await page.click(step);
        }
        await new Promise(r => setTimeout(r, 800));
      }
      await page.waitForSelector('.monaco-editor .view-lines', { visible: true, timeout: 15000 });
      const replaceText = async text => {
        await page.click('.monaco-editor .view-lines');
        await page.keyboard.down(selectAll);
        await page.keyboard.press('KeyA');
        await page.keyboard.up(selectAll);
        await page.keyboard.press('Backspace');
        await page.keyboard.type(text, { delay: 5 });
      };
      await replaceText('SELECT * WHERE { ?s ?p ?o }\n<#if x>');
      await page.waitForSelector('.template-syntax-error', { timeout: 10000 });
      await page.waitForSelector('.monaco-editor .squiggly-error', { timeout: 10000 });
      const banner = await page.$eval('.template-syntax-error', el => el.innerText);
      if (!banner.includes('Line 2')) errs.push(`unexpected error banner: ${banner}`);
      await replaceText('SELECT * WHERE { ?s ?p ?o }');
      await page.waitForSelector('.template-syntax-error', { hidden: true, timeout: 10000 });
      await page.waitForSelector('.monaco-editor .squiggly-error', { hidden: true, timeout: 10000 });
    } catch (e) {
      errs.push(`editor check: ${e.message}`);
      if (shots) await page.screenshot({ path: `${shots}/${name.replace(/[^a-z0-9]+/gi, '-')}-failed.png` }).catch(() => {});
    }
    console.log(`${errs.length ? 'FAIL' : 'PASS'} ${name}${errs.length ? '\n    ' + errs.join('\n    ') : ''}`);
    problems.push(...errs.map(e => `${name}: ${e}`));
    pages.push([name]);
    await page.close();
  }

  // Query access through the UI (signed in as admin on OIDC stacks).
  {
    const name = 'query access';
    const queryBase = process.env.QTT_QUERY_URL || 'http://localhost:18082/query';
    const page = await browser.newPage();
    await page.setViewport({ width: 1440, height: 900 });
    page.on('dialog', d => d.accept()); // the revoke confirmation
    const errs = [];
    page.on('pageerror', e => errs.push(`pageerror: ${e.message}`));
    // A route change is a delete and re-create, so the route can briefly be missing (404) while it saves.
    const routeAccess = async () => {
      const r = await fetch(`${api}/routes/${route}`, { headers: { Accept: 'application/json', ...auth } });
      return r.status === 200 ? (await r.json()).accessPolicy : undefined;
    };
    // The app scrolls inside its own container, so bring elements into view before using them.
    const reveal = sel => page.$eval(sel, el => el.scrollIntoView({ block: 'center' }));
    const waitFor = async (fn, what) => {
      for (let i = 0; i < 40; i++) { if (await fn()) return; await new Promise(r => setTimeout(r, 250)); }
      errs.push(`timed out waiting for ${what}`);
    };
    let step = 'open the route page';
    try {
      await page.goto(`${base}/#/routes/config-route/${route}`, { waitUntil: 'networkidle0', timeout: 60000 });
      await new Promise(r => setTimeout(r, 1500));
      let text = await page.evaluate(() => document.body.innerText);
      if (!text.includes(`/query/${route}`)) errs.push('endpoint URL not shown on the route page');
      if (!text.includes('Who can call this route')) errs.push('access setting not shown on the route page');
      if ((await fetch(`${queryBase}/${route}`)).status !== 200) errs.push('route not open before the change');

      // Close the route through the UI.
      step = 'open the access select';
      await page.click('.route-access mat-select');
      await page.waitForSelector('mat-option', { visible: true, timeout: 5000 });
      await page.evaluate(() => [...document.querySelectorAll('mat-option')]
        .find(o => o.innerText.trim().startsWith('Authenticated'))?.click());
      await new Promise(r => setTimeout(r, 500));
      text = await page.evaluate(() => document.body.innerText);
      if (!text.includes('Authorization: Bearer $QTT_TOKEN')) errs.push('curl example has no token header');
      step = 'save the route';
      await page.click('#config-route-btn');
      await waitFor(async () => (await routeAccess()) === 'authenticated', 'the route to be saved as authenticated');
      const anonymous = (await fetch(`${queryBase}/${route}`)).status;
      if (anonymous !== 401) errs.push(`anonymous call to the closed route got ${anonymous}, expected 401`);

      step = 'check the routes list';
      // The routes list marks it.
      await page.goto(`${base}/#/routes`, { waitUntil: 'networkidle0', timeout: 60000 });
      await new Promise(r => setTimeout(r, 1000));
      const marked = await page.evaluate(r => [...document.querySelectorAll('tr')]
        .some(tr => tr.innerText.includes(r) && tr.querySelector('[aria-label="Authenticated route"]')), route);
      if (!marked) errs.push('routes list does not mark the route as authenticated');

      step = 'create an API key';
      // Create an API key for it on the Settings page, call the route with it, then revoke it.
      await page.goto(`${base}/#/settings?section=access`, { waitUntil: 'networkidle0', timeout: 60000 });
      await page.waitForSelector('app-api-keys .new-key-button', { visible: true, timeout: 10000 });
      await reveal('app-api-keys .new-key-button');
      await page.click('app-api-keys .new-key-button');
      await page.waitForSelector('app-api-keys input[formcontrolname="name"]', { visible: true, timeout: 10000 });
      await reveal('app-api-keys input[formcontrolname="name"]');
      await page.type('app-api-keys input[formcontrolname="name"]', `ui key ${suffix}`);
      await reveal('app-api-keys input[formcontrolname="routes"]');
      await page.type('app-api-keys input[formcontrolname="routes"]', route);
      await reveal('app-api-keys .create-form button[type="submit"]');
      await page.click('app-api-keys .create-form button[type="submit"]');
      await page.waitForSelector('.created-key-value', { visible: true, timeout: 10000 });
      const key = await page.$eval('.created-key-value', el => el.innerText.trim());
      if (!key.startsWith('qtt_')) errs.push(`created key looks wrong: ${key.slice(0, 8)}…`);
      const withKey = (await fetch(`${queryBase}/${route}`, { headers: { Authorization: `Bearer ${key}` } })).status;
      if (withKey !== 200) errs.push(`call with the new API key got ${withKey}, expected 200`);
      step = 'dismiss the new key';
      await reveal('.created-key button[mat-stroked-button]');
      await page.click('.created-key button[mat-stroked-button]');
      await page.waitForSelector(`button[aria-label="Revoke ui key ${suffix}"]`, { visible: true, timeout: 10000 });
      step = 'revoke the key';
      await reveal(`button[aria-label="Revoke ui key ${suffix}"]`);
      await page.click(`button[aria-label="Revoke ui key ${suffix}"]`);
      await waitFor(async () => (await fetch(`${queryBase}/${route}`,
        { headers: { Authorization: `Bearer ${key}` } })).status === 401, 'the revoked key to be refused');
      if (shots) await page.screenshot({ path: `${shots}/query-access.png`, fullPage: true });
    } catch (e) {
      errs.push(`query access check (${step}): ${e.message}`);
      if (shots) await page.screenshot({ path: `${shots}/query-access-failed.png`, fullPage: true }).catch(() => {});
    }
    console.log(`${errs.length ? 'FAIL' : 'PASS'} ${name}${errs.length ? '\n    ' + errs.join('\n    ') : ''}`);
    problems.push(...errs.map(e => `${name}: ${e}`));
    pages.push([name]);
    await page.close();
  }

  if (oidc) {
    // [name, check(page) -> list of problems], each in a fresh browser context (its own cookies)
    const checks = [
      ['oidc viewer sees no write controls', async page => {
        const errs = [];
        await signIn(page, 'viewer', '#/routes');
        const menu = await page.$eval('.user-button', b => b.innerText);
        if (!menu.includes('Vera Viewer')) errs.push(`user menu shows "${menu}"`);
        if (await visible(page, 'Add Route')) errs.push('"Add Route" shown to a viewer');
        const forbidden = [];
        page.on('response', r => { if (r.status() === 403) forbidden.push(r.url()); });
        await page.goto(`${base}/#/routes/config-route/${route}`, { waitUntil: 'networkidle0', timeout: 60000 });
        await new Promise(r => setTimeout(r, 1500));
        if (!(await page.evaluate(() => document.body.innerText)).includes(route)) errs.push('route page did not load');
        if (await visible(page, 'Save')) errs.push('"Save" shown to a viewer');
        await page.goto(`${base}/#/datasources`, { waitUntil: 'networkidle0', timeout: 60000 });
        if (await visible(page, 'Add DataSource')) errs.push('"Add DataSource" shown to a viewer');
        if (forbidden.length) errs.push(`opening pages as a viewer got 403 from: ${forbidden.join(', ')}`);
        return errs;
      }],
      ['oidc user without a role sees "no access"', async page => {
        await signIn(page, 'norole');
        await new Promise(r => setTimeout(r, 1000));
        const text = await page.evaluate(() => document.body.innerText);
        return text.includes('No access to QTT') ? [] : ['no "No access" page'];
      }],
      ['oidc ended session signs in again, back on the same page', async page => {
        await signIn(page, 'admin', '#/routes');
        const client = await page.createCDPSession();
        const { cookies } = await client.send('Network.getCookies', { urls: [base] });
        const session = cookies.filter(c => c.name.startsWith('q_session'));
        if (!session.length) return ['no q_session cookie after signing in'];
        for (const c of session) await page.deleteCookie({ name: c.name, url: base });
        // The next API call gets 499; the UI reloads, Keycloak's own session signs the user straight back in.
        await Promise.all([page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 60000 }),
          page.evaluate(() => { window.location.hash = '#/datasources'; })]);
        await page.waitForFunction(d => document.body.innerText.includes(d), { timeout: 20000 }, ds).catch(() => {});
        const errs = [];
        if (!page.url().startsWith(base) || !page.url().endsWith('#/datasources')) errs.push(`ended on ${page.url()}`);
        if (!(await page.evaluate(d => document.body.innerText.includes(d), ds))) errs.push('datasources not shown');
        return errs;
      }],
      ['oidc sign out', async page => {
        await signIn(page, 'editor');
        await page.click('.user-button');
        await page.waitForSelector('a[href="/logout"]', { visible: true, timeout: 5000 });
        await new Promise(r => setTimeout(r, 500)); // let the menu finish opening
        await Promise.all([page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 60000 }),
          page.click('a[href="/logout"]')]);
        // Keycloak shows its login page again once QTT's post-logout redirect asks for a new session.
        return page.url().startsWith(realm) ? [] : [`after signing out, ended on ${page.url()}`];
      }],
    ];
    for (const [name, check] of checks) {
      const context = await browser.createBrowserContext();
      const page = await context.newPage();
      await page.setViewport({ width: 1440, height: 900 });
      const pageErrors = [];
      page.on('pageerror', e => pageErrors.push(`pageerror: ${e.message}`));
      let errs;
      try {
        errs = [...await check(page), ...pageErrors];
      } catch (e) {
        errs = [e.message, ...pageErrors];
      }
      if (shots) await page.screenshot({ path: `${shots}/${name.replace(/[^a-z0-9]+/gi, '-')}.png` }).catch(() => {});
      console.log(`${errs.length ? 'FAIL' : 'PASS'} ${name}${errs.length ? '\n    ' + errs.join('\n    ') : ''}`);
      problems.push(...errs.map(e => `${name}: ${e}`));
      pages.push([name]);
      await context.close();
    }
  }
} finally {
  await browser.close();
  await teardown();
}
console.log(`ui check: ${pages.length - new Set(problems.map(p => p.split(':')[0])).size}/${pages.length} pages clean`);
process.exit(problems.length ? 1 : 0);
