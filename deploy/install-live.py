#!/usr/bin/env python3
"""Run on the server after staging tests pass. Back up, atomically install, restart; rollback on failure."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path('/home/sollado/projects/voice-gateway')
FILES = {'app.py':'app.py', 'live_agent.py':'live_agent.py', 'live_asr.py':'live_asr.py',
         'live_session.py':'live_session.py', 'LIVE.md':'LIVE.md', 'README.md':'README.md',
         'test_live.py':'tests/test_live.py'}

def restore(backup):
    manifest=json.loads((backup/'manifest.json').read_text())
    for name,existed in manifest.items():
        target=ROOT/name
        if existed:
            shutil.copy2(backup/name,target)
        else:
            target.unlink(missing_ok=True)
    subprocess.run(['systemctl','--user','restart','voice-gateway.service'],check=True)

def main(args):
    if args.rollback:
        backup=Path(args.rollback).resolve()
        if ROOT/'backups' not in backup.parents:
            raise ValueError('Rollback must use a gateway backup')
        restore(backup)
        print('Restored',backup)
        return
    stage=Path(args.stage).resolve()
    for source in FILES:
        if not (stage/source).is_file(): raise FileNotFoundError(source)
    if hashlib.sha256((ROOT/'app.py').read_bytes()).hexdigest()!=args.expected_app_sha:
        raise RuntimeError('Online app.py changed since preflight; refusing overwrite')
    stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ-live')
    backup=ROOT/'backups'/stamp
    backup.mkdir(mode=0o700,parents=True,exist_ok=False)
    manifest={}
    for name in [*FILES.values(),'.env']:
        source=ROOT/name
        manifest[name]=source.exists()
        if source.exists():
            target=backup/name
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(source,target)
    (backup/'manifest.json').write_text(json.dumps(manifest,indent=2))
    print('BACKUP',backup,flush=True)
    try:
        for source,name in FILES.items():
            target=ROOT/name
            target.parent.mkdir(parents=True,exist_ok=True)
            tmp=target.with_name(target.name+'.live-tmp')
            shutil.copy2(stage/source,tmp)
            os.replace(tmp,target)
        model=ROOT/'models/streaming-zipformer-zh-en-int8'
        values={'VG_LIVE_ENABLED':'true','VG_LIVE_ASR_TOKENS':str(model/'tokens.txt'),
                'VG_LIVE_ASR_SILENCE_S':'0.8'}
        for key in ('encoder','decoder','joiner'):
            values['VG_LIVE_ASR_'+key.upper()]=str(next(model.glob(key+'*.int8.onnx')))
        env=ROOT/'.env'
        lines=[line for line in env.read_text().splitlines()
               if line.strip().split('=',1)[0] not in values]
        lines.extend(key+'='+value for key,value in values.items())
        tmp=ROOT/'.env.live-tmp'
        fd=os.open(tmp,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
        with os.fdopen(fd,'w') as out: out.write('\n'.join(lines)+'\n')
        os.chmod(tmp,0o600)
        os.replace(tmp,env)
        subprocess.run(['systemctl','--user','restart','voice-gateway.service'],check=True)
    except BaseException:
        restore(backup)
        raise
    print('Installed; verify /health.live.ready and WSS before declaring deployment complete.')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--stage')
    p.add_argument('--expected-app-sha')
    p.add_argument('--rollback')
    args=p.parse_args()
    if not args.rollback and not (args.stage and args.expected_app_sha):
        p.error('stage and expected-app-sha are required')
    main(args)
