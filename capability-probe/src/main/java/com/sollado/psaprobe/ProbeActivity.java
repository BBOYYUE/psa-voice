package com.sollado.psaprobe;
import android.app.Activity;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.util.*;

/** No microphone/network permissions. Only the statically verified getter transactions. */
public final class ProbeActivity extends Activity {
 private final Handler main = new Handler(Looper.getMainLooper());
 private final Set<ServiceConnection> connections = new HashSet<>();
 private TextView output;
 private volatile boolean closed;
 private static final class QueryRun { volatile boolean cancelled; }
 private final List<QueryRun> runs = new ArrayList<>();
 @Override public void onCreate(Bundle b) {
  super.onCreate(b);
  LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(24,24,24,24);
  TextView intro = new TextView(this); intro.setText("只查询原车能力，不录音、不播报、不修改设置。返回 false/空值也可能是后端未就绪。查询后自动解绑。"); box.addView(intro);
  Button run = new Button(this); run.setText("查询原车能力"); box.addView(run);
  output = new TextView(this); output.setTextIsSelectable(true); ScrollView scroll = new ScrollView(this); scroll.addView(output); box.addView(scroll); setContentView(box);
  run.setOnClickListener(v -> { if (!connections.isEmpty()) return; run.setEnabled(false); output.setText("重新打开页面可再次查询。\n"); bind(false); bind(true); });
 }
 private void report(String s) { main.post(() -> { if (!closed) output.append(s+"\n"); }); }
 private void report(QueryRun run, String s) { main.post(() -> { if (!closed && !run.cancelled) output.append(s+"\n"); }); }
 private void release(ServiceConnection c) { main.post(() -> { if (connections.remove(c)) unbindService(c); }); }
 private void bind(boolean voiceprint) {
  QueryRun run = new QueryRun(); runs.add(run);
  String descriptor = "com.cusc.manager.voicemanager."+(voiceprint?"IVprintManagerService":"IVoiceManagerService");
  ServiceConnection c = new ServiceConnection() {
   public void onServiceConnected(ComponentName name, IBinder binder) {
    if (closed || run.cancelled || !connections.contains(this)) return;
    new Thread(() -> {
     try {
      String actual = binder.getInterfaceDescriptor(); if (closed || run.cancelled) return; report(run, "Binder: "+actual);
      if (!descriptor.equals(actual)) throw new IllegalStateException("接口版本不匹配，停止查询");
      if (voiceprint) query(run, binder, descriptor, 2, "声纹可用性（0=当前可用；-1=未初始化或场景受限）", false);
      else {
       query(run, binder, descriptor, 1, "唤醒开关（1=开；0=关或后端未就绪）", false);
       if (closed || run.cancelled) return;
       query(run, binder, descriptor, 6, "附加/自定义唤醒词（不含完整默认词表）", true);
       if (!closed && !run.cancelled) speakers(run, binder, descriptor);
      }
     } catch (Exception e) { if (!run.cancelled) report(run, "查询失败："+e); }
     finally { release(this); }
    }, "read-only-binder").start();
   }
   public void onServiceDisconnected(ComponentName n) { report("服务断开"); release(this); }
   public void onNullBinding(ComponentName n) { report("服务返回空 Binder（可能未启用此能力）"); release(this); }
   public void onBindingDied(ComponentName n) { report("绑定失效"); release(this); }
  };
  Intent i = new Intent("android.intent.action.CUSC_"+(voiceprint?"VOICEPRINT_MANAGER_SERVICE":"VOICE_MANAGER_SERVICE"));
  i.setComponent(new ComponentName("com.cusc.voiceassistant", "com.cusc.voiceassistant.SpeechService"));
  try {
   if (bindService(i,c,Context.BIND_AUTO_CREATE)) { connections.add(c); main.postDelayed(() -> { if(connections.contains(c)) { run.cancelled=true; report("查询超时，已解除绑定；同步 Binder 调用无法强制中止"); release(c); } },8000); }
   else report("绑定失败："+i.getAction());
  } catch (Exception e) { report("绑定异常："+e); }
 }
 private void query(QueryRun run,IBinder b,String d,int code,String label,boolean string) throws RemoteException {
  if (closed || run.cancelled) return;
  Parcel in=Parcel.obtain(),out=Parcel.obtain();
  try { in.writeInterfaceToken(d); if(!b.transact(code,in,out,0)) throw new RemoteException("不支持事务 "+code); out.readException(); if (!closed && !run.cancelled) report(run, label+"："+(string?String.valueOf(out.readString()):Integer.toString(out.readInt()))); }
  finally {in.recycle();out.recycle();}
 }
 private void speakers(QueryRun run,IBinder b,String d) throws RemoteException {
  if (closed || run.cancelled) return;
  Parcel in=Parcel.obtain(),out=Parcel.obtain();
  try {
   in.writeInterfaceToken(d); if(!b.transact(10,in,out,0)) throw new RemoteException("不支持音色查询"); out.readException();
   if (closed || run.cancelled) return;
   int n=out.readInt(); if(n < -1 || n>100) throw new IllegalStateException("音色列表格式不匹配");
   report(run, "可选播报音色数量："+n+"（空列表也可能后端未就绪）");
   for(int j=0;j<n;j++) if(out.readInt()!=0) {
    String name=out.readString(),type=out.readString(),value=out.readString();
    out.readString();out.readString(); // SpeakerBean offlineTTS, onlineTTS
    report(run, "音色："+name+" / "+type+" / "+value);
   }
  } finally {in.recycle();out.recycle();}
 }
 @Override public void onDestroy() { closed=true; for(QueryRun run:runs)run.cancelled=true; main.removeCallbacksAndMessages(null); for(ServiceConnection c:new HashSet<>(connections)) { unbindService(c); } connections.clear(); super.onDestroy(); }
}
