"""Private checkpoints for the scheduled guest HandNavigate worker."""
from __future__ import annotations
import argparse
import io
import json
from pathlib import Path, PurePosixPath
import zipfile
from tools.supabase_factory import PRIVATE_BUCKET, SupabaseFactory

OBJECT='integrations/handnavigate/hosted-state.zip'
JOURNALS={'cloud-worker.json','collections.json','collections.json.bak','runner/workflows.json','runner/workflows.json.bak'}
MAX_BYTES=70_000_000


def allowed(name):
    p=PurePosixPath(name)
    return (not p.is_absolute() and '..' not in p.parts and '\\' not in name
            and (name in JOURNALS or (p.parts[0]=='downloads' and p.suffix.lower() in {'.json','.jpg','.jpeg','.png','.webp'})))


def restore(client,folder):
    raw=client.download(PRIVATE_BUCKET,OBJECT,missing_ok=True)
    if raw is None:return
    if len(raw)>MAX_BYTES:raise ValueError('Hosted checkpoint is too large')
    root=Path(folder).resolve();root.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        if len(archive.infolist())>10000 or sum(i.file_size for i in archive.infolist())>100_000_000:
            raise ValueError('Hosted checkpoint exceeds extraction bounds')
        for info in archive.infolist():
            if not allowed(info.filename) or info.is_dir() or (info.external_attr>>16)&0o170000==0o120000:
                raise ValueError('Unsafe hosted checkpoint entry')
            target=(root/info.filename).resolve()
            if not target.is_relative_to(root):raise ValueError('Checkpoint left its data folder')
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(archive.read(info))


def save(client,folder):
    root=Path(folder).resolve()
    if not (root/'collections.json').exists():return
    envelope=json.loads((root/'collections.json').read_text())
    pending={Path(i['file']).resolve() for j in envelope['data']['jobs'] if j.get('status')!='VERIFIED_STORAGE' for i in j.get('items',[])}
    output=io.BytesIO()
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
        for file in root.rglob('*'):
            if not file.is_file() or file.is_symlink():continue
            name=file.relative_to(root).as_posix()
            if not allowed(name):continue
            # Retain durable dedup history, but only unfinished originals. Verified
            # sources already live in Supabase; no browser sessions or keys leave
            # this ephemeral runner and no collection is put in a public artifact.
            if name.startswith('downloads/') and file.resolve() not in pending:continue
            archive.write(file,name)
    if output.tell()>MAX_BYTES:raise ValueError('Hosted checkpoint is too large; preserve unfinished work before retrying')
    raw=output.getvalue();client.upload(PRIVATE_BUCKET,OBJECT,raw,'application/zip')
    if client.download(PRIVATE_BUCKET,OBJECT)!=raw:raise ValueError('Hosted checkpoint read-back failed')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['restore','save']);parser.add_argument('--folder',required=True)
    args=parser.parse_args();(restore if args.action=='restore' else save)(SupabaseFactory(),args.folder)
    if args.action=='restore':
        from api._collection_control import rows
        client=SupabaseFactory();history=[]
        for offset in range(0,20000,100):
            page=rows(client,'media_intake_items',limit=100,offset=offset)
            history.extend({'sha256':r['original_sha256'],'source':r['source_url']} for r in page)
            if len(page)<100:break
        root=Path(args.folder);root.mkdir(parents=True,exist_ok=True)
        (root/'cloud-history.json').write_text(json.dumps(history),encoding='utf-8')
    print('Private hosted checkpoint '+args.action+' complete; no profile, credential or image output logged.')
