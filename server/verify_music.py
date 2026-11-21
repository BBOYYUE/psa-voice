import io,json,time,wave,requests
from pathlib import Path
import config
base='https://ai.sollado.com'
headers={'Authorization':'Bearer '+config.GATEWAY_TOKEN}
for text,kind in [('周杰伦的晴天','music.search_play'),('先搜晴天不要播放','music.search'),('下一首','music.next'),('帮我导航回家',None)]:
 start=time.monotonic()
 r=requests.post(base+'/v1/text',headers=headers,json={'text':text,'scene':'music','device_id':'contract-public-test','request_id':'check-'+str(time.time_ns())},timeout=45)
 r.raise_for_status();d=r.json()
 assert d['schema']=='v1' and d['ok'],d
 if kind: assert d['action']['type']==kind,d
 else: assert d['action'] is None and d['decision']=='unsupported',d
 print(json.dumps({'text':text,'decision':d['decision'],'action':d['action'],'seconds':round(time.monotonic()-start,2)},ensure_ascii=False))
r=requests.get(base+'/v1/capabilities',headers=headers,timeout=10);r.raise_for_status();assert r.json()['retention']['audio_saved'] is False
b=io.BytesIO()
with wave.open(b,'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(16000);w.writeframes(bytes(16000))
r=requests.post(base+'/v1/voice',headers=headers,files={'file':('silence.wav',b.getvalue(),'audio/wav')},data={'scene':'music'},timeout=30)
assert r.status_code==422 and r.json()['error']['code']=='empty_transcript',r.text
assert list(config.AUDIO_TEMP_DIR.iterdir())==[],list(config.AUDIO_TEMP_DIR.iterdir())
print('Silence rejected; request and ffmpeg temp files removed.')
r=requests.post(base+'/v1/voice',headers=headers,files={'file':('invalid.wav',b'not audio','audio/wav')},data={'scene':'music'},timeout=30)
assert r.status_code==422 and r.json()['action'] is None,r.text
assert list(config.AUDIO_TEMP_DIR.iterdir())==[]
print('Invalid audio rejected; temp files removed.')
p=Path('/home/sollado/hermes/workspace/voice-asr/t2.mp3')
if p.exists():
 with p.open('rb') as f:r=requests.post(base+'/v1/voice',headers=headers,files={'file':('fixture.mp3',f,'audio/mpeg')},data={'scene':'music','request_id':'fixture-'+str(time.time_ns())},timeout=45)
 r.raise_for_status();d=r.json();assert d['ok'] and d['schema']=='v1' and d['transcript'],d
 assert list(config.AUDIO_TEMP_DIR.iterdir())==[]
 print(json.dumps({'audio_e2e':True,'transcript':d['transcript'],'decision':d['decision'],'action':d['action'],'meta':d['meta']},ensure_ascii=False))
print('Public music contract verification passed.')
