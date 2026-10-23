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
      packs: [{id: 'fixture-pack', name: 'Fixture reaction', creator: 'Tester', rating: 'GENERAL',
        sha256: 'a'.repeat(64),
        status: 'draft', version: 1, sticker_count: 1, references: [{media_credit: {license: 'CC0'}}]}],
      factory_packs: [], runs: [{day: '2026-10-15', generated: 10, target: 40,
        status: 'running', display_status: 'waiting', attempts: 3, retry_at: 1792098000,
        last_result: 'Provider rate limit reached. Progress saved; the next scheduled worker retries after cooldown.'}],
      requests: [], events: [], briefs: [],
      settings: {enabled: true, daily_target: 40, batch_size: 5, topics: [],
        content_categories: ['EVERYDAY'], rating: 'GENERAL'},
      catalog: {version: 1, packs: []}, catalog_base: 'https://example.invalid/catalog/'
    };
    let overviewRequests = 0, releaseBackground, backgroundStarted, uploaded = 0, captionEdit = null;
    const mediaItems = [];
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
      if (payload.action === 'source_upload') {
        assert.equal(payload.rights, 'USER_OWNED');
        assert.ok(payload.image.startsWith('data:image/png;base64,'));
        uploaded++;
        const path = 'inbox/' + 'b'.repeat(64) + '.png';
        mediaItems.push({path, name: 'My own meme.png', size: 256});
        return route.fulfill({json: {path}});
      }
      if (payload.action === 'pack_detail') return route.fulfill({json: {
        pack: snapshot.packs[0], offset: 0, preview_count: 5, categories: ['GENERAL'], target_packs: [],
        stickers: [{name: 'fixture.webp', kind: 'STATIC', label: 'Old caption', tags: '',
          caption_editable: true, preview: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg=='}]
      }});
      if (payload.action === 'pack_cover') return route.fulfill({json: {
        id: payload.id, sha256: snapshot.packs[0].sha256,
        preview: 'data:image/webp;base64,UklGRjwAAABXRUJQVlA4IDAAAADQAQCdASoBAAEAAUAmJaACdLoB+AADsAD+8ut//NgVzXPv9//S4P0uD9Lg/9KQAAA='
      }});
      if (payload.action === 'pack_save') {
        captionEdit = payload.stickers[0];
        return route.fulfill({json: {saved: true}});
      }
      assert.equal(payload.action, 'overview');
      overviewRequests++;
      if (overviewRequests === 2) {
        backgroundStarted();
        await new Promise(resolve => {releaseBackground = resolve;});
      }
      await route.fulfill({json: snapshot});
    });
    await page.route('**/api/library', async route => {
      const payload = route.request().postDataJSON();
      if (payload.action === 'list') return route.fulfill({json: {items: mediaItems, next_offset: null}});
      if (payload.action === 'preview') return route.fulfill({json: {
        path: payload.path, name: 'My own meme', purpose: 'SOURCE', license: 'USER_OWNED',
        preview: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg=='}});
      throw new Error('Unexpected Admin library action: ' + payload.action);
    });
    await page.goto(process.env.ADMIN_PREVIEW_URL || 'http://127.0.0.1:4173/admin');
    await page.getByRole('heading', {name: 'Your sticker factory, at a glance.', exact: true}).waitFor();
    await page.getByRole('img', {name: 'Fixture reaction cover'}).first().waitFor();
    await page.waitForFunction(() => document.querySelector('.pack-art img')?.naturalWidth > 0);
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
    await page.getByRole('button', {name: 'Open Fixture reaction'}).click();
    await page.getByRole('button', {name: 'Preview full-size artwork for Old caption'}).click();
    await page.locator('#sticker-preview-dialog[open] #sticker-preview-image').waitFor();
    if (process.env.ADMIN_ARTWORK_SCREENSHOT)
      await page.locator('#sticker-preview-dialog').screenshot({path: process.env.ADMIN_ARTWORK_SCREENSHOT});
    await page.getByRole('button', {name: 'Close artwork preview'}).click();
    await page.locator('[data-field=caption]').fill('Wait, what?');
    await page.getByRole('button', {name: 'Save changes'}).click();
    await page.waitForFunction(() => document.querySelector('#pack-dialog')?.open === false);
    assert.equal(captionEdit.caption, 'Wait, what?', 'Visible caption must reach the save API');
    await page.setViewportSize({width: 390, height: 844});
    await page.getByRole('button', {name: 'Open Fixture reaction'}).click();
    assert.equal(await page.locator('#pack-dialog').evaluate(dialog => dialog.scrollWidth > dialog.clientWidth), false,
      'Caption editor must fit a narrow phone viewport');
    if (process.env.ADMIN_CAPTION_SCREENSHOT)
      await page.locator('#pack-dialog').screenshot({path: process.env.ADMIN_CAPTION_SCREENSHOT});
    await page.locator('#pack-dialog [data-close]').click();
    await page.setViewportSize({width: 1280, height: 900});
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
    await page.setViewportSize({width: 1280, height: 900});
    await page.locator('nav button[data-page=media]').click();
    await page.getByRole('heading', {name: 'Media library', exact: true}).waitFor();
    await page.locator('#source-rights').selectOption('USER_OWNED');
    const png = await page.evaluate(() => {
      const canvas = document.createElement('canvas'); canvas.width = 2; canvas.height = 2;
      return canvas.toDataURL('image/png').split(',')[1];
    });
    await page.locator('#source-upload').setInputFiles({name: 'my-own-meme.png', mimeType: 'image/png', buffer: Buffer.from(png, 'base64')});
    await page.locator('.media-card').waitFor();
    assert.equal(uploaded, 1, 'A rights-confirmed local image should reach the workspace upload action');
    await page.getByText('My own meme', {exact: false}).first().waitFor();
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
        return route.fulfill({json: {items: [{path, name: 'Owned test source', size: 1024}], next_offset: null}});
      if (endpoint === '/api/library' && payload.action === 'preview')
        return route.fulfill({json: {path, name: 'Owned test source', purpose: 'SOURCE', license: 'USER_OWNED',
          preview: await studio.locator('#canvas').evaluate(canvas => canvas.toDataURL('image/png'))}});
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
    await studio.locator('#library-gallery .library-item').first().click();
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
      'rate-limit waiting state and retry time', 'mobile layout', 'explicit catalog save wording',
      'rights-confirmed Admin media upload', 'lazy real-art pack cover', 'full-size sticker review',
      'visible-caption edit sent to save API', 'narrow caption editor layout', 'no page errors'
    ]}));
  } finally {
    await browser.close();
  }
})().catch(error => {console.error(error); process.exitCode = 1;});
