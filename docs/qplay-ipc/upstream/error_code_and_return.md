
# 错误码&返回值说明

## QQ音乐API错误码 ErrorCodes

|QQ音乐API错误码|值|             说明             |
| :-----: | :-----: |:--------------------------:|
|ERROR\_OK|0|            没有错误            |
|ERROR\_API\_UNKNOWN|1|            未知错误            |
|ERROR\_API\_NOT\_INITIALIZED|2|       QQ音乐的API接口未初始化       |
|ERROR\_API\_UNSUPPORTED\_ACTION|3|           不支持的操作           |
|ERROR\_API\_ILLEGAL\_ARGUMENT|4|            参数错误            |
|ERROR\_API\_NO\_PERMISSION|5|       应用没有调用API的权限。        |
|ERROR\_FAILED\_TO\_QUERY\_FROM\_MID|6|       从MID请求歌曲信息时失败        |
|ERROR\_NEED\_USER\_AUTHENTICATION|7|           需要用户登录           |
|ERROR\_API\_INTERRUPTED|8|           线程中断错误           |
|ERROR\_SERVICE\_INITIATING\_TIMEOUT|9|           服务超时启动           |
|ERROR\_FAILED\_TO\_QUERY\_FROM\_SONG\_ID|10|      从SongId请求歌曲信息时失败      |
|ERROR\_SERVICE\_NOT\_READY|11|           服务没有就绪           |
|ERROR\_PRIVACY\_POLICY\_NOT\_AGREED|12|          隐私协议未同意           |
|ERROR\_UPDATE\_DATABASE\_ERROR|13|          内部数据更新失败          |
|ERROR\_PLAY\_UNKNOWN|100|         播放错误：未知错误          |
|ERROR\_PLAY\_NO\_MEDIA|101|       播放错误：没有可播放的媒体        |
|ERROR\_PLAY\_PLAYER\_ERROR|102|         播放错误：播放器错误         |
|ERROR\_PLAY\_NO\_PERMISSION|103|         播放错误：没有权限          |
|ERROR\_PLAY\_OVER\_SEA\_FORBIDDEN|104|         播放错误：海外受限          |
|ERROR\_PLAY\_NO\_STORAGE|105|        播放错误：存储空间不够         |
|ERROR\_PLAY\_NO\_NETWORK|106|         播放错误：没有网络          |
|ERROR\_PLAY\_ROAMING\_DATA|107|        播放错误：打开了流量提醒        |
|ERROR\_PLAY\_LIST\_TOO\_LONG|108|         播放错误：列表过长          |
|ERROR\_PLAY\_TOO\_OFTEN|109|         播放错误：调用太频繁         |
|ERROR\_GET\_DATA\_ARGUMENT|200|         获取数据：参数错误          |
|ERROR\_GET\_DATA\_NETWORK|201|         获取数据：网络错误          |
|ERROR\_GET\_DATA\_INNER|202|         获取数据：内部错误          |
|ERROR\_AUTH\_FAILED|203| 授权错误，重新调用requestAuth接口进行鉴权 |
|ERROR\_INVALID\_APP|204|           非法APP            |
|ERROR\_INVOKE\_API|205|     调用API错误（调用了错误的API）     |
|ERROR\_VOICE\_NOT\_SUPPORTED|300|          语音助手不支持           |
|ERROR\_PLAY\_TRY\_PLAY|1030|       播放错误：没有权限，试听相关       |
|ERROR\_PLAY\_GREEN\_NO\_PERMISSION|1031|       播放错误：没有权限,绿钻相关       |
|ERROR\_PLAY\_PAY\_SONG\_NO\_PERMISSION|1032|    播放错误：没有权限,付费歌曲或者数字专辑    |
|ERROR\_PLAY\_RIGHT\_NO\_PERMISSION|1033|       播放错误：没有权限,无版权        |

## QQ音乐API二级错误码 SubErrorCodes

|错误码|值|说明|
| :-----: | :-----: | :-----: |
|SUB\_ERROR\_OK|0|无错误|
|SUB\_ERROR\_INVALID\_CALL|1|无效的调用（比如取消收藏一首未收藏的歌曲）|
|SUB\_ERROR\_UPDATE\_DATABASE\_ERROR|2|内部数据更新失败|
|3SUB\_ERROR\_SONG\_ID\_TYPE\_ERROR|3|传入的songId参数格式有误|

## 事件定义 Events

