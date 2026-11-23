package com.genee.psavoice;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/** A translucent ordinary Activity, not a system overlay. Service owns the conversation. */
public final class ConversationActivity extends Activity {
    static final String SHOW = "com.genee.psavoice.SHOW_CONVERSATION";
    private static final int AUDIO_PERMISSION = 81;
    private final Runnable observer = this::render;
    private TextView status;
    private TextView detail;
    private LinearLayout messages;
    private ScrollView scroll;
    private Button retry;
    private boolean pendingPermission;
    private String rendered = "";

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(R.layout.activity_conversation);
        overridePendingTransition(0, 0);
        status = findViewById(R.id.conversation_status);
        detail = findViewById(R.id.conversation_detail);
        messages = findViewById(R.id.conversation_messages);
        scroll = findViewById(R.id.conversation_scroll);
        retry = findViewById(R.id.conversation_retry);
        findViewById(R.id.conversation_close).setOnClickListener(view -> leave());
        retry.setOnClickListener(view -> startOrCancel());
        View panel = findViewById(R.id.conversation_panel);
        panel.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (panel.getWidth() <= 0 || panel.getHeight() <= 0) return;
                panel.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) panel.getLayoutParams();
                layout.width = Math.min(panel.getWidth(), dp(760));
                layout.height = Math.min(panel.getHeight(), dp(560));
                panel.setLayoutParams(layout);
            }
        });
        pendingPermission = saved != null && saved.getBoolean("pending_permission");
        if (saved == null && !SHOW.equals(getIntent().getAction())) startOrCancel();
        else if (saved != null && !ConversationStore.active && !ConversationStore.ended && !pendingPermission)
            ConversationStore.end("会话已中断，请重新开始。", true);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!SHOW.equals(intent.getAction())) startOrCancel();
    }
    private void startOrCancel() {
        if (ConversationStore.active) {
            startService(new Intent(this, LiveAssistantService.class).setAction(LiveAssistantService.CANCEL));
            return;
        }
        ConversationStore.prepare();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingPermission = true;
            ConversationStore.state("需要麦克风权限", "允许麦克风后才能开始语音对话");
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION);
        } else startSession();
    }
    private void startSession() {
        try { startForegroundService(new Intent(this, LiveAssistantService.class).setAction(LiveAssistantService.TOGGLE)); }
        catch (RuntimeException e) { ConversationStore.end("暂时无法启动语音服务，请重新打开助手。", true); }
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != AUDIO_PERMISSION) return;
        pendingPermission = false;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) startSession();
        else ConversationStore.end("麦克风权限未授予，无法进行语音对话。", true);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("pending_permission", pendingPermission);
        super.onSaveInstanceState(state);
    }
    @Override protected void onStart() { super.onStart(); ConversationStore.observe(observer); render(); }
    @Override protected void onStop() { ConversationStore.remove(observer); super.onStop(); }
    private void render() {
        if (isFinishing()) return;
        if (ConversationStore.ended && !ConversationStore.failed) { finish(); return; }
        status.setText(ConversationStore.status);
        status.setTextColor(Color.parseColor(ConversationStore.failed ? "#FFB2A6" : "#8CE3CA"));
        detail.setText(ConversationStore.detail);
        retry.setVisibility(ConversationStore.failed ? View.VISIBLE : View.GONE);
        ArrayList<ConversationStore.Turn> turns = ConversationStore.history();
        StringBuilder key = new StringBuilder();
        for (ConversationStore.Turn turn : turns) key.append(turn.id).append('\0').append(turn.user)
                .append('\0').append(turn.finalText).append('\0').append(turn.assistant).append('\0').append(turn.action);
        if (key.toString().equals(rendered) && messages.getChildCount() > 0) return;
        rendered = key.toString();
        boolean follow = scroll.getChildCount() == 0 || scroll.getScrollY() + scroll.getHeight() >= messages.getHeight() - dp(70);
        messages.removeAllViews();
        if (turns.isEmpty()) bubble("随时开口", "可以点歌，也可以聊聊。", false, true);
        for (ConversationStore.Turn turn : turns) {
            if (!turn.user.isEmpty()) bubble(turn.finalText ? "你" : "你 · 正在识别", turn.user, true, !turn.finalText);
            if (!turn.assistant.isEmpty()) bubble("助手", turn.assistant, false, false);
            if (!turn.action.isEmpty()) bubble("音乐", turn.action, false, true);
        }
        if (follow) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void bubble(String label, String text, boolean user, boolean muted) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(16));
        background.setColor(Color.parseColor(user ? "#553B7967" : "#303F5260"));
        box.setBackground(background);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.setMargins(user ? dp(44) : 0, 0, user ? 0 : dp(32), dp(12));
        messages.addView(box, layout);
        TextView role = new TextView(this);
        role.setText(label); role.setTextSize(13); role.setTextColor(Color.parseColor("#A6C7D0"));
        box.addView(role);
        TextView body = new TextView(this);
        body.setText(text); body.setTextSize(21); body.setLineSpacing(dp(3), 1);
        body.setTextColor(Color.parseColor(muted ? "#B9CED7" : "#F1F7FA"));
        if (user && muted) body.setTypeface(null, Typeface.ITALIC);
        body.setPadding(0, dp(5), 0, 0);
        box.addView(body);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void leave() {
        if (ConversationStore.active) startService(new Intent(this, LiveAssistantService.class).setAction(LiveAssistantService.CANCEL));
        finish();
        overridePendingTransition(0, 0);
    }
    @Override public void onBackPressed() { leave(); }
}
