"""Export the existing Android vector brand mark for Apple's app-icon catalog.

Build-time only: pip install -r tools/requirements-apple-assets.txt
No new artwork, external image/model request or app runtime dependency.
"""
from pathlib import Path
import json
import io
import xml.etree.ElementTree as ET

import resvg_py
from PIL import Image


def export(root):
    android = '{http://schemas.android.com/apk/res/android}'
    vector = ET.parse(root / 'app/src/main/res/drawable/ic_launcher_foreground.xml').getroot()
    elements = ['<rect width="108" height="108" fill="#12130F"/>']
    for path in vector.findall('path'):
        attributes = {'d': path.get(android + 'pathData'), 'fill': path.get(android + 'fillColor', 'none')}
        if attributes['fill'] == '@android:color/transparent': attributes['fill'] = 'none'
        for source, target in [('strokeColor', 'stroke'), ('strokeWidth', 'stroke-width'), ('strokeLineCap', 'stroke-linecap')]:
            if path.get(android + source): attributes[target] = path.get(android + source)
        elements.append(ET.tostring(ET.Element('path', attributes), encoding='unicode'))
    svg = '<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 108 108">' + ''.join(elements) + '</svg>'
    assets = root / 'ios/Resources/Assets.xcassets'
    icon = assets / 'AppIcon.appiconset'; icon.mkdir(parents=True, exist_ok=True)
    (assets / 'Contents.json').write_text(json.dumps({'info': {'author': 'xcode', 'version': 1}}, indent=2) + '\n')
    Image.open(io.BytesIO(resvg_py.svg_to_bytes(svg_string=svg))).convert('RGB').save(icon / 'AppIcon.png')
    (icon / 'Contents.json').write_text(json.dumps({'images': [{'filename': 'AppIcon.png', 'idiom': 'universal', 'platform': 'ios', 'size': '1024x1024'}], 'info': {'author': 'xcode', 'version': 1}}, indent=2) + '\n')
    print(icon / 'AppIcon.png')


if __name__ == '__main__':
    export(Path(__file__).resolve().parent.parent)
