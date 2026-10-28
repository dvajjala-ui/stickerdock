"""Local OCR screening for automatic photo captions; image bytes never leave the worker."""
import csv
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from PIL import Image, ImageOps

SCREEN_VERSION = 'tesseract-eng-psm11-v1'


def has_confident_text(tsv):
    rows = csv.DictReader(io.StringIO(tsv), delimiter='\t')
    if not {'level', 'conf', 'text'} <= set(rows.fieldnames or []):
        raise ValueError('Local text screening returned invalid output')
    words=[]
    for row in rows:
        if row.get('level') != '5':
            continue
        try:
            confidence=float(row['conf'])
        except (TypeError, ValueError):
            continue
        letters=''.join(re.findall(r'[A-Za-z]', row.get('text') or ''))
        if len(letters) >= 2 and confidence >= 65:
            words.append((letters,confidence))
    return ((len(words) >= 2 and sum(len(word) for word,_ in words) >= 6)
            or any(len(word) >= 6 and confidence >= 85 for word,confidence in words))


def source_has_text(path):
    executable=shutil.which('tesseract')
    if not executable:
        raise ValueError('Automatic photo captions require local Tesseract OCR with English data')
    path=Path(path)
    if path.stat().st_size > 20_000_000:
        raise ValueError('Text-screening source exceeds 20MB')
    with Image.open(path) as original:
        if original.width * original.height > 20_000_000:
            raise ValueError('Text-screening source exceeds pixel limit')
        upright=ImageOps.exif_transpose(original).convert('RGBA')
        background=Image.new('RGBA',upright.size,'white')
        background.alpha_composite(upright)
        sample=ImageOps.contain(background.convert('RGB'),(1200,1200),Image.Resampling.LANCZOS)
        output=io.BytesIO();sample.save(output,'PNG')
    try:
        result=subprocess.run([executable,'stdin','stdout','-l','eng','--psm','11','tsv'],
            input=output.getvalue(),stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,
            timeout=15,check=True,env={**os.environ,'OMP_THREAD_LIMIT':'1'})
    except (OSError,subprocess.SubprocessError):
        raise ValueError('Local text screening failed; source was not cleared for automatic captions') from None
    if len(result.stdout)>1_000_000:
        raise ValueError('Local text screening output exceeded its bound')
    return has_confident_text(result.stdout.decode('utf-8',errors='replace'))


def screen_sources(paths,cache_path):
    """Cache only a bounded content-digest decision, never recognized private text."""
    cache_path=Path(cache_path)
    try:
        cache=json.loads(cache_path.read_text()) if cache_path.stat().st_size <= 64_000 else {}
        cache=cache if isinstance(cache,dict) else {}
    except (OSError,ValueError):
        cache={}
    decisions={}
    blocked=[]
    for path in paths:
        if path.stat().st_size > 20_000_000:
            raise ValueError('Text-screening source exceeds 20MB')
        digest=hashlib.sha256(path.read_bytes()).hexdigest()
        key=SCREEN_VERSION+':'+digest
        result=cache.get(key)
        if type(result) is not bool:
            result=source_has_text(path)
        decisions[key]=result
        if result:
            blocked.append(path)
    temporary=cache_path.with_suffix('.tmp')
    temporary.write_text(json.dumps(dict(list(decisions.items())[-300:])),encoding='utf-8')
    temporary.replace(cache_path)
    return blocked
