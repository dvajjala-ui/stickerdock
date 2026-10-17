/** StickerDock account mail. Deploy as the owner, accessible to Anyone.
 * Set RELAY_SECRET (32+ random characters) in Project settings > Script properties.
 * Only the server knows this secret. Never paste it into this source file.
 */
function authorizeMail() {
  console.log('Remaining daily recipients: ' + MailApp.getRemainingDailyQuota());
}

function doGet() {
  return json_({service: 'StickerDock account mail', ok: true});
}

function doPost(e) {
  var lock = LockService.getScriptLock();
  try {
    if (!e || !e.postData || e.postData.contents.length > 20000) throw Error('request');
    var envelope = JSON.parse(e.postData.contents);
    var secret = PropertiesService.getScriptProperties().getProperty('RELAY_SECRET');
    if (!secret || secret.length < 32) throw Error('configuration');
    if (!Number.isInteger(envelope.timestamp) || Math.abs(Date.now()/1000 - envelope.timestamp) > 300) throw Error('expired');
    if (typeof envelope.id !== 'string' || !/^[a-f0-9]{64}$/.test(envelope.id)) throw Error('id');
    if (typeof envelope.payload !== 'string' || envelope.payload.length > 12000) throw Error('payload');
    var signed = envelope.timestamp + '.' + envelope.id + '.' + envelope.payload;
    var expected = Utilities.base64Encode(Utilities.computeHmacSha256Signature(signed, secret, Utilities.Charset.UTF_8));
    if (!equal_(expected, envelope.signature)) throw Error('signature');
    var payload = JSON.parse(Utilities.newBlob(Utilities.base64Decode(envelope.payload)).getDataAsString('UTF-8'));
    var batch = Array.isArray(payload) ? payload : [payload];
    if (batch.length < 1 || batch.length > 2) throw Error('batch');
    // Validate the entire request before sending either email.
    var mails = batch.map(mail_);
    if (!lock.tryLock(1000)) throw Error('busy');
    var properties = PropertiesService.getScriptProperties();
    // Persist only IDs/times: never store recipients, reset links or codes.
    var now = Math.floor(Date.now()/1000);
    var saved = properties.getProperties();
    Object.keys(saved).forEach(function(key) {
      if (key.indexOf('SENT_') === 0 && Number(saved[key]) < now - 86400) properties.deleteProperty(key);
    });
    var pending = mails.map(function(mail, index) {
      return {mail: mail, key: 'SENT_' + envelope.id + (mails.length === 1 ? '' : '_' + index)};
    }).filter(function(item) { return !saved[item.key]; });
    if (!pending.length) return json_({ok: true, duplicate: true});
    var day = new Date().toISOString().slice(0,10);
    var budget = JSON.parse(properties.getProperty('DAILY_BUDGET') || '{}');
    if (budget.day !== day) budget = {day: day, count: 0};
    // Consumer Apps Script has a small recipient allowance; keep headroom.
    if (budget.count + pending.length > 90 || MailApp.getRemainingDailyQuota() < pending.length) throw Error('quota');
    pending.forEach(function(item) {
      MailApp.sendEmail(item.mail);
      budget.count++;
      // Persist after each recipient so a retry can resume a partial batch.
      properties.setProperty(item.key, String(now));
      properties.setProperty('DAILY_BUDGET', JSON.stringify(budget));
    });
    return json_({ok: true});
  } catch (error) {
    // Apps Script ContentService cannot set HTTP error status. The adapter
    // MUST inspect ok; never log the request or Google's error details.
    return json_({ok: false, error: 'Mail delivery unavailable'});
  } finally {
    if (lock.hasLock()) lock.releaseLock();
  }
}

function mail_(message) {
  if (!message || typeof message.to !== 'string' || message.to.length > 254 || !/^[^\s@,;<>]+@[^\s@,;<>]+\.[^\s@,;<>]+$/.test(message.to)) throw Error('recipient');
  var titles = {signup: 'Confirm your StickerDock email', recovery: 'Reset your StickerDock password', magiclink: 'Sign in to StickerDock', invite: 'Your StickerDock invitation', email_change: 'Confirm your StickerDock email change', reauthentication: 'Confirm your StickerDock sign-in'};
  var subject = Object.prototype.hasOwnProperty.call(titles, message.action) ? titles[message.action] : '';
  if (!subject) throw Error('action');
  if (message.action !== 'reauthentication' && (typeof message.link !== 'string' || !/^https:\/\/qqicsllktmcmvmzplsxl\.supabase\.co\/auth\/v1\/verify\?[^\s<>"']+$/.test(message.link))) throw Error('link');
  if (message.code && !/^\d{6,10}$/.test(message.code)) throw Error('code');
  if (message.action === 'reauthentication' && !message.code) throw Error('code');
  var body = subject;
  if (message.action !== 'reauthentication') body += '\n\nOpen this link:\n' + message.link;
  if (message.code) body += '\n\nVerification code: ' + message.code;
  body += '\n\nIf you did not request this, ignore this email. Never share this link or code.\nStickerDock';
  return {to: message.to, subject: subject, body: body, name: 'StickerDock'};
}

function equal_(left, right) {
  if (typeof right !== 'string' || left.length !== right.length) return false;
  var difference = 0;
  for (var i = 0; i < left.length; i++) difference |= left.charCodeAt(i) ^ right.charCodeAt(i);
  return difference === 0;
}

function json_(value) {
  return ContentService.createTextOutput(JSON.stringify(value)).setMimeType(ContentService.MimeType.JSON);
}
