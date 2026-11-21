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
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

final class MusicExecutor {
    static final class Outcome {
        final boolean confirmed;
        final String message;
        Outcome(boolean confirmed, String message) { this.confirmed = confirmed; this.message = message; }
    }
    private static final String QQ = "com.tencent.qqmusiccar";
    private final Context context;
    private volatile boolean cancelled;
    MusicExecutor(Context context) { this.context = context; }
    void cancel() { cancelled = true; }

    Outcome execute(GatewayClient.Result action) throws Exception {
        if (cancelled) return new Outcome(false, "已取消。");
        if (action.type.equals("music.search") || action.type.equals("music.search_play")) {
            String query = Base64.encodeToString(action.query.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            boolean play = action.type.equals("music.search_play");
            if (play) {
                // QQ 3.16 can retain a stale global scene after CLEAR_TASK.
                // Its own scene-switch entry rebuilds the UI without killing it.
                context.startActivity(new Intent().setClassName(QQ, QQ + ".app.activity.AppStarterActivity")
                        .putExtra("KEY_SWITCH_APP_SCENE", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Thread.sleep(450);
                if (cancelled) return new Outcome(false, "已取消。");
            }
            Intent intent = new Intent("com.tencent.qqmusiccar.action").setClassName(QQ, QQ + ".app.activity.DispacherActivityForThird")
                    .putExtra("action", 8).putExtra("search_key", query).putExtra("m1", play)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            if (action.type.equals("music.search")) return new Outcome(false, "已打开搜索结果。");
            long until = SystemClock.elapsedRealtime() + 20000;
            while (!cancelled && SystemClock.elapsedRealtime() < until) {
                MediaController controller = controller();
                if (playing(controller) && matches(action.query, controller.getMetadata())) {
                    return new Outcome(true, "正在播放" + title(controller) + "。");
                }
                Thread.sleep(300);
            }
            return new Outcome(false, "已打开QQ音乐，请确认播放结果。");
        }
        MediaController controller = controller();
        if (controller == null) return new Outcome(false, "请先打开QQ音乐，再试一次。");
        String before = title(controller);
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
                if (!before.equals(title(controller))) return new Outcome(true, "正在播放" + title(controller) + "。");
            }
            Thread.sleep(200);
        }
        return new Outcome(false, "已发送音乐指令。");
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
    private static String title(MediaController controller) {
        MediaMetadata data = controller.getMetadata();
        if (data == null) return "";
        String value = data.getString(MediaMetadata.METADATA_KEY_TITLE);
        return value == null ? "" : value;
    }
    private static boolean matches(String query, MediaMetadata metadata) {
        if (metadata == null) return false;
        String title = normalize(metadata.getString(MediaMetadata.METADATA_KEY_TITLE));
        String artist = normalize(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST));
        String normalized = normalize(query);
        if (normalized.isEmpty()) return false;
        String[] words = query.trim().split("\\s+");
        if (words.length > 1) {
            for (String word : words) if (!(title + artist).contains(normalize(word))) return false;
            return true;
        }
        return normalized.equals(title) || normalized.equals(artist)
                || (!title.isEmpty() && !artist.isEmpty() && normalized.contains(title) && normalized.contains(artist));
    }
    private static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}]", "");
    }
}
