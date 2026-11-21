package com.genee.psavoice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Toolbox launcher target; no window, task animation, or recent-task entry. */
public final class TriggerActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        overridePendingTransition(0, 0);
        startForegroundService(new Intent(this, AssistantService.class));
        finish();
        overridePendingTransition(0, 0);
    }
}
