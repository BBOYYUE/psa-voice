package com.sollado.psavoice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
/** ADB-only text probe; DUMP permission is unavailable to ordinary apps. */
public final class DiagnosticReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!"com.sollado.psavoice.DEBUG_TEXT".equals(action) && !"com.sollado.psavoice.DEBUG_SPEAK".equals(action)) return;
        String text = intent.getStringExtra("text");
        if (text == null || text.trim().isEmpty() || text.length() > 2000) return;
        Intent service = new Intent(context, AssistantService.class);
        if ("com.sollado.psavoice.DEBUG_SPEAK".equals(action)) service.putExtra("diagnostic_speak", text);
        else service.putExtra("diagnostic_text", text);
        if ("general".equals(intent.getStringExtra("scene"))) service.putExtra("scene", "general");
        context.startForegroundService(service);
    }
}
