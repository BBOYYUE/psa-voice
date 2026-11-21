
# 接口类
## 查看版本号
### 功能说明
无实际作用，应用可调用此方法查看返回中的版本号。

### 接口函数
```java
void hi();
```
### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|1.9.5|
|tv|5.5|
|lite|3.3|

### 
## 启动QQ音乐主界面
### 功能说明
启动QQ音乐主界面

### 接口函数
```java
void openQQMusic();
```
### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|1.9.5|
|tv|5.5|
|lite|null|

### 
## 从头开始播放指定的歌曲mid列表
### 功能说明
从头开始播放指定的歌曲列表。

### 接口函数
```java
void playSongMid(
    java.util.List<java.lang.String> midList,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| ----- | ----- | ----- | ----- |
|midList|List<String>|是|歌曲的mid列表|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|null|
|tv|6.9|
|lite|3.3|

### 
## 从头开始播放指定的歌曲id列表
### 功能说明
从头开始播放指定的歌曲列表。

### 接口函数
```java
void playSongId(
    java.util.List<java.lang.String> songIdList,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| ----- | ----- | ----- | ----- |
|songIdList|List<String>|是|歌曲的songId列表|
|callback|IQQMusicApiCallback|是|接口回调|



### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.5|
|car|1.12.0|
|tv|5.7|
|lite|3.3|

### 
## 从索引开始播放指定的歌曲mid列表
### 功能说明
从索引开始播放指定的歌曲列表。

### 接口函数
```java
void playSongMidAtIndex(
    java.util.List<java.lang.String> midList,
    int index,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| ----- | ----- | ----- | ----- |
|midList|List<String>|是|歌曲的mid列表|
|index|int|是|开始播放时的索引|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.8.5|
|car|null|
|tv|6.9|
|lite |3.3|

### 
## 从索引开始播放指定的歌曲id列表
### 功能说明
从索引开始播放指定的歌曲列表。

### 接口函数
```java
void playSongIdAtIndex(
    java.util.List<java.lang.String> songIdList,
    int index,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| ----- | ----- | ----- | ----- |
|songIdList|List<String>|是|歌曲的songId列表|
|index|int|是|开始播放时的索引|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.8.5|
|car|1.12.0|
|tv|5.7|
|lite |3.3|

### 
## 播放本地路径下的歌曲列表
### 功能说明
播放本地路径下的歌曲。

### 接口函数
```java
void playSongLocalPath(
    java.util.List<java.lang.String> pathList,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| ----- | ----- | ----- | ----- |
|pathList|List<String>|是|本地文件路径列表|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|null|
|tv|null|
|lite|null|

### 
## 播放当前歌曲
### 功能说明
播放当前歌曲。

### 接口函数
```java
int playMusic();
```
### 返回数据
|类型|含义|
| ----- | ----- |
|int|错误码, 参考ErrorCodes类|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|1.9.5|
|tv|5.5|
|lite|3.3|

### 
## 停止当前播放
### 功能说明
停止当前播放，如果不在播放状态，没有副作用。

### 接口函数
```java
int stopMusic();
```
### 返回数据
|类型|含义|
| ----- | ----- |
|int|错误码, 参考ErrorCodes类|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|1.9.5|
|tv|5.5|
|lite|3.3|

### 
## 暂停当前播放
### 功能说明
暂停当前播放，如果不在播放状态，没有副作用。

### 接口函数
```java
int pauseMusic();
```
### 返回数据
|类型|含义|
| ----- | ----- |
|int|错误码, 参考ErrorCodes类|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|1.9.5|
|tv|5.5|
|lite|3.3|

### 
## 恢复当前播放
### 功能说明
恢复当前播放，如果已在播放状态，没有副作用。如果没有任何播放歌曲，将没有任何效果。

### 接口函数
```java
int resumeMusic();
```
### 返回数据
|类型|含义|
| ----- | ----- |
|int|错误码, 参考ErrorCodes类|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|1.9.5|
|tv|5.5|
|lite|3.3|

### 
## 播放下一首
### 功能说明
播放下一首。

### 接口函数
```java
int skipToNext();
```
### 返回数据
|类型|含义|
| ----- | ----- |
|int|错误码, 参考ErrorCodes类|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|1.9.5|
|tv|5.5|
|lite|3.3|

### 
## 播放上一首
### 功能说明
播放上一首。

### 接口函数
```java
int skipToPrevious();
```
### 返回数据
|类型|含义|
| ----- | ----- |
|int|错误码, 参考ErrorCodes类|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|1.9.5|
|tv|5.5|
|lite|3.3|

### 
## 获取当前播放状态
### 功能说明
获取当前播放状态。

### 接口函数
```java
int getPlaybackState();
```
### 返回数据
|类型|含义|
| ----- | ----- |
|int|当前播放状态|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|1.9.5|
|tv|5.5|
|lite|3.3|

### 
## 获取当前播放歌曲的信息
### 功能说明
获取当前播放歌曲的信息。

### 接口函数
```java
Data.Song getCurrentSong();
```
### 返回数据
|类型|含义|
| ----- | ----- |
|Data.Song|当前播放歌曲的信息，可能为空|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|1.9.5|
|tv|5.5|
|lite|3.3|

### 
## 将指定歌曲添加到当前登录用户的收藏中
### 功能说明
将指定歌曲添加到当前登录用户的收藏中。

### 接口函数
```java
void addToFavourite(
    java.util.List<java.lang.String> midList,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| ----- | ----- | ----- | ----- |
|midList|List<String>|是|歌曲的mid列表|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|2.0.3|
|tv|5.6|
|lite|3.3|

### 
## 将指定歌曲从当前登录用户的收藏中移除
### 功能说明
将指定歌曲从当前登录用户的收藏中移除。

### 接口函数
```java
void removeFromFavourite(
    java.util.List<java.lang.String> midList,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| ----- | ----- | ----- | ----- |
|midList|List<String>|是|歌曲的mid列表|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|2.0.3|
|tv|5.6|
|lite|3.3|

### 
## 将本地路径中的指定歌曲添加到当前登录用户的收藏中
### 功能说明
将本地路径中的指定歌曲添加到当前登录用户的收藏中。

### 接口函数
```java
void addLocalPathToFavourite(
    java.util.List<java.lang.String> localPathList,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| ----- | ----- | ----- | ----- |
|localPathList|List<String>|是|本地歌曲的文件路径列表|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|null|
|tv|null|
|lite|null|

### 
## 将本地路径中的指定歌曲从当前登录用户的收藏中移除
### 功能说明
将本地路径中的指定歌曲从当前登录用户的收藏中移除。

### 接口函数
```java
void removeLocalPathFromFavourite(
    java.util.List<java.lang.String> localPathList,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| ----- | ----- | ----- | ----- |
|localPathList|List<String>|是|本地歌曲的文件路径列表|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|null|
|tv|null|
|lite|null|

### 
## 是否为收藏的歌曲
### 功能说明
判断歌曲是否为当前登录用户收藏的歌曲。

### 接口函数
```java
void isFavouriteMid(
    java.util.List<java.lang.String> midList,
    java.util.List<java.lang.String> idList,
    java.util.List<java.lang.String> typeList,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| ----- | ----- | ----- | ----- |
|midList|List<String>|是|需要判断的歌曲mid列表|
|idList|List<String>|否|需要判断的歌曲id列表|
|typeList|List<String>|否|歌曲的类型列表，顺序需和idList保持一致|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|10.5.5|
|car|1.9.5|
|tv|5.5|
|lite|3.3|

### 
## 是否为收藏的本地路径下的歌曲
### 功能说明
判断歌曲是否为当前登录用户收藏的本地路径下歌曲。

### 接口函数
```java
void isFavouriteLocalPath(
    java.util.List<java.lang.String> localPathList,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| ----- | ----- | ----- | ----- |
|localPathList|List<String>|是|本地歌曲的文件路径列表|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|null|
|tv|null|
|lite|null|

### 
## 注册事件监听
### 功能说明
注册事件监听，支持的事件见Events。

### 接口函数
```java
void registerEventListener(
    java.util.List<java.lang.String> eventList,
    IQQMusicApiEventListener listener
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| ----- | ----- | ----- | ----- |
|eventList|List<String>|是|注册的事件列表|
|listener|IQQMusicApiEventListener|是|事件监听回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|1.9.5|
|tv|5.5|
|lite|3.3|

### 
## 取消注册事件监听
### 功能说明
取消注册事件监听。

### 接口函数
```java
void unregisterEventListener(
    java.util.List<java.lang.String> eventList,
    IQQMusicApiEventListener listener
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| ----- | ----- | ----- | ----- |
|eventList|List<String>|是|需要取消注册监听的事件列表|
|listener|IQQMusicApiEventListener|是|事件监听回调，需保持和注册的listener为同一对象|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.2|
|car|1.9.5|
|tv|5.5|
|lite|3.3|

### 
## 获取当前播放歌曲的总时长
### 功能说明
获取当前播放歌曲的总时长， 单位（ms）。

### 接口函数
```java
long getTotalTime();
```
### 返回数据
|类型|含义|
| ----- | ----- |
|long|歌曲总时长|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.5|
|car|1.9.5|
|tv|5.6|
|lite|3.3|

### 
## 获取当前播放歌曲的播放进度
### 功能说明
获取当前播放歌曲的播放进度， 单位（ms）。

### 接口函数
```java
long getCurrTime();
```
### 返回数据
|类型|含义|
| ----- | ----- |
|long|歌曲播放进度|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.5|
|car|1.9.5|
|tv|5.5|
|lite|3.3|



## 获取当前播放歌曲列表
### 功能说明
获取当前播放歌曲列表。

### 接口函数
```java
void getPlayList(
    int page,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| ----- | ----- | ----- | ----- |
|page|int|是|页码(每页最多包括1000首歌）|
|listener|IQQMusicApiEventListener|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.5|
|car|null|
|tv|5.6|
|lite|3.3|

### 
## 获取歌单、电台、排行榜等Folder数据
### 功能说明
获取歌单、电台、排行榜等Folder数据。

### 接口函数
```java
void getFolderList(
    java.lang.String folderId,
    int folderType,
    int page,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| ----- | ----- | ----- | ----- |
|folderId|String|是|目录Id|
|folderType|int|是|目录类型|
|page|int|是|页码|
|listener|IQQMusicApiEventListener|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.5|
|car|1.12.0|
|tv|5.6|
|lite|3.3|



## 获取Folder对应的歌曲列表
### 功能说明
获取Folder对应的歌曲列表

### 接口函数
```java
void getSongList(
    java.lang.String folderId,
    int folderType,
    int page,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|folderId|String|是|目录id|
|folderType|int|是|目录类型|
|page|int|是|页码|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.5|
|car|1.12.0|
|tv|5.6|
|lite|3.3|



## 搜索歌曲
### 功能说明
搜索歌曲；

当searchType为SEARCH\_TYPE\_SIMILAR\_SONG时，keyword为歌曲songId，firstPage无用;

### 接口函数
```java
void search(
    java.lang.String keyword,
    int searchType,
    boolean firstPage,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|keyword|String|是|关键词|
|searchType|int|是|搜索类型 Data.SearchType|
|firstPage|boolean|是|页码|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.8|
|car|1.9.5|
|tv|5.6|
|lite|3.3|



## 获取我喜欢目录ID
### 功能说明
获取我喜欢目录ID ,另外我喜欢的FolderType固定是FolderType.MY\_FOLDER\_SONG\_LIST

### 接口函数
```java
java.lang.String getFavouriteFolderId();
```
### 返回数据
|类型|含义|
| ----- | ----- |
|String|我喜欢目录Id|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.8.5|
|car|1.9.5|
|tv|5.6|
|lite|3.3|



## 获取播放模式
### 功能说明
获取当前播放模式。

### 接口函数
```java
int getPlayMode();
```
### 返回数据
|类型|含义|
| ----- | ----- |
|int|播放模式，定义在Data.PlayMode内|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.8.5|
|car|1.9.5|
|tv|5.5|
|lite|3.3|



## 设置播放模式
### 功能说明
设置播放模式。

### 接口函数
```java
void setPlayMode(int playMode);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|playMode|int|是|新播放模式,  参数含义参考Data.PlayMode类|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.8.5|
|car|1.9.5|
|tv|5.5|
|lite|3.3|



## 设置播放模式（带有返回值）
### 功能说明
设置播放模式，并返回设置成功后的播放模式或错误码。

### 接口函数
```java
int setPlayModeWithRet(int playMode);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|playMode|int|是|新播放模式,  参数含义参考Data.PlayMode类|

### 返回数据
|类型|含义|
| ----- | ----- |
|int|设置成功后的播放模式值或错误码，播放模式值参考Data.PlayMode类，错误码参考ErrorCodes|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|null|
|car|2.5.0|
|tv|9.0|
|lite|3.3|



## 设置是否从高潮处播放
### 功能说明
设置是否从高潮处播放。

### 接口函数
```java
void playFromChorus(boolean fromChorus);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|fromChorus|boolean|是|是否从高潮处播放|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|9.9|
|car|null|
|tv|null|
|lite|null|



## 请求授权
### 功能说明
请求授权。

### 接口函数
```java
void requestAuth(
    java.lang.String encryptString,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|encryptString|String|是|加密字符串,加密方式参考demo实现|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.9.6|
|car|1.9.5|
|tv|5.5|
|lite|3.3|



## 获取用户歌单
### 功能说明
获取用户歌单。

### 接口函数
```java
void getUserFolderList(
    java.lang.String openId,
    java.lang.String openToken,
    java.lang.String folderId,
    int folderType,
    int page,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|openId|String|是|通过requestAuth拿到的openID|
|openToken|String|是|通过requestAuth拿到的openToken|
|folderId|String|是|目录id|
|folderType|int|是|目录类型|
|page|int|是|页码|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.9.6|
|car|1.9.5|
|tv|5.5|
|lite|3.3|



## 获取用户Folder对应的歌曲列表
### 功能说明
获取用户Folder对应的歌曲列表。

### 接口函数
```java
void getUserSongList(
    java.lang.String openId,
    java.lang.String openToken,
    java.lang.String folderId,
    int folderType,
    int page,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|openId|String|是|通过requestAuth拿到的openID|
|openToken|String|是|通过requestAuth拿到的openToken|
|folderId|String|是|目录id|
|folderType|int|是|目录类型|
|page|int|是|页码|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|8.9.6|
|car|1.12.0|
|tv|5.9|
|lite|3.3|



## QQ音乐是否在前台
### 功能说明
判断QQ音乐是否在前台。

### 返回数据
|类型|含义|
| ----- | ----- |
|int|是否在前台，参考Data.AppForeground|

### 接口函数
```java
int isQQMusicForeground();
```
### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|null|
|car|1.9.5|
|tv|5.9|
|lite|null|



## 查询登录信息
### 功能说明
查询登录信息。

### 返回数据
|类型|含义|
| ----- | ----- |
|int|当前的登录状态，参考Data.LoginState|

### 接口函数
```java
int getLoginState();
```
### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|10.16|
|car|1.9.5|
|tv|5.5|
|lite|3.3|



## 快进播放
### 功能说明
快进播放

#### 接口函数
```java
int seekForward(long time);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|time|long|是|快进的时间(ms)|

### 返回数据
|类型|含义|
| ----- | ----- |
|int|phone: 无意义, car/tv:错误码， 参考ErrorCodes|

### 生效版本
|平台|生效版本|
| ----- | ----- |
|phone|10.16|
|car|1.9.5|
|tv|5.5|
|lite|3.3|



## 快退播放
### 功能说明
快退播放。

### 接口函数
```java
int seekBack(long time);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|time|long|是|快退的时间(ms)|

### 返回数据
|类型|含义|
| ----- | ----- |
|int|phone: 无意义, car/tv:错误码， 参考ErrorCodes|

### 生效版本
|平台|生效版本|
| :----- | :----- |
|phone|10.16|
|car|1.9.5|
|tv|5.5|
|lite|3.3|



## 获取歌词
### 功能说明
获取歌词。

### 接口函数
```java
void getLyric(
    long songId,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|songId|long|是|歌曲id|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| :----- | :----- |
|phone|null|
|car|1.12.0|
|tv|5.9|
|lite|3.3|



## 获取歌词
### 功能说明
获取歌词。

### 接口函数
```java
void getLyricWithId(
    java.lang.String songId,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|songId|String|是|歌曲id|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| :----- | :----- |
|phone|10.16|
|car|1.12.0|
|tv|null|
|lite|3.3|



## 获取歌词
### 功能说明
获取歌词，返回的歌曲格式为【开始时间】【结束时间】【歌词信息】/【开始时间】【歌词信息】，受needEndTime控制。

### 接口函数
```java
 void getLyricWithIdNew(java.lang.String songId,
                        boolean ignoreInvalidLyric,
                        boolean needEndTime,
                        IQQMusicApiCallback callback);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|songId|String|是|歌曲id|
|ignoreInvalidLyric|boolean|是|是否过滤非法的歌词，如无时间戳歌词。true -> 过滤 false -> 不过滤|
|needEndTime|boolean|是|是否需要结束时间|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| :----- | :----- |
|phone|13.12|
|car|null|
|tv|null|
|lite|3.3|



## 获取歌词
### 功能说明
获取歌词，返回的歌曲格式为【开始时间】【结束时间】【歌词信息】。

### 接口函数
```java
 void getLyricIncludeEndTime(java.lang.String songId,
                        IQQMusicApiCallback callback);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|songId|String|是|歌曲id|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| :----- | :----- |
|phone|13.12|
|car|null|
|tv|null|
|lite|3.3|



## 捷径类播放接口
### 功能说明
捷径类播放接口。

### 接口函数
```java
void voiceShortcut(
    java.lang.String intent,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|intent|String|是|捷径意图|
|callback|IQQMusicApiCallback|是|接口回调|

捷径意图的参数定义参考：QQ音乐语音接入文档

### 生效版本
|平台|生效版本|
| :----- | :----- |
|phone|9.7.5|
|car|null|
|tv|null|
|lite|3.3|



## 捷径类播放接口
### 功能说明
捷径类播放接口。

### 接口函数
```java
void voicePlay(
    java.lang.String query,
    java.util.List<java.lang.String> slotList,
    IQQMusicApiCallback callback
);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|query|String|是|语音具体指令|
|slotList|List<String>|是|语义槽位列表|
|callback|IQQMusicApiCallback|是|接口回调|

输入参数具体参考：QQ音乐语音接入文档

### 生效版本
|平台|生效版本|
| :----- | :----- |
|phone|9.7.5|
|car|null|
|tv|null|
|lite|3.3|



## 开启Pcm模式
### 功能说明
开启Pcm模式，会在callback中返回`mediaInfo`以及`pcmFileDescriptor`
`mediaInfo`为`Data.MediaInfo`类型，提供播放器初始化时使用的参数 `pcmFileDescriptor`为`ParcelFileDescriptor`类型，为pcm传输用的文件描述符。

### 接口函数
```java
void startPcmMode(IQQMusicApiCallback callback);
```
### 输入参数
|参数名称|类型|描述|
| :----- | :----- | :----- |
|callback|IQQMusicApiCallback|接口回调|

### 生效版本
|平台|生效版本|
| :----- | :----- |
|phone|10.11|
|car|null|
|tv|null|
|lite|null|



## 关闭Pcm模式
### 功能说明
关闭Pcm模式。

### 接口函数
```java
void stopPcmMode();
```
### 生效版本
|平台|生效版本|
| :----- | :----- |
|phone|10.11|
|car|null|
|tv|null|
|lite|null|



## 是否是QQ音乐新用户
### 功能说明
是否是QQ音乐新用户。

### 接口函数
```java
boolean isNewUser();
```
### 返回数据
|类型|描述|
| :----- | :----- |
|boolean|是否是QQ音乐新用户|

### 生效版本
|平台|生效版本|
| :----- | :----- |
|phone|10.16|
|car|null|
|tv|null|
|lite|null|



## 一键发起播放
### 功能说明
根据指定的目录ID，目录类型， 起播位置一键发起播放。

### 接口函数
```java
    void playFolderType(
            java.lang.String folderId,
            int folderType,
            int index,
            IQQMusicApiCallback callback
    );
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|folderId|String|是|目录Id|
|folderType|int|是|目录类型|
|index|int|是|起播位置|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| :----- | :----- |
|phone|NULL|
|car|2.0.0|
|tv|9.0|
|lite|3.3|



## 获得当前正在播放歌曲在列表中的索引
### 功能说明
获得当前正在播放歌曲在列表中的索引。

### 接口函数
```java
  int getCurrentSongIndexInList();
```
### 返回数据
|类型|描述|
| :----- | :----- |
|int|当前歌曲在播放列表中的索引|

### 生效版本
|平台|生效版本|
| :----- | :----- |
|phone|12.70|
|car|null|
|tv|null|
|lite|3.3|



## 快进到指定播放位置
### 功能说明
快进到指定播放位置。

### 接口函数
```java
long seek(long seekPos);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|seekPos|long|是|快进后的播放位置|

### 返回数据
|类型|描述|
| :----- | :----- |
|int|快进后的播放位置|

### 生效版本
|平台|生效版本|
| :----- | :----- |
|phone|13.9|
|car|null|
|tv|null|
|lite|3.3|



## 合作厂商上报数据
### 功能说明
合作厂商上报数据，用list传，每一个string都需要传key=value的形式。

### 接口函数
```java
    void report(List<String> reportData, IQQMusicApiCallback callback);
```
### 输入参数
|输入参数|类型|参数是否必传|含义|
| :----- | ----- | :----- | :----- |
|reportData|List<String>|是|上报数据|
|callback|IQQMusicApiCallback|是|接口回调|

### 生效版本
|平台|生效版本|
| :----- | :----- |
|phone|null|
|car|3.6|
|tv|null|
|lite|null|