| 事件定义                                  | 值                                    | 说明         | 从以下版本开始支持            |
|:--------------------------------------|:----------------------------------------|:-----------|:-------------------------------|
| API_EVENT_AUDIO_FOCUS_CHANGED         | "API_EVENT_AUDIO_FOCUS_CHANGED"         | 音频焦点变更     |phone 10.17.5|
| API_EVENT_LOGIN_STATE_CHANGED         | "API_EVENT_LOGIN_STATE_CHANGED"         | 登录状态变更     |phone 10.17.5|
| API_EVENT_MUSIC_BLOCKED_NO_WIFI       | "API_EVENT_MUSIC_BLOCKED_NO_WIFI"       | 音乐被中断。     |phone 9.3.5|
| API_EVENT_MUSIC_BLOCKED_TIMER         | "API_EVENT_MUSIC_BLOCKED_TIMER"         | 音乐被中断。     |phone 9.3.5|
| API_EVENT_MUSIC_BLOCKED_WIFI_ONLY     | "API_EVENT_MUSIC_BLOCKED_WIFI_ONLY"     | 音乐被中断。     |phone 9.3.5|
| API_EVENT_PLAY_LIST_CHANGED           | "API_EVENT_PLAY_LIST_CHANGED"           | 播放列表发生变化   |phone 8.5|
| API_EVENT_PLAY_MODE_CHANGED           | "API_EVENT_PLAY_MODE_CHANGED"           | 播放模式发生变化   |phone 8.8.5|
| API_EVENT_PLAY_SONG_CHANGED           | "API_EVENT_PLAY_SONG_CHANGED"           | 播放歌曲发生改变   |phone 8.2|
| API_EVENT_PLAY_STATE_CHANGED          | "API_EVENT_PLAY_STATE_CHANGED"          | 播放状态发生改变。  |phone 8.2|
| API_EVENT_SONG_FAVORITE_STATE_CHANGED | "API_EVENT_SONG_FAVORITE_STATE_CHANGED" | 收藏状态发生变化   |phone 8.8.5|
| API_EVENT_SONG_PLAY_ERROR             | "API_EVENT_SONG_PLAY_ERROR"             | 播放错误       |TV 5.7 只针对TV平台|
| API_EVENT_PLAY_START                  | "API_EVENT_PLAY_START"                  | 开始播放         ||

## QPlay IPC (Android)接口调用的返回 Keys

| QPlay IPC (Android)接口调用的返回         | 值                   | 说明                     |
|:-----------------------------------|:--------------------|:-----------------------|
| API_EVENT_KEY_IS_FAVORITE          | "isFavorite"        | 是否已收藏                  |
| API_EVENT_KEY_PLAY_LIST_SIZE       | "playListSize"      | 播放歌曲列表大小               |
| API_EVENT_KEY_PLAY_MODE            | "playMode"          | 播放模式                   |
| API_EVENT_KEY_PLAY_SONG            | "playSong"          | 播放歌曲                   |
| API_EVENT_KEY_PLAY_STATE           | "playState"         | 播放状态                   |
| API_EVENT_KEY_SONG                 | "song"              | 歌曲                     |
| API_PARAM_KEY_ACTION               | "action"            | action                 |
| API_PARAM_KEY_FROM                 | "from"              | from                   |
| API_PARAM_KEY_M0                   | "m0"                | m0                     |
| API_PARAM_KEY_M1                   | "m1"                | m1                     |
| API_PARAM_KEY_M2                   | "m2"                | m2                     |
| API_PARAM_KEY_MB                   | "mb"                | mb                     |
| API_PARAM_KEY_MEDIA_INFO           | "mediaInfo"         | mediaInfo              |
| API_PARAM_KEY_PCM_FILE_DESCRIPTOR  | "pcmFileDescriptor" | pcmFileDescriptor      |
| API_PARAM_KEY_PLATFORM_TYPE        | "platformType"      | platformType           |
| API_PARAM_KEY_PULL_FROM            | "pull_from"         | pull_from              |
| API_PARAM_KEY_SDK_EXPIRETIME       | "expireTime"        | expireTime             |
| API_PARAM_KEY_SDK_VERSION          | "sdkVersionCode"    | sdkVersion             |
| API_PARAM_KEY_SEARCH_KEY           | "search_key"        | search_key             |
| API_RETURN_KEY_APP_ID              | "appId"             | 应用ID                   |
| API_RETURN_KEY_AUDIO_FOCUS_CHANGED | "focusChange"       | 焦点变化类型                 |
| API_RETURN_KEY_CALLBACK_URL        | "callbackUrl"       | callbackUrl            |
| API_RETURN_KEY_CODE                | "code"              | 操作的返回码                 |
| API_RETURN_KEY_DATA                | "data"              | 操作返回值，可选               |
| API_RETURN_KEY_ENCRYPT_STRING      | "encryptString"     | 加密字符串                  |
| API_RETURN_KEY_ERROR               | "error"             | 如果操作发生了错误，此值记录了简单的错误信息 |
| API_RETURN_KEY_HAS_MORE            | "hasMore"           | 是否还有更多数据               |
| API_RETURN_KEY_IS_LOGIN            | "isLogin"           | 登录态                    |
| API_RETURN_KEY_NONCE               | "nonce"             | 加密种子                   |
| API_RETURN_KEY_OPEN_ID             | "openId"            | OpenID                 |
| API_RETURN_KEY_OPEN_TOKEN          | "openToken"         | Open Token             |
| API_RETURN_KEY_SIGN                | "sign"              | 签名                     |
| API_RETURN_KEY_VERSION             | "version"           | QQ音乐的版本号               |

