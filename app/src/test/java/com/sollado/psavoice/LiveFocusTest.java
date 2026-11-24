package com.sollado.psavoice;
import android.media.AudioManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.lang.reflect.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class LiveFocusTest {
 @Test public void focusLossDuringSpeechEndsSession() throws Exception {
  for(int loss:new int[]{AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,AudioManager.AUDIOFOCUS_LOSS}) {
   var controller=Robolectric.buildService(LiveAssistantService.class).create();
   LiveAssistantService service=controller.get();
   Field running=LiveAssistantService.class.getDeclaredField("running");running.setAccessible(true);running.set(service,true);
   Field speaking=LiveAssistantService.class.getDeclaredField("speaking");speaking.setAccessible(true);speaking.set(service,true);
   Method changed=LiveAssistantService.class.getDeclaredMethod("focusChanged",long.class,int.class);changed.setAccessible(true);
   changed.invoke(service,0L,loss);
   assertFalse(running.getBoolean(service));assertFalse(speaking.getBoolean(service));
   controller.destroy();
  }
 }
}
