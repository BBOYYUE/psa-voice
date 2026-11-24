#!/usr/bin/env python3
"""Verify pinned SDK, extract a car capability matrix, and compile offline calls.
No device connection, authorization, model request or playback is performed.
"""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SDK = ROOT / 'vendor/qplay-ipc-sdk/1.1.6'
EXPECTED = 'ec51699e3032fdd4e4d7a3864bc7b25b0ab47baf4c33506faf127897f733d0af'
assert hashlib.sha256((SDK / 'aidl-api-sdk-1.1.6.aar').read_bytes()).hexdigest() == EXPECTED
with zipfile.ZipFile(SDK / 'aidl-api-sdk-1.1.6.aar') as archive:
    assert archive.read('classes.jar') == (SDK / 'classes.jar').read_bytes()
jdk = Path(os.environ.get('JAVA_HOME', str(Path.home() / '.cache/psa-build/jdk21/Contents/Home')))
android = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))
classes = [
    'Methods', 'CommonCmd', 'Data$SearchType', 'Data$PlayMode', 'Events',
    'ErrorCodes', 'IQQMusicApi', 'IQQMusicApiCallback', 'IQQMusicApiEventListener',
]
api = subprocess.check_output([str(jdk / 'bin/javap'), '-classpath', str(SDK / 'classes.jar'),
    '-public', '-constants', *['com.tencent.qqmusic.third.api.contract.' + c for c in classes]], text=True)
for constant in ['PLAY_MODE_REPEAT_LIST = 0', 'PLAY_MODE_REPEAT_ONE = 1',
                 'PLAY_MODE_SHUFFLE = 2', 'SEARCH_TYPE_FOLDER = 3', 'ERROR_VOICE_NOT_SUPPORTED = 300']:
    assert constant in api, constant
(ROOT / 'docs/qplay-ipc/sdk-1.1.6-public-api.txt').write_text(api)
source = (ROOT / 'docs/qplay-ipc/upstream/interface.md').read_text()
matrix = []
for block in re.split(r'(?m)^## ', source):
    name = re.search(r'\b(?:void|int|long|boolean|String|Data\.Song)\s+(\w+)\s*\(', block)
    car = re.search(r'\|\s*car\s*\|\s*([^|]+)\s*\|', block)
    if name and car:
        matrix.append({'method': name[1], 'description': block.splitlines()[0],
                       'car_min_version': None if car[1].strip() == 'null' else car[1].strip(),
                       'async': 'IQQMusicApiCallback' in block})
by_name = {entry['method']: entry for entry in matrix}
for method in ['search', 'addToFavourite', 'removeFromFavourite', 'getPlayMode', 'setPlayModeWithRet', 'playSongIdAtIndex']:
    assert by_name[method]['car_min_version'], method
for method in ['voicePlay', 'voiceShortcut', 'playSongMidAtIndex']:
    assert by_name[method]['car_min_version'] is None, method
(ROOT / 'docs/qplay-ipc/car-capabilities.json').write_text(json.dumps(matrix, ensure_ascii=False, indent=2) + '\n')
with tempfile.TemporaryDirectory(prefix='qplay-compile-') as build:
    subprocess.run([str(jdk / 'bin/javac'), '-encoding', 'UTF-8', '-cp',
        str(SDK / 'classes.jar') + os.pathsep + str(android / 'platforms/android-34/android.jar'),
        '-d', build, str(ROOT / 'experiments/qplay-ipc/src/com/sollado/psavoice/qplay/QplayCarCalls.java')], check=True)
print(f'PASS: SDK digest, embedded classes, protocol constants, {len(matrix)} documented methods, and offline adapter compilation.')
print('NOT TESTED: binding, caller identity, user authorization, callbacks, data payloads, and car execution.')
