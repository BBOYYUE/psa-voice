package com.genee.psavoice;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MusicExecutor {
    static final class Outcome {
        final boolean confirmed;
        final String message;
        final String status;
        Outcome(boolean confirmed, String message) { this(confirmed ? "confirmed" : "sent", message); }
        Outcome(String status, String message) {
            this.status = status; this.confirmed = "confirmed".equals(status); this.message = message;
        }
    }
    private static final String QQ = "com.tencent.qqmusiccar";
    private static final String DISPATCHER = QQ + ".app.activity.DispacherActivityForThird";
    private static final String STARTER = QQ + ".app.activity.AppStarterActivity";
    // QQ 车机版官方外部拉起协议 action=2~9 的页面。
    private static final Map<String, Integer> PAGE_ACTIONS = new HashMap<>();
    private static final Map<String, String> PAGE_NAMES = new HashMap<>();
    static {
        PAGE_ACTIONS.put("recent", 2);       PAGE_NAMES.put("recent", "最近播放");
        PAGE_ACTIONS.put("local", 3);        PAGE_NAMES.put("local", "本地音乐");
        PAGE_ACTIONS.put("favorite", 4);     PAGE_NAMES.put("favorite", "收藏");
        PAGE_ACTIONS.put("car_playlist", 5); PAGE_NAMES.put("car_playlist", "车载歌单");
        PAGE_ACTIONS.put("radio", 6);        PAGE_NAMES.put("radio", "音乐电台");
        PAGE_ACTIONS.put("rank", 7);         PAGE_NAMES.put("rank", "排行榜");
        PAGE_ACTIONS.put("my_playlist", 9);  PAGE_NAMES.put("my_playlist", "自建歌单");
    }
    private final Context context;
    private volatile boolean cancelled;
    MusicExecutor(Context context) { this.context = context; }
    void cancel() { cancelled = true; }

    Outcome execute(GatewayClient.Result action) throws Exception {
        if (cancelled) return new Outcome(false, "已取消。");
        switch (action.type) {
            case "music.search":
            case "music.search_play":
                return search(action);
            case "music.open":
                launch(0, null);
                return new Outcome(false, "已打开QQ音乐。");
            case "music.close":
                launch(1, null);
                return new Outcome(false, "正在关闭QQ音乐。");
            case "music.open_page":
                return openPage(action);
            default:
                return mediaControl(action);
        }
    }

    private Outcome search(GatewayClient.Result action) throws Exception {
        boolean play = action.type.equals("music.search_play");
        String beforeKey = play ? currentTrackKey() : "";
        if (play) {
            refreshScene();
            if (cancelled) return new Outcome(false, "已取消。");
        }
        String query = Base64.encodeToString(action.query.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        if (cancelled) return new Outcome("failed", "已取消。");
        Intent intent = new Intent("com.tencent.qqmusiccar.action").setClassName(QQ, DISPATCHER)
                .putExtra("action", 8).putExtra("search_key", query).putExtra("m1", play)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        if (!play) return new Outcome(false, "已打开搜索结果。");
        long waitStart = SystemClock.elapsedRealtime();
        long until = waitStart + 20000;
        while (!cancelled && SystemClock.elapsedRealtime() < until) {
            MediaController controller = controller();
            String failure = playbackError(controller);
            if (failure != null) return new Outcome("failed", failure);
            if (playing(controller)) {
                String title = title(controller.getMetadata());
                String artist = artist(controller.getMetadata());
                String key = TrackMatcher.trackKey(title, artist);
                TrackMatcher.Result match = TrackMatcher.match(action.query, title, artist);
                // 严格匹配（歌名完整命中）可立即确认；宽松匹配必须等曲目真正变化，
                // 避免旧歌曲短暂恢复时被误报为新请求成功。
                if (match.matched && (match.strict || !key.equals(beforeKey))) {
                    return new Outcome(true, "正在播放" + title + "。");
                }
                // QQ 刷新场景和异步搜索期间可能继续暴露旧歌或中间曲目。
                // 不匹配时继续等待完整窗口，不能把暂态元数据当成点歌结果。
            }
            Thread.sleep(300);
        }
        if (cancelled) return new Outcome(false, "已取消。");
        return new Outcome(false, "点歌指令已发送，暂时未能确认播放结果，请查看QQ音乐。");
    }

    private Outcome openPage(GatewayClient.Result action) throws Exception {
        Integer code = PAGE_ACTIONS.get(action.page);
        String name = PAGE_NAMES.get(action.page);
        if (code == null || name == null) return new Outcome(false, "不支持该音乐操作。");
        if (!action.autoplay) {
            launch(code, false);
            return new Outcome(false, "已打开" + name + "。");
        }
        refreshScene();
        if (cancelled) return new Outcome(false, "已取消。");
        launch(code, true);
        long until = SystemClock.elapsedRealtime() + 10000;
        while (!cancelled && SystemClock.elapsedRealtime() < until) {
            MediaController controller = controller();
            String failure = playbackError(controller);
            if (failure != null) return new Outcome("failed", failure);
            if (playing(controller)) return new Outcome(true, "正在播放" + title(controller.getMetadata()) + "。");
            Thread.sleep(300);
        }
        return new Outcome(false, "已打开" + name + "，没有检测到播放，请确认。");
    }

    private Outcome mediaControl(GatewayClient.Result action) throws Exception {
        MediaController controller = controller();
        if (controller == null) return new Outcome("failed", "请先打开QQ音乐，再试一次。");
        String before = title(controller.getMetadata());
        if (cancelled) return new Outcome("failed", "已取消。");
        switch (action.type) {
            case "music.pause": controller.getTransportControls().pause(); break;
            case "music.resume": controller.getTransportControls().play(); break;
            case "music.next": controller.getTransportControls().skipToNext(); controller.getTransportControls().play(); break;
            case "music.previous": controller.getTransportControls().skipToPrevious(); controller.getTransportControls().play(); break;
            default: return new Outcome(false, "不支持该音乐操作。");
        }
        long until = SystemClock.elapsedRealtime() + 6000;
        while (!cancelled && SystemClock.elapsedRealtime() < until) {
            if (action.type.equals("music.pause")) {
                PlaybackState state = controller.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PAUSED) return new Outcome(true, "已暂停。");
            } else if (playing(controller)) {
                if (action.type.equals("music.resume")) return new Outcome(true, "继续播放。");
                if (!before.equals(title(controller.getMetadata()))) return new Outcome(true, "正在播放" + title(controller.getMetadata()) + "。");
            }
            Thread.sleep(200);
        }
        return new Outcome(false, "已发送音乐指令。");
    }

    // QQ 3.16 can retain a stale global scene after CLEAR_TASK.
    // Its own scene-switch entry rebuilds the UI without killing it.
    private void refreshScene() throws Exception {
        if (cancelled) return;
        context.startActivity(new Intent().setClassName(QQ, STARTER)
                .putExtra("KEY_SWITCH_APP_SCENE", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Thread.sleep(450);
    }

    // 官方协议 action=0~9，mb 固定 true：返回键回 launcher（车机无界面助手场景）。
    private void launch(int code, Boolean m1) {
        if (cancelled) return;
        Intent intent = new Intent("com.tencent.qqmusiccar.action").setClassName(QQ, DISPATCHER)
                .putExtra("action", code).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (m1 != null) intent.putExtra("m1", m1.booleanValue()).putExtra("mb", true);
        context.startActivity(intent);
    }

    private String currentTrackKey() {
        MediaController controller = controller();
        // 录音前已暂停音乐，暂停会话中的旧歌元数据仍须作为基线。
        if (controller == null) return "";
        return TrackMatcher.trackKey(title(controller.getMetadata()), artist(controller.getMetadata()));
    }

    private MediaController controller() {
        try {
            MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> controllers = manager.getActiveSessions(new ComponentName(context, MediaAccessService.class));
            for (MediaController controller : controllers) if (QQ.equals(controller.getPackageName())) return controller;
        } catch (SecurityException ignored) { }
        return null;
    }
    private static boolean playing(MediaController controller) {
        if (controller == null) return false;
        PlaybackState state = controller.getPlaybackState();
        return state != null && state.getState() == PlaybackState.STATE_PLAYING;
    }
    // QQ 无法播放（常见为版权限制）时会话会进入 STATE_ERROR；只报告检测到的错误，不猜测结果。
    private static String playbackError(MediaController controller) {
        if (controller == null) return null;
        PlaybackState state = controller.getPlaybackState();
        if (state == null || state.getState() != PlaybackState.STATE_ERROR) return null;
        CharSequence detail = state.getErrorMessage();
        if (detail != null && detail.toString().contains("版权")) {
            return "这首歌因版权原因暂时无法播放，请换一首。";
        }
        return "这首歌暂时无法播放，请换一首试试。";
    }
    private static String title(MediaMetadata data) {
        if (data == null) return "";
        String value = data.getString(MediaMetadata.METADATA_KEY_TITLE);
        return value == null ? "" : value;
    }
    private static String artist(MediaMetadata data) {
        if (data == null) return "";
        String value = data.getString(MediaMetadata.METADATA_KEY_ARTIST);
        return value == null ? "" : value;
    }
}
