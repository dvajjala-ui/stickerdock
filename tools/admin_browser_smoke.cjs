/* Run against tools/admin_dev_server.py. Uses only fixture APIs, never production. */
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');

(async () => {
  const browser = await chromium.launch({channel: 'chrome', headless: true});
  try {
    const page = await browser.newPage({viewport: {width: 1280, height: 900}});
    await page.clock.install();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const snapshot = {
      packs: [], factory_packs: [], runs: [{day: '2026-10-15', generated: 10, target: 40,
        status: 'running', display_status: 'waiting', attempts: 3, retry_at: 1792098000,
        last_result: 'Provider rate limit reached. Progress saved; the next scheduled worker retries after cooldown.'}],
      requests: [], events: [], briefs: [],
      settings: {enabled: true, daily_target: 40, batch_size: 5, topics: [],
        content_categories: ['EVERYDAY'], rating: 'GENERAL'},
      catalog: {version: 1, packs: []}, catalog_base: 'https://example.invalid/catalog/'
    };
    let overviewRequests = 0, releaseBackground, backgroundStarted;
    let signedIn = false;
    await page.route('**/api/session', async route => {
      const payload = route.request().postDataJSON();
      if (payload.action === 'login') signedIn = true;
      await route.fulfill({json: {signed_in: signedIn}});
    });
    const backgroundObserved = new Promise(resolve => {backgroundStarted = resolve;});
    await page.route('**/api/admin', async route => {
      assert.equal(route.request().headers()['x-stickerdock-token'], undefined,
        'Browser session requests must not send a manually copied owner token');
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
    assert.equal(await page.locator('#preview-banner').isVisible(), false, 'Live sign-in must not show the retired preview-token banner');
    await page.locator('#connect-button').click();
    assert.equal(await page.locator('#owner-token').count(), 0, 'Owner login must use a normal session');
    await page.locator('#owner-email').fill('fixture-owner@example.invalid');
    await page.locator('#owner-password').fill('fixture-password-not-a-real-account');
    await page.locator('#connect-form button[type=submit]').click();
    await page.locator('#connect-dialog').waitFor({state: 'hidden'});
    assert.equal(await page.locator('#preview-banner').isVisible(), false, 'Signed-in owners must not see a sign-in banner');
    assert.equal(await page.locator('#owner-password').inputValue(), '');
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
    await page.locator('nav button[data-page=factory]').click();
    await page.getByText('Provider cooldown ends', {exact: false}).waitFor();
    assert.equal(await page.locator('.badge.waiting').count(), 1);
    await page.setViewportSize({width: 390, height: 844});
    await page.waitForFunction(() => document.querySelector('#sidebar').getBoundingClientRect().right <= 0);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false,
      'Factory recovery must fit a narrow phone viewport');
    if (process.env.ADMIN_SMOKE_SCREENSHOT) await page.screenshot({path: process.env.ADMIN_SMOKE_SCREENSHOT, fullPage: true});
    await page.locator('#menu-button').click();
    await page.locator('nav button[data-page=catalog]').click();
    await page.getByText('Users preview and save the packs they want.', {exact: false}).waitFor();
    assert.deepEqual(errors, []);
    console.log(JSON.stringify({pass: true, checks: [
      'navigation while a background read is pending', 'stale response does not reset edits',
      'settings form protected from polling', 'actions remain enabled', 'owner session login',
      'rate-limit waiting state and retry time', 'mobile layout', 'explicit catalog save wording', 'no page errors'
    ]}));
  } finally {
    await browser.close();
  }
})().catch(error => {console.error(error); process.exitCode = 1;});