## 播放状态定义 PlayState

| 播放状态定义            | 值   | 说明             |
|:------------------|:----|:---------------|
| IDLE              | 0   | 空闲             |
| INITIALIZED       | 1   | 已初始化           |
| PREPARED          | 2   | 准备完成，可以开始播放    |
| PREPARING         | 3   | 正在准备           |
| STARTED           | 4   | 正在播放           |
| PAUSED            | 5   | 暂停             |
| STOPPED           | 6   | 停止             |
| PLAYBACKCOMPLETED | 7   | 播放顺利完成         |
| END               | 8   | 播放器被释放，需要reset |
| ERROR             | 9   | 播放错误，需要reset   |
| BUFFERING         | 10  | 正在缓冲           |
| PAUSING           | 11  | 正在暂停           |
| STOPPING          | 12  | 正在停止           |

## 目录类型 Data.FolderType

| 目录类型                            | 值   | 说明                                   |
|:--------------------------------|:----|:-------------------------------------|
| ROOT                            | 0   | 根目录                                  |
| MY_FOLDER                       | 1   | 我的歌单列表,二级目录是MY_FOLDER_SONG_LIST      |
| RANK                            | 2   | 排行榜列表,二级目录是排行榜RANK_SONG_LIST         |
| RECOMMEND_FOLDER                | 3   | 推荐歌单                                 |
| RADIO                           | 4   | 电台列表, 二级目录是RADIO_SONG_LIST           |
| MY_FAV_FOLDER                   | 5   | 收藏的歌单，二级目录是PLAYLIST_FOLDER_SONG_LIST |
| AI_FOLDER                       | 6   | ai 歌单，二级目录是PLAYLIST_FOLDER_SONG_LIST |
| LOCAL_SONG_LIST                 | 100 | 本地歌曲                                 |
| MY_FOLDER_SONG_LIST             | 101 | 我的歌单下的歌曲列表                           |
| RANK_SONG_LIST                  | 102 | 排行榜下的歌曲列表                            |
| RECOMMEND_FOLDER_SONG_LIST      | 103 | 推荐歌单的歌曲列表                            |
| PERSONAL_RADIO_FOLDER_SONG_LIST | 104 | 个性电台的歌曲列表                            |
| PLAYLIST_FOLDER_SONG_LIST       | 105 | 歌单的歌曲列表                              |
| ALBUM_FOLDER_SONG_LIST          | 106 | 专辑的歌曲列表                              |
| RADIO_SONG_LIST                 | 107 | 电台的歌曲列表                              |
| DAY30_SONG_LIST                 | 108 | 每日30首的歌曲列表                           |
| MYFAV_FOLDER_SONG_LIST          | 201 | 我喜欢歌曲列表                              |
| RECENTPLAY_FOLDER_SONG_LIST     | 202 | 最近播放歌曲列表                             |

## 搜索类型 Data.SearchType

| 搜索类型                     | 值   | 说明     |
|:-------------------------|:----|:-------|
| SEARCH_TYPE_SONG         | 0   | 歌曲搜索   |
| SEARCH_TYPE_SINGER       | 1   | 歌手搜索   |
| SEARCH_TYPE_ALBUM        | 2   | 专辑搜索   |
| SEARCH_TYPE_FOLDER       | 3   | 歌单搜索   |
| SEARCH_TYPE_MV           | 4   | MV搜索   |
| SEARCH_TYPE_LYRIC        | 7   | 歌词搜索   |
| SEARCH_TYPE_USER         | 8   | 用户搜索   |
| SEARCH_TYPE_SIMILAR_SONG | 9   | 歌曲推荐搜索 |
| SEARCH_TYPE_MIX          | 100 | 综合     |

## 播放模式 Data.SearchType

| 播放模式                  | 值   | 说明   |
|:----------------------|:----|:-----|
| PLAY_MODE_REPEAT_LIST | 0   | 列表循环 |
| PLAY_MODE_REPEAT_ONE  | 1   | 单曲循环 |
| PLAY_MODE_SHUFFLE     | 2   | 随机播放 |

## 登陆状态 Data.LoginState

| 登陆状态         | 值   | 说明  |
|:-------------|:----|:----|
| NOT_LOGIN    | 0   | 无登录 |
| STRONG_LOGIN | 2   | 强登录 |
| WEAK_LOGIN   | 1   | 弱登录 |

## 运行在前台或后台 Data.AppForeground

| 登陆状态           | 值   | 说明      |
|:---------------|:----|:--------|
| APP_BACKGROUND | 0   | QQ音乐在后台 |
| APP_FOREGROUND | 1   | QQ音乐在前台 |
