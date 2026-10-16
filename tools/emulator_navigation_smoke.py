"""Verify content-first navigation on a rooted emulator without clearing data.

Uses the existing licensed local reaction fixtures. Does not publish, sign in,
access a phone or claim WhatsApp device acceptance. Explicitly saves one new
animated sticker to the existing QA pack after preview and checks old rows.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

from emulator_clip_creator_smoke import ClipCreatorSmoke


def run(adb, serial, output):
    test = ClipCreatorSmoke(adb, serial, output)
    installed = test.adb('shell', 'pm', 'path', 'app.stickerdock.duo').strip().removeprefix('package:')
    test.adb('pull', installed, str(output / 'installed.apk'))
    digest = hashlib.sha256((output / 'installed.apk').read_bytes()).hexdigest()
    before = test.counts()
    old = test.adb('shell', 'sqlite3 /data/data/app.stickerdock.duo/databases/stickerdock.db "SELECT id,pack_id,file_name,sha256 FROM stickers ORDER BY id;"').splitlines()
    test.launch(restart=True)
    test.wait_text('Search library')
    test.tap('Create')
    test.wait_text('Make a reaction worth sending')
    test.wait_text('Make an animated reaction')
    test.screenshot('01-create-411dp')
    test.tap('Import and backups')
    test.wait_text('Bring your stickers together')
    test.tap('Back')
    test.wait_text('Make a reaction worth sending')
    test.tap('Import and backups')
    test.wait_text('Bring your stickers together')
    test.adb('shell', 'input', 'keyevent', '4')
    test.wait_text('Make a reaction worth sending')
    assert test.counts() == before, 'Navigation created or removed stickers'
    test.tap('Library')
    test.wait_text('Search library')
    test.scroll_to_text('Group chat energy · Static')
    test.tap('Group chat energy · Static')
    test.wait_text('Pack options')
    test.wait_text('Group chat is on fire')
    test.screenshot('02-real-stickers-first-viewport')
    test.tap('Use in WhatsApp')
    test.wait_text('WhatsApp not installed')
    test.wait_text('Business not installed')
    assert test.action_node('WhatsApp not installed').get('enabled') == 'false'
    assert test.action_node('Business not installed').get('enabled') == 'false'
    test.screenshot('03-whatsapp-target-sheet')
    test.tap('Done')
    test.wait_text('Pack options')
    assert test.counts() == before
    for density, scale, name in [(480, '1.0', '04-pack-360dp'), (420, '1.3', '05-pack-large-text')]:
        test.adb('shell', 'wm', 'density', str(density))
        test.adb('shell', 'settings', 'put', 'system', 'font_scale', scale)
        test.wait_text('Pack options')
        test.wait_text('Use in WhatsApp')
        test.wait_text('Group chat is on fire')
        test.screenshot(name)
    test.adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    test.adb('shell', 'wm', 'density', '420')
    test.open_clip('facepalm-native.mp4', from_create=True)
    caption = 'Navigation recovery reaction'
    test.preview(caption, '06-create-motion')
    test.save()
    result = test.saved(caption, 'created-reaction.webp')
    assert test.counts() == (before[0], before[1] + 1)
    test.launch(restart=True)
    test.scroll_to_text(test.PACK)
    test.tap(test.PACK)
    test.wait_text('Pack options')
    test.screenshot('07-saved-after-restart')
    new = test.adb('shell', 'sqlite3 /data/data/app.stickerdock.duo/databases/stickerdock.db "SELECT id,pack_id,file_name,sha256 FROM stickers ORDER BY id;"').splitlines()
    assert set(old) <= set(new), 'Prior stickers changed during navigation or save'
    crashes = test.adb('logcat', '-b', 'crash', '-d')
    assert 'FATAL EXCEPTION' not in crashes, 'Android crash buffer contains an exception'
    report = dict(passed=True, version='0.10.0', apk_sha256=digest, before=before, after=test.counts(), result=result,
                  checks=['Create tab and visible media actions', 'toolbar and system Back restore Create from import tools', 'real sticker artwork in initial viewport', 'explicit WhatsApp target sheet with disabled missing applications', '360dp and 1.3x text screenshots', 'moving MP4 preview leaves library unchanged', 'explicit existing-pack save', 'saved bytes/motion/alpha/duration', 'all prior rows preserved after restart'],
                  limitations=['AOSP Android 15 emulator only', 'no physical WhatsApp/Business or iPhone device acceptance'])
    (output / 'report.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    run(args.adb, args.serial, args.output)
