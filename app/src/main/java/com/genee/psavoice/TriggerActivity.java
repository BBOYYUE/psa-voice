package com.genee.psavoice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** NoDisplay toolbox bridge to the unified conversation panel. */
public final class TriggerActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        overridePendingTransition(0, 0);
        startActivity(new Intent(this, ConversationActivity.class).setAction(LiveAssistantService.TOGGLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
        overridePendingTransition(0, 0);
    }
}
