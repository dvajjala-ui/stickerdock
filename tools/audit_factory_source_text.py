"""Read-only audit of existing private Factory photos. No generation or storage writes."""
import hashlib
import json
from pathlib import Path
import tempfile
from production_worker import STATE_OBJECT, safe_restore
from source_text_screening import SCREEN_VERSION, screen_sources
from supabase_factory import PRIVATE_BUCKET, SupabaseFactory


def audit(client):
    data=client.download(PRIVATE_BUCKET,STATE_OBJECT,missing_ok=True)
    if data is None:
        raise ValueError('No saved Factory source snapshot is available')
    with tempfile.TemporaryDirectory(prefix='stickerdock-source-audit-') as temporary:
        runtime=Path(temporary)
        safe_restore(data,runtime)
        sources=sorted(path for path in (runtime/'reference-images').glob('*')
            if path.is_file() and path.suffix.lower() in {'.png','.jpg','.jpeg','.webp'})
        if len(sources)>100:
            raise ValueError('Source audit exceeds its 100-image bound')
        blocked=screen_sources(sources,runtime/'audit-text-cache.json')
        return {'screen_version':SCREEN_VERSION,'screened':len(sources),
            'withheld':len(blocked),'clear':len(sources)-len(blocked),
            'withheld_sha256':[hashlib.sha256(path.read_bytes()).hexdigest() for path in blocked],
            'generation_calls':0,'storage_writes':0}


if __name__=='__main__':
    print(json.dumps(audit(SupabaseFactory()),sort_keys=True))
