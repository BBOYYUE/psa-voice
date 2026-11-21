package com.genee.psavoice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
/** ADB-only text probe; DUMP permission is unavailable to ordinary apps. */
public final class DiagnosticReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!"com.genee.psavoice.DEBUG_TEXT".equals(intent.getAction())) return;
        String text = intent.getStringExtra("text");
        if (text == null || text.trim().isEmpty() || text.length() > 2000) return;
        context.startForegroundService(new Intent(context, AssistantService.class).putExtra("diagnostic_text", text));
    }
}
