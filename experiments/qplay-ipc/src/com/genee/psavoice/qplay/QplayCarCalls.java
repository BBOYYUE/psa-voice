package com.genee.psavoice.qplay;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import com.tencent.qqmusic.third.api.contract.CommonCmd;
import com.tencent.qqmusic.third.api.contract.Data;
import com.tencent.qqmusic.third.api.contract.ErrorCodes;
import com.tencent.qqmusic.third.api.contract.Events;
import com.tencent.qqmusic.third.api.contract.IQQMusicApi;
import com.tencent.qqmusic.third.api.contract.IQQMusicApiCallback;
import com.tencent.qqmusic.third.api.contract.IQQMusicApiEventListener;
import com.tencent.qqmusic.third.api.contract.Keys;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Offline-compiled integration preparation, excluded from the production app.
 * Caller owns binding, authorization, worker-thread dispatch, timeouts and lifecycle.
 * No method is invoked automatically. Bundle payloads remain intact until real
 * car responses establish their concrete data types and pagination behavior.
 */
public final class QplayCarCalls {
    private final IQQMusicApi api;
    public QplayCarCalls(IQQMusicApi api) {
        if (api == null) throw new IllegalArgumentException("QQ IPC is not bound");
        this.api = api;
    }
    public static Intent serviceIntent() {
        return new Intent("com.tencent.qqmusiccar.third.api.QQMusicApiService")
                .setPackage("com.tencent.qqmusiccar");
    }
    public Bundle hello() throws RemoteException {
        CommonCmd.init(CommonCmd.AIDL_PLATFORM_TYPE_CAR);
        Bundle args = new Bundle();
        args.putInt(Keys.API_PARAM_KEY_SDK_VERSION, CommonCmd.SDK_VERSION);
        args.putString(Keys.API_PARAM_KEY_PLATFORM_TYPE, CommonCmd.AIDL_PLATFORM_TYPE_CAR);
        return api.execute("hi", args);
    }
    public void searchSongs(String keyword, boolean firstPage, IQQMusicApiCallback callback) throws RemoteException {
        search(keyword, Data.SearchType.SEARCH_TYPE_SONG, firstPage, callback);
    }
    public void searchFolders(String keyword, boolean firstPage, IQQMusicApiCallback callback) throws RemoteException {
        search(keyword, Data.SearchType.SEARCH_TYPE_FOLDER, firstPage, callback);
    }
    private void search(String keyword, int type, boolean firstPage, IQQMusicApiCallback callback) throws RemoteException {
        Bundle args = new Bundle();
        args.putString("keyword", nonempty(keyword));
        args.putInt("searchType", type);
        args.putBoolean("firstPage", firstPage);
        api.executeAsync("search", args, callback);
    }
    public void playSongIds(List<String> ids, int index, IQQMusicApiCallback callback) throws RemoteException {
        ArrayList<String> copy = checkedIds(ids);
        if (index < 0 || index >= copy.size()) throw new IllegalArgumentException("Invalid song index");
        Bundle args = new Bundle();
        args.putStringArrayList("songIdList", copy);
        args.putInt("index", index);
        api.executeAsync("playSongIdAtIndex", args, callback);
    }
    public void favourite(List<String> mids, boolean add, IQQMusicApiCallback callback) throws RemoteException {
        Bundle args = new Bundle();
        args.putStringArrayList("midList", checkedIds(mids));
        api.executeAsync(add ? "addToFavourite" : "removeFromFavourite", args, callback);
    }
    public Bundle currentSong() throws RemoteException { return api.execute("getCurrentSong", null); }
    public Bundle playbackState() throws RemoteException { return api.execute("getPlaybackState", null); }
    public Bundle playMode() throws RemoteException { return api.execute("getPlayMode", null); }
    public Bundle setPlayMode(int mode) throws RemoteException {
        if (mode != Data.PlayMode.PLAY_MODE_REPEAT_LIST && mode != Data.PlayMode.PLAY_MODE_REPEAT_ONE
                && mode != Data.PlayMode.PLAY_MODE_SHUFFLE) throw new IllegalArgumentException("Invalid play mode");
        Bundle args = new Bundle();
        args.putInt("playMode", mode);
        return api.execute("setPlayModeWithRet", args);
    }
    // Keep the same listener instance for registration and unregistration.
    public Bundle register(IQQMusicApiEventListener listener) throws RemoteException {
        return api.registerEventListener(events(), listener);
    }
    public Bundle unregister(IQQMusicApiEventListener listener) throws RemoteException {
        return api.unregisterEventListener(events(), listener);
    }
    private static ArrayList<String> events() {
        return new ArrayList<>(Arrays.asList(Events.API_EVENT_PLAY_STATE_CHANGED,
                Events.API_EVENT_PLAY_SONG_CHANGED, Events.API_EVENT_PLAY_MODE_CHANGED,
                Events.API_EVENT_SONG_FAVORITE_STATE_CHANGED));
    }
    public static int resultCode(Bundle result) {
        return result != null && result.containsKey(Keys.API_RETURN_KEY_CODE)
                ? result.getInt(Keys.API_RETURN_KEY_CODE) : ErrorCodes.ERROR_API_UNKNOWN;
    }
    private static String nonempty(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Empty value");
        return value.trim();
    }
    private static ArrayList<String> checkedIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("Empty song list");
        ArrayList<String> copy = new ArrayList<>();
        for (String id : ids) copy.add(nonempty(id));
        return copy;
    }
}
