package com.sollado.psaprobe;
import android.os.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.lang.reflect.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ProbeCancellationTest {
 @Test public void timeoutSuppressesLateResultAndSubsequentTransaction() throws Exception {
  var controller=Robolectric.buildActivity(ProbeActivity.class).setup();
  ProbeActivity activity=controller.get();
  Class<?> runType=Class.forName("com.sollado.psaprobe.ProbeActivity$QueryRun");
  Constructor<?> ctor=runType.getDeclaredConstructor();ctor.setAccessible(true);Object run=ctor.newInstance();
  Field cancelled=runType.getDeclaredField("cancelled");cancelled.setAccessible(true);
  int[] calls={0};
  Binder binder=new Binder(){@Override protected boolean onTransact(int code,Parcel in,Parcel out,int flags){
   calls[0]++; try{cancelled.setBoolean(run,true);}catch(Exception e){throw new RuntimeException(e);}
   out.writeNoException();out.writeInt(1);return true;
  }};
  Method query=ProbeActivity.class.getDeclaredMethod("query",runType,IBinder.class,String.class,int.class,String.class,boolean.class);query.setAccessible(true);
  query.invoke(activity,run,binder,"test",1,"late",false);
  query.invoke(activity,run,binder,"test",1,"next",false);
  Shadows.shadowOf(Looper.getMainLooper()).idle();
  assertEquals(1,calls[0]);
  Field output=ProbeActivity.class.getDeclaredField("output");output.setAccessible(true);
  assertEquals("",((android.widget.TextView)output.get(activity)).getText().toString());
  controller.pause().stop().destroy();
 }
}
