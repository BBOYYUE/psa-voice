package com.sollado.psavoice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** 兼容现有电话键组件绑定，转入同一个连续会话面板。 */
public final class ChatTriggerActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        overridePendingTransition(0, 0);
        startActivity(new Intent(this, ConversationActivity.class).setAction(LiveAssistantService.TOGGLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
        overridePendingTransition(0, 0);
    }
}
