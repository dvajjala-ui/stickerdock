/* Run against tools/admin_dev_server.py. Uses only fixture APIs, never production. */
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');

(async () => {
  const browser = await chromium.launch({channel: 'chrome', headless: true});
  try {
    const page = await browser.newPage();
    await page.clock.install();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const snapshot = {
      packs: [], factory_packs: [], runs: [], requests: [], events: [], briefs: [],
      settings: {enabled: true, daily_target: 40, batch_size: 5, topics: [],
        content_categories: ['EVERYDAY'], rating: 'GENERAL'},
      catalog: {version: 1, packs: []}, catalog_base: 'https://example.invalid/catalog/'
    };
    let overviewRequests = 0, releaseBackground, backgroundStarted;
    const backgroundObserved = new Promise(resolve => {backgroundStarted = resolve;});
    await page.route('**/api/admin', async route => {
      const payload = route.request().postDataJSON();
      if (payload.action === 'health') {
        return route.fulfill({json: {admin_ready: true, worker_ready: true, checks: []}});
      }
      assert.equal(payload.action, 'overview');
      overviewRequests++;
      if (overviewRequests === 2) {
        backgroundStarted();
        await new Promise(resolve => {releaseBackground = resolve;});
      }
      await route.fulfill({json: snapshot});
    });
    await page.goto(process.env.ADMIN_PREVIEW_URL || 'http://127.0.0.1:4173/admin');
    await page.locator('#connect-button').click();
    await page.locator('#owner-token').fill('fixture-owner-token-never-used-in-production');
    await page.locator('#connect-form button[type=submit]').click();
    await page.locator('#connect-dialog').waitFor({state: 'hidden'});
    await page.clock.runFor(30000);
    await backgroundObserved;
    await page.waitForFunction(() => document.querySelector('#mode-pill').textContent.includes('Live'));
    assert.equal(overviewRequests, 2, 'Background poll must be in flight');
    await page.locator('nav button[data-page=settings]').click();
    await page.getByRole('heading', {name: 'Factory settings', exact: true}).waitFor();
    await page.locator('textarea[name=topics]').fill('EVERYDAY | Keep this unsaved topic');
    const completed = page.waitForResponse(response => response.url().endsWith('/api/admin'));
    releaseBackground();
    await completed;
    await page.clock.runFor(30000);
    assert.equal(await page.locator('textarea[name=topics]').inputValue(),
      'EVERYDAY | Keep this unsaved topic', 'A stale poll must not reset the form');
    assert.equal(overviewRequests, 2, 'Automatic refresh must leave settings forms alone');
    await page.locator('nav button[data-page=overview]').click();
    await page.getByRole('heading', {name: 'Your sticker factory, at a glance.', exact: true}).waitFor();
    assert.equal(await page.getByRole('button', {name: '＋ Create pack', exact: true}).isEnabled(), true);
    assert.deepEqual(errors, []);
    console.log(JSON.stringify({pass: true, checks: [
      'navigation while a background read is pending', 'stale response does not reset edits',
      'settings form protected from polling', 'actions remain enabled', 'no page errors'
    ]}));
  } finally {
    await browser.close();
  }
})().catch(error => {console.error(error); process.exitCode = 1;});
