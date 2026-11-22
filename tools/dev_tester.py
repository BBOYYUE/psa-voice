#!/usr/bin/env python3
"""PSA 语音链路本地测试台（仅本机使用，token 不进入页面）。

用法:  python3 tools/dev_tester.py [端口]
然后浏览器打开 http://127.0.0.1:8765

- 浏览器录制 16kHz 单声道 PCM 并编码为 WAV（与车机录音格式一致），或上传已有 WAV
- 转发到线上网关 /v1/voice（附带 token，页面永远看不到 token）
- 页面显示：转写、动作/回答、分段耗时、X-Stage、波形与电平统计
"""
from __future__ import annotations

import http.client
import json
import os
import sys
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GATEWAY = "ai.sollado.com"


def load_token() -> str:
    env = ROOT / ".private" / "server" / "gateway.env"
    for line in env.read_text().splitlines():
        if line.startswith("VOICE_GATEWAY_TOKEN="):
            return line.split("=", 1)[1].strip().strip('"').strip("'")
    raise SystemExit("未找到 .private/server/gateway.env 中的 VOICE_GATEWAY_TOKEN")


TOKEN = load_token()

PAGE = """<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="utf-8">
<title>PSA 语音链路测试台</title>
<style>
body{font:14px/1.6 -apple-system,sans-serif;max-width:880px;margin:24px auto;padding:0 16px;background:#111;color:#eee}
h1{font-size:18px}
button,select,input{font-size:14px;padding:6px 12px;margin:2px}
button{cursor:pointer}
#rec{background:#c0392b;color:#fff;border:none;border-radius:6px}
#rec.on{background:#27ae60}
canvas{width:100%;height:90px;background:#000;border-radius:6px}
pre{background:#1c1c1c;padding:10px;border-radius:6px;overflow:auto;font-size:12px;white-space:pre-wrap}
table{border-collapse:collapse;width:100%;font-size:12px}
td,th{border:1px solid #444;padding:4px 6px;text-align:left}
.stat{color:#7fd}
</style>
</head>
<body>
<h1>PSA 语音链路测试台 <span class="stat" id="gw"></span></h1>
<div>
  <select id="scene"><option value="music">music（音乐指令）</option><option value="general">general（语音聊天）</option></select>
  <button id="rec">● 开始录音</button>
  <label><input type="checkbox" id="ns"> 浏览器降噪</label>
  <label><input type="checkbox" id="agc" checked> 自动增益</label>
  <label><input type="checkbox" id="ec" checked> 回声消除</label>
  <input type="file" id="file" accept=".wav,audio/wav">
</div>
<div id="level" style="margin:8px 0;color:#888">电平：等待录音</div>
<canvas id="wave" width="880" height="90"></canvas>
<div id="result"></div>
<h3>历史 <button id="export">导出 JSON</button></h3>
<table><thead><tr><th>#</th><th>场景</th><th>转写</th><th>结果</th><th>耗时</th><th>电平(peak/rms dB)</th></tr></thead><tbody id="hist"></tbody></table>
<pre id="raw" style="display:none"></pre>
<script>
let ctx, proc, stream, chunks = [], recording = false, t0rec;
const $ = id => document.getElementById(id);
const hist = [];
function showErr(e){ $('result').innerHTML='<pre style="color:#e74c3c">错误: '+(e && (e.message||e))+'</pre>'; }
window.onerror = (m,s,l,c,e)=>{ showErr(e||m); };

async function startRec() {
  try {
    const ns = $('ns').checked, agc = $('agc').checked, ec = $('ec').checked;
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) throw new Error('此浏览器不支持麦克风采集（请用 Chrome/Edge/Safari 打开 localhost 页面）');
    stream = await navigator.mediaDevices.getUserMedia({audio:{channelCount:1, noiseSuppression:ns, autoGainControl:agc, echoCancellation:ec}});
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) throw new Error('此浏览器不支持 AudioContext');
    try { ctx = new AC({sampleRate:16000}); } catch(_) { ctx = new AC(); }
    if (ctx.state === 'suspended') await ctx.resume();
    const src = ctx.createMediaStreamSource(stream);
    proc = ctx.createScriptProcessor(4096,1,1);
    chunks = [];
    proc.onaudioprocess = e => { chunks.push(new Float32Array(e.inputBuffer.getChannelData(0))); drawLevel(); };
    src.connect(proc); proc.connect(ctx.destination);
    recording = true; t0rec = performance.now();
    $('rec').textContent = '■ 停止并提交'; $('rec').className = 'on';
    $('result').innerHTML = '录音中（采样率 '+ctx.sampleRate+'Hz）…说完点"停止并提交"';
  } catch (e) {
    recording = false;
    showErr(e.name==='NotAllowedError' ? '麦克风权限被拒绝，请在浏览器设置中允许本页面使用麦克风' : e);
  }
}
function stopRec() {
  recording = false;
  proc.disconnect(); stream.getTracks().forEach(t=>t.stop()); ctx.close();
  const n = chunks.reduce((s,c)=>s+c.length,0);
  const pcm = new Float32Array(n); let off=0;
  for (const c of chunks) { pcm.set(c,off); off+=c.length; }
  const wav = encodeWav(pcm, 16000);
  analyze(wav);
  $('rec').textContent = '● 开始录音'; $('rec').className = '';
  upload(wav, 'mic');
}
function encodeWav(samples, rate) {
  const b = new ArrayBuffer(44 + samples.length*2), v = new DataView(b);
  const ws = (o,s)=>{for(let i=0;i<s.length;i++)v.setUint8(o+i,s.charCodeAt(i));};
  ws(0,'RIFF'); v.setUint32(4,36+samples.length*2,true); ws(8,'WAVEfmt ');
  v.setUint32(16,16,true); v.setUint16(20,1,true); v.setUint16(22,1,true);
  v.setUint32(24,rate,true); v.setUint32(28,rate*2,true); v.setUint16(32,2,true); v.setUint16(34,16,true);
  ws(36,'data'); v.setUint32(40,samples.length*2,true);
  for (let i=0;i<samples.length;i++) { let s=Math.max(-1,Math.min(1,samples[i])); v.setInt16(44+i*2, s<0?s*0x8000:s*0x7FFF, true); }
  return b;
}
function drawLevel() {
  const last = chunks[chunks.length-1]; if(!last) return;
  let peak=0; for (const s of last) peak=Math.max(peak,Math.abs(s));
  const db = 20*Math.log10(peak+1e-9);
  $('level').textContent = '电平：' + (isFinite(db)?db.toFixed(1):'--') + ' dBFS 峰值';
}
function analyze(wavBuf) {
  const v = new DataView(wavBuf), n = (v.byteLength-44)/2;
  let peak=0, sum=0, clip=0;
  const pts = [];
  for (let i=0;i<n;i++){ const s=v.getInt16(44+i*2,true)/32768; peak=Math.max(peak,Math.abs(s)); sum+=s*s; if(Math.abs(s)>0.98)clip++; if(i%Math.floor(n/880||1)===0)pts.push(Math.abs(s)); }
  const rms=Math.sqrt(sum/n), dur=n/16000;
  const db=x=>(20*Math.log10(x+1e-9)).toFixed(1);
  const c=$('wave').getContext('2d'); c.clearRect(0,0,880,90); c.fillStyle='#4af';
  pts.forEach((p,i)=>c.fillRect(i*(880/pts.length),45-p*44,2,p*88));
  $('level').textContent = `时长 ${dur.toFixed(1)}s · peak ${db(peak)} dBFS · rms ${db(rms)} dBFS · 削波 ${(100*clip/n).toFixed(1)}%`;
  return {dur, peak:+db(peak), rms:+db(rms)};
}
async function upload(wavBuf, source) {
  const scene=$('scene').value, rid='web-'+Math.random().toString(16).slice(2,10);
  const fd=new FormData();
  fd.append('scene',scene); fd.append('device_id','mac-web'); fd.append('request_id',rid);
  fd.append('client_version','0.5.0'); fd.append('trigger','mac_web_'+source);
  fd.append('file', new Blob([wavBuf],{type:'audio/wav'}), 'record.wav');
  $('result').innerHTML='上传中…';
  const t0=performance.now();
  let stage=null, tHead=null;
  try {
    const resp = await fetch('/api/voice', {method:'POST', body:fd});
    stage = resp.headers.get('x-stage'); tHead=(performance.now()-t0)/1000;
    const body = await resp.json(); const total=(performance.now()-t0)/1000;
    const meta = body.meta||{};
    const res = body.ok ? (body.decision ? body.decision+' '+JSON.stringify(body.action||body.answer) : body.answer) : 'ERR '+JSON.stringify(body.error);
    const kb = (wavBuf.byteLength/1024).toFixed(1);
    const durS = analyzeCached ? analyzeCached.dur.toFixed(1)+'s' : '-';
    $('result').innerHTML =
`<pre><b>请求</b> POST /v1/voice (multipart/form-data)
  scene=${scene}  device_id=mac-web  request_id=${rid}
  client_version=0.5.0  trigger=mac_web_${source}
  file=record.wav  ${kb} KB  ${durS} 16kHz/16bit/单声道
  (token 由本地代理注入，页面不可见)

<b>响应</b> X-Stage: ${stage||'(无)'} · headers ${tHead.toFixed(2)}s · total ${total.toFixed(2)}s
${JSON.stringify(body,null,2)}</pre>`;
    const stats = analyzeCached;
    hist.unshift({t:new Date().toISOString(), scene, transcript:body.transcript, res, total:+total.toFixed(2), stage, stats});
    const tr=document.createElement('tr');
    tr.innerHTML=`<td>${hist.length}</td><td>${scene}</td><td>${body.transcript||''}</td><td>${(res||'').slice(0,60)}</td><td>${total.toFixed(1)}s</td><td>${stats?stats.peak+'/'+stats.rms:'-'}</td>`;
    $('hist').prepend(tr);
  } catch(e) { showErr(e); }
}
let analyzeCached=null;
const origAnalyze=analyze; analyze=w=>{analyzeCached=origAnalyze(w);return analyzeCached;};
$('rec').onclick = ()=> recording? stopRec() : startRec();
$('file').onchange = async e=>{
  const f=e.target.files[0]; if(!f) return;
  const buf=await f.arrayBuffer(); analyzeCached=null;
  // 只支持标准 16bit PCM WAV
  analyze(buf); upload(buf,'file:'+f.name);
};
$('export').onclick=()=>{
  const a=document.createElement('a');
  a.href=URL.createObjectURL(new Blob([JSON.stringify(hist,null,2)],{type:'application/json'}));
  a.download='psa-voice-test-log.json'; a.click();
};
fetch('/api/health').then(r=>r.json()).then(h=>$('gw').textContent=h.ok?'· 网关在线':'· 网关异常').catch(()=>$('gw').textContent='· 网关不可达');
</script>
</body>
</html>
"""


