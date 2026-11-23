package com.genee.psavoice;

import android.Manifest;
import android.content.Intent;
import android.widget.LinearLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class ConversationActivityTest {
    private ActivityController<ConversationActivity> controller;
    @Before public void setUp() {
        ConversationStore.prepare();
        ConversationStore.active = false;
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO);
    }
    @After public void tearDown() {
        if (controller != null) controller.pause().stop().destroy();
        ConversationStore.active = false;
    }
    private ConversationActivity show() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), ConversationActivity.class).setAction(ConversationActivity.SHOW);
        controller = Robolectric.buildActivity(ConversationActivity.class, intent).setup();
        return controller.get();
    }
    @Test public void inflatesAndReplacesPartialTranscriptRatherThanAppending() {
        ConversationActivity activity = show();
        ConversationStore.begin();
        ConversationStore.Turn turn = ConversationStore.turn(1);
        turn.user = "播放T";
        ConversationStore.changed();
        LinearLayout messages = activity.findViewById(R.id.conversation_messages);
        assertEquals(1, messages.getChildCount());
        turn.user = "播放T-ARA的歌"; turn.finalText = true;
        ConversationStore.changed();
        assertEquals(1, messages.getChildCount());
        turn.assistant = "你好。";
        ConversationStore.changed();
        assertEquals(2, messages.getChildCount());
    }
    @Test public void showingExistingPanelDoesNotToggleTheService() {
        show();
        ConversationStore.begin();
        controller.newIntent(new Intent(RuntimeEnvironment.getApplication(), ConversationActivity.class).setAction(ConversationActivity.SHOW));
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
    }
    @Test public void repeatedWakeCancelsInsteadOfStartingAnotherConversation() {
        show();
        ConversationStore.begin();
        controller.newIntent(new Intent(RuntimeEnvironment.getApplication(), ConversationActivity.class).setAction(LiveAssistantService.TOGGLE));
        Intent service = Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();
        assertNotNull(service);
        assertEquals(LiveAssistantService.CANCEL, service.getAction());
    }
    @Test public void recreationDoesNotSendAnotherWake() {
        show();
        ConversationStore.begin();
        controller.recreate();
        assertNull(Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService());
        assertTrue(ConversationStore.active);
    }
    @Test public void explicitExitClosesButFailureKeepsRetryVisible() {
        ConversationActivity activity = show();
        ConversationStore.begin();
        ConversationStore.end("连接断开", true);
        assertFalse(activity.isFinishing());
        assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.conversation_retry).getVisibility());
        ConversationStore.end("已结束", false);
        assertTrue(activity.isFinishing());
    }

    @Test
    @Config(sdk = 28, qualifiers = "w1280dp-h720dp-land-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void renderLandscapePreviewFromActualLayout() throws Exception {
        ConversationActivity activity = show();
        ConversationStore.begin();
        ConversationStore.Turn turn = ConversationStore.turn(1);
        turn.user = "播放T-ARA的歌"; turn.finalText = true;
        turn.assistant = "还想听点别的吗？";
        turn.action = "点歌指令已发送，请确认播放结果。";
        ConversationStore.state("正在听", "可以继续调整歌曲，也可以聊点别的");
        android.view.View root = activity.getWindow().getDecorView();
        int width = android.view.View.MeasureSpec.makeMeasureSpec(1280, android.view.View.MeasureSpec.EXACTLY);
        int height = android.view.View.MeasureSpec.makeMeasureSpec(720, android.view.View.MeasureSpec.EXACTLY);
        root.measure(width, height); root.layout(0, 0, 1280, 720);
        root.getViewTreeObserver().dispatchOnGlobalLayout();
        root.measure(width, height); root.layout(0, 0, 1280, 720);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(1280, 720, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        canvas.drawColor(android.graphics.Color.rgb(54, 67, 77));
        root.draw(canvas);
        java.io.File image = new java.io.File(System.getProperty("psa.preview.file"));
        image.getParentFile().mkdirs();
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(image)) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
        }
        assertTrue(activity.findViewById(R.id.conversation_panel).getWidth() > 0);
    }
}
