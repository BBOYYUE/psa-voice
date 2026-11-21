
# 实体说明

## 专辑信息 Data.Album

| 方法          | 调用方式                                                 | 说明          |
|:------------|:-----------------------------------------------------|:------------|
| getCoverUri | `public java.lang.String getCoverUri()`              | 获取专辑封面图片uri |
| getId       | `public long getId()`                                | 获取专辑id      |
| getMid      | `public java.lang.String getMid()`                   | 获取专辑Mid     |
| getTitle    | `public java.lang.String getTitle()`                 | 获取专辑名称      |
| setCoverUri | `public void setCoverUri(java.lang.String coverUri)` | 设置专辑封面图片uri |
| setId       | `public void setId(long id)`                         | 设置专辑id      |
| setMid      | `public void setMid(java.lang.String mid)`           | 设置专辑Mid     |
| setTitle    | `public void setTitle(java.lang.String title)`       | 设置专辑名称      |

## 歌曲信息 Data.Song

| 方法             | 调用方式                                                                        | 说明                                                        |
|:---------------|:----------------------------------------------------------------------------|:----------------------------------------------------------|
| getAlbum       | `public Data.Album getAlbum()`                                              | 获取专辑信息                                                    |
| getDuration    | `public long getDuration()`                                                 | 获取歌曲时长，单位ms                                               |
| getGenre       | `public java.lang.String getGenre()`                                        | 获取歌曲流派                                                    |
| getId          | `public java.lang.String getId`                                             | 获取歌曲id                                                    |
| getLabelIdList | `public java.util.List<java.lang.Integer> getLabelIdList()`                 | 获取歌曲标签id列表，标签id映射关系如下： 1: 动感 19: 国风 75: 怀旧 93: 沉浸 162: 潮流 |
| getMid         | `public java.lang.String getMid()`                                          | 获取歌曲Mid                                                   |
| getSinger      | `public Data.Singer getSinger()`                                            | 获取歌手                                                      |
| getTitle       | `public java.lang.String getTitle()`                                        | 获取歌曲名称                                                    |
| getType        | `public int getType()`                                                      | 获取当前播放类型                                                  |
| isAudition     | `public boolean isAudition()`                                               | 是否为试听歌曲                                                   |
| isOrigin       | `public boolean isOrigin()`                                                 | 是否为原唱, false对应未知                                          |
| setAlbum       | `public void setAlbum(Data.Album album)`                                    | 设置专辑信息                                                    |
| setAudition    | `public void setAudition(boolean audition)`                                 | 设置是否为试听歌曲                                                 |
| setDuration    | `public void setDuration(long duration)`                                    | 设置歌曲时长，单位ms                                               |
| setGenre       | `public void setGenre(java.lang.String genre)`                              | 设置歌曲流派                                                    |
| setId          | `public void setId(java.lang.String id)`                                    | 设置歌曲id                                                    |
| setLabelIdList | `public void setLabelIdList(java.util.List<java.lang.Integer> labelIdList)` | 设置歌曲标签id列表                                                |
| setMid         | `public void setMid(java.lang.String mid)`                                  | 设置歌曲Mid                                                   |
| setOrigin      | `public void setOrigin(boolean origin)`                                     | 设置是否为原唱                                                   |
| setSinger      | `public void setSinger(Data.Singer singer)`                                 | 设置歌手                                                      |
| setTitle       | `public void setTitle(java.lang.String title)`                              | 设置歌曲名称                                                    |
| setType        | `public void setType(int type)`                                             | 设置当前播放类型                                                  |

## 歌手信息 Data.Singer
| 方法       | 调用方式                                           | 说明      |
|:---------|:-----------------------------------------------|:--------|
| getId    | `public long getId()`                          | 获取歌手id  |
| setId    | `public void setId(long id)`                   | 设置歌手id  |
| getTitle | `public java.lang.String getTitle()`           | 获取歌手名称  |
| setTitle | `public void setTitle(java.lang.String title)` | 设置歌手名称  |
| getMid   | `public java.lang.String getMid()`             | 获取专辑Mid |
| setMid   | `public void setMid(java.lang.String mid)`     | 设置专辑Mid |

## 目录信息 Data.FolderInfo

| 方法            | 调用方式                                                    | 说明                                               |
|:--------------|:--------------------------------------------------------|:-------------------------------------------------|
| getType       | `public int getType()`                                  | 获取目录类型                                           |
| setType       | `public void setType(int type)`                         | 设置目录类型                                           |
| getId         | `public java.lang.String getId()`                       | 获取目录ID                                           |
| setId         | `public void setId(java.lang.String id)`                | 设置目录ID                                           |
| getMainTitle  | `public java.lang.String getMainTitle()`                | 获取主标题                                            |
| setMainTitle  | `public void setMainTitle(java.lang.String mainTitle)`  | 设置主标题                                            |
| getSubTitle   | `public java.lang.String getSubTitle()`                 | 获取副标题                                            |
| setSubTitle   | `public void setSubTitle(java.lang.String subTitle)`    | 设置副标题                                            |
| getPicUrl     | `public java.lang.String getPicUrl()`                   | 获取封面url                                          |
| setPicUrl     | `public void setPicUrl(java.lang.String picUrl)`        | 设置封面url                                          |
| isSongFolder  | `public boolean isSongFolder()`                         | 是否是歌曲目录 （true: 下一级内容是歌曲列表 false: 下一级内容是Folder列表） |
| setSongFolder | `public void setSongFolder(boolean songFolder)`         | 设置是否是歌曲目录                                        |

## 媒体信息 Data.MediaInfo

| 登陆状态              | 值                                                          | 说明      |
|:------------------|:-----------------------------------------------------------|:--------|
| describeContents  | public int describeContents()                              ||
| getAudioFormat    | public int getAudioFormat()                                ||
| getChannelConfig  | public int getChannelConfig()                              ||
| getSampleRateInHz | public int getSampleRateInHz()                             ||
| setAudioFormat    | public void setAudioFormat(intaudioFormat)                 ||
| setChannelConfig  | public void setChannelConfig(intchannelConfig)             ||
| setSampleRateInHz | public void setSampleRateInHz(intsampleRateInHz)           ||
| writeToParcel     | public void writeToParcel(android.os.Parceldest, intflags) ||

