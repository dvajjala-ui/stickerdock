"""Inventory requirement documents and source references, without claiming QA.

Produces a reproducible snapshot for manual acceptance review. Keywords are
review cues, not a classifier proving a feature works. Never reads work/private
media, environment files or generated build directories.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re


def inventory(root):
    paths = [root / 'AGENTS.md', root / 'README.md']
    paths += sorted((root / 'docs').glob('*.md'))
    paths += [root / 'ios/README.md', root / 'sticker-core/README.md']
    records = []
    for path in paths:
        if not path.is_file():
            continue
        data = path.read_bytes()
        text = data.decode('utf-8')
        cues = [{'line': i, 'text': line} for i, line in enumerate(text.splitlines(), 1)
                if re.search(r'pending|unverified|not implemented|automatically downloads|not yet|must not|requires? ', line, re.I)]
        records.append({'path': path.relative_to(root).as_posix(), 'sha256': hashlib.sha256(data).hexdigest(),
                        'lines': len(text.splitlines()), 'requirement_ids': sorted(set(re.findall(r'\bR\d{2}\b', text))),
                        'review_cues': cues})
    return {'documents': records, 'document_count': len(records),
            'acceptance_source': 'docs/REQUIREMENTS_ACCEPTANCE_AUDIT_2026-10-16.md',
            'limitations': ['Document inventory only; feature status requires source and test evidence',
                            'Historical requirements remain recorded, not silently marked complete']}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, default=Path('work/requirements-audit/md-inventory.json'))
    args = parser.parse_args()
    result = inventory(Path(__file__).resolve().parent.parent)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=True), encoding='utf-8')
    print(json.dumps({'documents': result['document_count'], 'output': str(args.output)}))
