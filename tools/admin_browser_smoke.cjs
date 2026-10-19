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
    const sessionRequests = [];
    page.on('request', request => {if (request.url().endsWith('/api/session')) sessionRequests.push(request.url());});
    const backgroundObserved = new Promise(resolve => {backgroundStarted = resolve;});
    await page.route('**/api/admin', async route => {
      assert.equal(route.request().headers()['x-stickerdock-token'], undefined,
        'Workspace requests must not send an owner token');
      assert.equal(route.request().headers().cookie, undefined, 'Workspace APIs do not need cookies');
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
    await page.getByRole('heading', {name: 'Your sticker factory, at a glance.', exact: true}).waitFor();
    assert.equal(await page.locator('#connect-dialog, #owner-token, input[type=password]').count(), 0,
      'Dashboard must open directly without login controls');
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
    assert.deepEqual(sessionRequests, [], 'Dashboard must never call the removed session API');

    const retry = await browser.newPage();
    let attempts = 0;
    await retry.route('**/api/admin', async route => {
      if (route.request().postDataJSON().action === 'health') return route.fulfill({json: {admin_ready: true}});
      attempts++;
      return attempts === 1 ? route.fulfill({status: 503, json: {error: 'Temporary test outage'}})
        : route.fulfill({json: snapshot});
    });
    await retry.goto(process.env.ADMIN_PREVIEW_URL || 'http://127.0.0.1:4173/admin');
    await retry.getByRole('heading', {name: 'Workspace unavailable', exact: true}).waitFor();
    assert.equal(await retry.getByText('Total stickers', {exact: true}).count(), 0,
      'Failed startup must not render fabricated zero totals');
    await retry.getByRole('button', {name: 'Try again', exact: true}).click();
    await retry.getByRole('heading', {name: 'Your sticker factory, at a glance.', exact: true}).waitFor();
    await retry.close();

    const studio = await browser.newPage();
    const studioActions = [];
    studio.on('pageerror', error => errors.push(error.message));
    await studio.route('**/api/**', async route => {
      assert.equal(route.request().headers()['x-stickerdock-token'], undefined);
      assert.equal(route.request().headers().cookie, undefined);
      const payload = route.request().postDataJSON();
      const endpoint = new URL(route.request().url()).pathname;
      studioActions.push(endpoint + ':' + payload.action);
      const path = 'inbox/' + 'a'.repeat(64) + '.png';
      if (endpoint === '/api/library' && payload.action === 'list')
        return route.fulfill({json: {items: [{path, name: 'Owned test source', size: 1024}]}});
      if (endpoint === '/api/library' && payload.action === 'fetch')
        return route.fulfill({json: {image: await studio.locator('#canvas').evaluate(canvas => canvas.toDataURL('image/png'))}});
      if (endpoint === '/api/captions' && payload.action === 'queue')
        return route.fulfill({status: 202, json: {id: 'fixture-job', status: 'queued'}});
      if (endpoint === '/api/captions' && payload.action === 'status')
        return route.fulfill({json: {status: 'complete', result_path: path}});
      throw new Error('Unexpected Studio request: ' + endpoint);
    });
    await studio.goto(new URL('/studio/index.html', process.env.ADMIN_PREVIEW_URL || 'http://127.0.0.1:4173').href);
    await studio.locator('#refresh').click();
    await studio.locator('#library').selectOption('inbox/' + 'a'.repeat(64) + '.png');
    await studio.locator('#load-library').click();
    await studio.locator('#prompt').fill('An original reaction');
    await studio.waitForFunction(() => !document.querySelector('#generate').disabled);
    await studio.locator('#generate').click();
    await studio.waitForFunction(() => document.querySelector('#status').textContent.includes('AI sticker ready'));
    assert.ok(studioActions.includes('/api/captions:queue'));
    assert.ok(studioActions.includes('/api/captions:status'));
    assert.equal(studioActions.some(action => action.startsWith('/api/session:')), false);
    assert.deepEqual(errors, []);
    await studio.close();
    console.log(JSON.stringify({pass: true, checks: [
      'navigation while a background read is pending', 'stale response does not reset edits',
      'settings form protected from polling', 'actions remain enabled', 'direct dashboard without login/session API',
      'startup outage and retry without fake data',
      'Studio source loading and fixture AI queue/result without authentication',
      'rate-limit waiting state and retry time', 'mobile layout', 'explicit catalog save wording', 'no page errors'
    ]}));
  } finally {
    await browser.close();
  }
})().catch(error => {console.error(error); process.exitCode = 1;});
