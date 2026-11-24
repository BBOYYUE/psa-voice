package com.sollado.psavoice;
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.*;
import android.media.audiofx.*;
import android.os.Bundle;
import android.widget.*;
import java.util.*;

/** Foreground-only manual capture; no files, playback, ASR or network. */
public final class AudioDiagnosticActivity extends Activity {
 private TextView output;
 private Spinner source;
 private CheckBox effects;
 private Button start;
 private volatile boolean stopped=true;
 private volatile AudioRecord recorder;
 private Thread worker;
 private boolean visible;
 @Override public void onCreate(Bundle b) {
  super.onCreate(b);
  LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(24,24,24,24);
  TextView title=new TextView(this); title.setText("音频对比：每次最多5秒，不保存、不上传。请先结束助手会话。电平不能代表识别或消回声效果。"); box.addView(title);
  source=new Spinner(this); source.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"MIC","VOICE_RECOGNITION","VOICE_COMMUNICATION"})); box.addView(source);
  effects=new CheckBox(this); effects.setText("尝试显式开启 AEC / NS / AGC（仅本次录音）"); box.addView(effects);
  start=new Button(this); start.setText("开始5秒测试"); box.addView(start);
  Button stop=new Button(this); stop.setText("立即停止"); stop.setOnClickListener(v->stop()); box.addView(stop);
  output=new TextView(this); output.setTextIsSelectable(true); ScrollView scroll=new ScrollView(this); scroll.addView(output); box.addView(scroll); setContentView(box);
  start.setOnClickListener(v->{
   if(worker!=null && worker.isAlive())return;
   if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},42); return; }
   int src=new int[]{MediaRecorder.AudioSource.MIC,MediaRecorder.AudioSource.VOICE_RECOGNITION,MediaRecorder.AudioSource.VOICE_COMMUNICATION}[source.getSelectedItemPosition()];
   boolean enable=effects.isChecked(); output.setText(""); stopped=false; start.setEnabled(false);
   worker=new Thread(()->capture(src,enable),"audio-diagnostic"); worker.start();
  });
 }
 private void log(String s) { runOnUiThread(()->{if(visible)output.append(s+"\n");}); }
 private void effect(AudioEffect e,boolean enable,List<AudioEffect> all) {
  if(e==null) {log("效果创建返回 null");return;}
  all.add(e); log(e.getDescriptor().name+" control="+e.hasControl()+" initial="+e.getEnabled());
  if(enable)log("setEnabled="+e.setEnabled(true)+" enabled="+e.getEnabled());
 }
 private void capture(int src,boolean enable) {
  AudioRecord r=null; List<AudioEffect> all=new ArrayList<>();
  try {
   log("AEC="+AcousticEchoCanceler.isAvailable()+" NS="+NoiseSuppressor.isAvailable()+" AGC="+AutomaticGainControl.isAvailable());
   AudioEffect.Descriptor[] ds=AudioEffect.queryEffects(); if(ds!=null)for(AudioEffect.Descriptor d:ds)log("effect="+d.name+" type="+d.type+" uuid="+d.uuid);
   int min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
   if(min<=0)throw new IllegalStateException("不支持录音格式");
   r=new AudioRecord(src,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min*2,6400)); recorder=r;
   if(r.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("录音初始化失败");
   int session=r.getAudioSessionId(); log("source="+r.getAudioSource()+" session="+session+" rate="+r.getSampleRate());
   // Baseline does not create explicit effects; source/HAL defaults may still apply.
   if(enable) {
    try {effect(AcousticEchoCanceler.create(session),true,all);}catch(RuntimeException e){log("AEC: "+e);}
    try {effect(NoiseSuppressor.create(session),true,all);}catch(RuntimeException e){log("NS: "+e);}
    try {effect(AutomaticGainControl.create(session),true,all);}catch(RuntimeException e){log("AGC: "+e);}
   }
   if(stopped)return;
   r.startRecording(); if(r.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("未开始录音");
   short[] data=new short[1600]; long end=android.os.SystemClock.elapsedRealtime()+5000;
   while(!stopped && android.os.SystemClock.elapsedRealtime()<end) {
    int n=r.read(data,0,data.length,AudioRecord.READ_NON_BLOCKING);
    if(n<0)throw new IllegalStateException("读取失败 "+n);
    if(n==0){Thread.sleep(20);continue;}
    double sum=0;int peak=0; for(int j=0;j<n;j++){int x=data[j];sum+=(double)x*x;peak=Math.max(peak,Math.abs(x));}
    log(String.format(Locale.ROOT,"RMS %.1f dBFS / peak %d",20*Math.log10(Math.max(1,Math.sqrt(sum/n))/32768),peak));
   }
   log("测试结束；音频未保存");
  }catch(Exception e){log("测试失败："+e);}
  finally {
   stopped=true; recorder=null;
   for(AudioEffect e:all)e.release();
   if(r!=null){try{r.stop();}catch(IllegalStateException ignored){}r.release();}
   runOnUiThread(()->{if(visible)start.setEnabled(true);});
  }
 }
 private void stop(){stopped=true;AudioRecord r=recorder;if(r!=null)try{r.stop();}catch(IllegalStateException ignored){}}
 @Override public void onResume(){super.onResume();visible=true;if(worker==null||!worker.isAlive())start.setEnabled(true);}
 @Override public void onPause(){visible=false;stop();super.onPause();}
}
