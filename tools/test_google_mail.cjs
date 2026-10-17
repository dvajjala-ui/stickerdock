// Run the real Apps Script source with isolated Google-service fakes.
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('integrations/google-mail/Code.gs', 'utf8');
const secret = 'unit-test-mail-secret-with-32-characters';
const saved = {RELAY_SECRET: secret};
const sent = [];
let available = 100, busy = false, locked = false;
const properties = {
  getProperty: key => saved[key] || null,
  getProperties: () => ({...saved}),
  setProperty: (key, value) => { saved[key] = value; },
  deleteProperty: key => { delete saved[key]; },
};
const context = vm.createContext({console, Date, JSON, Number, Object, Error,
  PropertiesService: {getScriptProperties: () => properties},
  LockService: {getScriptLock: () => ({tryLock: () => {locked = !busy; return locked;}, hasLock: () => locked, releaseLock: () => {locked = false;}})},
  MailApp: {getRemainingDailyQuota: () => available, sendEmail: message => sent.push(message)},
  Utilities: {
    Charset: {UTF_8: 'UTF-8'},
    base64Encode: value => Buffer.from(value).toString('base64'),
    base64Decode: value => Buffer.from(value, 'base64'),
    computeHmacSha256Signature: (value, key) => crypto.createHmac('sha256', key).update(value).digest(),
    newBlob: value => ({getDataAsString: () => Buffer.from(value).toString('utf8')}),
  },
  ContentService: {MimeType: {JSON: 'application/json'}, createTextOutput: text => ({setMimeType: () => JSON.parse(text)})},
});
vm.runInContext(source, context);
const message = {to: 'owner@example.com', action: 'recovery', link: 'https://qqicsllktmcmvmzplsxl.supabase.co/auth/v1/verify?token=abc&type=recovery', code: '123456'};
function request(changes = {}, id = 'a'.repeat(64), age = 0) {
  const timestamp = Math.floor(Date.now()/1000) + age;
  const payload = Buffer.from(JSON.stringify({...message, ...changes})).toString('base64');
  const signature = crypto.createHmac('sha256', secret).update(`${timestamp}.${id}.${payload}`).digest('base64');
  return {postData: {contents: JSON.stringify({timestamp, id, payload, signature})}};
}
assert.equal(context.doPost(request()).ok, true);
assert.equal(sent.length, 1);
assert.match(sent[0].body, /Reset your StickerDock password/);
assert.equal(context.doPost(request()).duplicate, true);
assert.equal(sent.length, 1);
for (const invalid of [request({}, 'b'.repeat(64), -301), request({to: 'a@b.com,c@d.com'}), request({link: 'https://attacker.invalid'}), request({action: 'spam'}), {postData: {contents: '{}'}}]) {
  assert.equal(context.doPost(invalid).ok, false);
}
const tampered = request({}, 'c'.repeat(64));
tampered.postData.contents = tampered.postData.contents.replace('"signature":"', '"signature":"tampered');
assert.equal(context.doPost(tampered).ok, false);
available = 0;
assert.equal(context.doPost(request({}, 'd'.repeat(64))).ok, false);
available = 100;
busy = true;
assert.equal(context.doPost(request({}, 'e'.repeat(64))).ok, false);
busy = false;
saved.DAILY_BUDGET = JSON.stringify({day: new Date().toISOString().slice(0,10), count: 90});
assert.equal(context.doPost(request({}, 'f'.repeat(64))).ok, false);
assert.equal(sent.length, 1);
assert.equal(locked, false);
assert.ok(!JSON.stringify(saved).includes('owner@example.com'));
assert.ok(!JSON.stringify(saved).includes('token=abc'));
console.log('Google MailApp relay: signature, replay, quota, recipient/link validation and lock tests passed.');