def forward(wav: bytes, fields: dict) -> tuple[dict, dict, float, float]:
    rid = fields.get("request_id") or ("mac-" + uuid.uuid4().hex[:8])
    boundary = "psa-" + rid
    head = b""
    for key, value in fields.items():
        head += ('--%s\r\nContent-Disposition: form-data; name="%s"\r\n\r\n%s\r\n'
                 % (boundary, key, value)).encode()
    head += ('--%s\r\nContent-Disposition: form-data; name="file"; filename="record.wav"\r\n'
             'Content-Type: audio/wav\r\n\r\n' % boundary).encode()
    body = head + wav + ("\r\n--%s--\r\n" % boundary).encode()
    conn = http.client.HTTPSConnection(GATEWAY, timeout=90)
    t0 = time.time()
    conn.request("POST", "/v1/voice", body=body,
                 headers={"Authorization": "Bearer " + TOKEN,
                          "Content-Type": "multipart/form-data; boundary=" + boundary})
    resp = conn.getresponse()
    t_head = time.time() - t0
    headers = {"x-stage": resp.getheader("X-Stage") or ""}
    data = resp.read()
    conn.close()
    return json.loads(data), headers, t_head, time.time() - t0


def parse_multipart(body: bytes, ctype: str) -> tuple[bytes, dict]:
    boundary = ctype.split("boundary=")[-1].encode()
    wav, fields = b"", {}
    for part in body.split(b"--" + boundary):
        part = part.strip(b"\r\n")
        if not part or part == b"--":
            continue
        head, _, content = part.partition(b"\r\n\r\n")
        if b'filename=' in head:
            wav = content.rstrip(b"\r\n")
        else:
            for line in head.decode(errors="replace").split("\r\n"):
                if "name=" in line:
                    name = line.split('name="')[1].split('"')[0]
                    fields[name] = content.decode(errors="replace").strip()
    return wav, fields


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def _send(self, code, data: bytes, ctype="application/json; charset=utf-8", extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/api/health":
            try:
                conn = http.client.HTTPSConnection(GATEWAY, timeout=10)
                conn.request("GET", "/health")
                data = conn.getresponse().read()
                conn.close()
                self._send(200, data)
            except Exception as exc:
                self._send(502, json.dumps({"ok": False, "error": str(exc)}).encode())
            return
        self._send(200, PAGE.encode(), "text/html; charset=utf-8")

    def do_POST(self):
        if self.path != "/api/voice":
            self._send(404, b'{"ok":false}')
            return
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)
        wav, fields = parse_multipart(body, self.headers.get("Content-Type", ""))
        if not wav:
            self._send(400, b'{"ok":false,"error":"no wav"}')
            return
        try:
            result, headers, t_head, t_total = forward(wav, fields)
        except Exception as exc:
            self._send(502, json.dumps({"ok": False, "error": str(exc)}, ensure_ascii=False).encode())
            return
        extra = {"x-stage": headers["x-stage"]} if headers.get("x-stage") else None
        self._send(200, json.dumps(result, ensure_ascii=False).encode(), extra=extra)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    print(f"测试台已启动: http://127.0.0.1:{port}  (token 从 .private 读取，不进入页面)")
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
