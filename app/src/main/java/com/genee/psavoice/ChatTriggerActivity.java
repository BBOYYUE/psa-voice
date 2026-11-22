package com.genee.psavoice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** 电话键长按的语音聊天入口；无窗口、无切换动画、不留最近任务。 */
public final class ChatTriggerActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        overridePendingTransition(0, 0);
        startForegroundService(new Intent(this, AssistantService.class).putExtra("scene", "general"));
        finish();
        overridePendingTransition(0, 0);
    }
}
