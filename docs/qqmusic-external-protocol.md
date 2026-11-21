# 外部拉起 QQ 音乐车机版协议

平台：Android
Latest Version: v1.9.8

# 目录

[1.1 scheme方式](/external?id=_11-scheme%e6%96%b9%e5%bc%8f)

[1.1.0 打开QQ音乐](/external?id=_110-%E6%89%93%E5%BC%80QQ%E9%9F%B3%E4%B9%90)

[1.1.1 关闭QQ音乐](/external?id=_111-%E5%85%B3%E9%97%ADQQ%E9%9F%B3%E4%B9%90)

[1.1.2 打开最近播放页面](/external?id=_112-%E6%89%93%E5%BC%80%E6%9C%80%E8%BF%91%E6%92%AD%E6%94%BE%E9%A1%B5%E9%9D%A2)

[1.1.3 打开本地音乐页面](/external?id=_113-%E6%89%93%E5%BC%80%E6%9C%AC%E5%9C%B0%E9%9F%B3%E4%B9%90%E9%A1%B5%E9%9D%A2)

[1.1.4 打开收藏音乐页面](/external?id=_114-%E6%89%93%E5%BC%80%E6%94%B6%E8%97%8F%E9%9F%B3%E4%B9%90%E9%A1%B5%E9%9D%A2)

[1.1.5 打开车载歌单页面](/external?id=_115-%E6%89%93%E5%BC%80%E8%BD%A6%E8%BD%BD%E6%AD%8C%E5%8D%95%E9%A1%B5%E9%9D%A2)

[1.1.6 打开音乐电台页面](/external?id=_116-%E6%89%93%E5%BC%80%E9%9F%B3%E4%B9%90%E7%94%B5%E5%8F%B0%E9%A1%B5%E9%9D%A2)

[1.1.7 打开音乐排行榜页面](/external?id=_117-%E6%89%93%E5%BC%80%E9%9F%B3%E4%B9%90%E6%8E%92%E8%A1%8C%E6%A6%9C%E9%A1%B5%E9%9D%A2)

[1.1.8 打开搜索页面](/external?id=_118-%E6%89%93%E5%BC%80%E6%90%9C%E7%B4%A2%E9%A1%B5%E9%9D%A2)

[1.1.9 打开自建歌单页面](/external?id=_119-%E6%89%93%E5%BC%80%E8%87%AA%E5%BB%BA%E6%AD%8C%E5%8D%95%E9%A1%B5%E9%9D%A2)

# 更新日志：

2022.04.14 初始说明（by liebli）

## 版本接口变更

1.11.0 增加打开自建歌单页面

# 1.1 scheme 方式(推荐方式)

### 1.1.0 打开QQ音乐

腾讯音乐客户端支持 intent scheme 方式外部拉起方式，预定义 uri 及参数说明如下所示。

| Scheme Uri | qqmusiccar://?action=0 | |
| --- | --- |--- |
| 功能 | 打开 QQ 音乐 |
| 参数及说明 | action（int 类型） | 对应 action 名称，必填字段 action=0 |

- 使用示例

```java
Intent intent = new Intent();
String uri = "qqmusiccar://?action=0";
intent.setData(Uri.parse(uri));
startActivity(intent);
```

### 1.1.1 关闭QQ音乐

| Scheme Uri | qqmusiccar://?action=1 | |
| --- | --- |--- |
| 功能 | 关闭 QQ 音乐 |
| 参数及说明 | action（int 类型） | 对应 action 名称，必填字段 action=1 |

- 使用示例

```java
Intent intent = new Intent();
String uri = "qqmusiccar://?action=1";
intent.setData(Uri.parse(uri));
startActivity(intent);
```

### 1.1.2 打开最近播放页面

| Scheme Uri | qqmusiccar://?action=2&mb=true&m1=true | |
| --- | --- |--- |
| 功能 | 打开最近播放页面 ||
| 参数及说明 | action（int类型） | 对应action名称，必填字段 action=2 |
|   | mb (boolean) | 按返回键是否返回退回到launch，true返回到launch，false返回到QQ音乐的上一层，默认false |
|   | m1 (boolean) | 是否进入后直接开始播放，默认false |

- 使用示例

```java
Intent intent = new Intent();
String uri = "qqmusiccar://?action=2&mb=true&m1=true";
intent.setData(Uri.parse(uri));
startActivity(intent);
```

### 1.1.3 打开本地音乐页面

| Scheme Uri | qqmusiccar://?action=3&mb=true&m1=true | |
| --- | --- |--- |
| 功能 | 打开本地音乐页面 ||
| 参数及说明 | action（int类型） | 对应action名称，必填字段 action=3 |
|   | mb (boolean) | 按返回键是否返回退回到launch，true返回到launch，false返回到QQ音乐的上一层，默认false |
|   | m1 (boolean) | 是否进入后直接开始播放，默认false |

- 使用示例

```java
Intent intent = new Intent();
String uri = "qqmusiccar://?action=3&mb=true&m1=true";
intent.setData(Uri.parse(uri));
startActivity(intent);
```

### 1.1.4 打开收藏音乐页面

| Scheme Uri | qqmusiccar://?action=4&mb=true&m1=true | |
| --- | --- |--- |
| 功能 | 打开本地音乐页面 ||
| 参数及说明 | action（int类型） | 对应action名称，必填字段 action=4 |
|   | mb (boolean) | 按返回键是否返回退回到launch，true返回到launch，false返回到QQ音乐的上一层，默认false |
|   | m1 (boolean) | 是否进入后直接开始播放，默认false |

- 使用示例

```java
Intent intent = new Intent();
String uri = "qqmusiccar://?action=4&mb=true&m1=true";
intent.setData(Uri.parse(uri));
startActivity(intent);
```

### 1.1.5 打开车载歌单页面

| Scheme Uri | qqmusiccar://?action=5&mb=true&m1=true | |
| --- | --- |--- |
| 功能 | 打开车载歌单页面 ||
| 参数及说明 | action（int类型） | 对应action名称，必填字段 action=5 |
|   | mb (boolean) | 按返回键是否返回退回到launch，true返回到launch，false返回到QQ音乐的上一层，默认false |
|   | m1 (boolean) | 是否进入后直接开始播放，默认false |

- 使用示例

```java
Intent intent = new Intent();
String uri = "qqmusiccar://?action=5&mb=true&m1=true";
intent.setData(Uri.parse(uri));
startActivity(intent);
```

### 1.1.6 打开音乐电台页面

| Scheme Uri | qqmusiccar://?action=6&mb=true&m1=true | |
| --- | --- |--- |
| 功能 | 打开音乐电台页面 ||
| 参数及说明 | action（int类型） | 对应action名称，必填字段 action=6 |
|   | mb (boolean) | 按返回键是否返回退回到launch，true返回到launch，false返回到QQ音乐的上一层，默认false |
|   | m1 (boolean) | 是否进入后直接开始播放，默认false |

- 使用示例

```java
Intent intent = new Intent();
String uri = "qqmusiccar://?action=6&mb=true&m1=true";
intent.setData(Uri.parse(uri));
startActivity(intent);
```

### 1.1.7 打开音乐排行榜页面

| Scheme Uri | qqmusiccar://?action=7&mb=true&m1=true | |
| --- | --- |--- |
| 功能 | 打开音乐排行榜页面 ||
| 参数及说明 | action（int类型） | 对应action名称，必填字段 action=7 |
|   | mb (boolean) | 按返回键是否返回退回到launch，true返回到launch，false返回到QQ音乐的上一层，默认false |
|   | m1 (boolean) | 是否进入后直接开始播放，默认false |

- 使用示例

```java
Intent intent = new Intent();
String uri = "qqmusiccar://?action=7&mb=true&m1=true";
intent.setData(Uri.parse(uri));
startActivity(intent);
```

### 1.1.8 打开搜索页面

| Scheme Uri | qqmusiccar://?action=8&mb=true&m1=true&search_key= | |
| --- | --- |--- |
| 功能 | 打开搜索页面 ||
| 参数及说明 | action（int类型） | 对应action名称，必填字段 action=8 |
|   | search_key (String) | 搜索内容，必填字段，需要Base64编码 |
|   | mb (boolean) | 按返回键是否返回退回到launch，true返回到launch，false返回到QQ音乐的上一层，默认false |
|   | m1 (boolean) | 是否进入后直接开始播放，默认false |

- 使用示例

```java
Intent intent = new Intent();
String uri = "qqmusiccar://?action=8&mb=true&m1=true&search_key=";
intent.setData(Uri.parse(uri));
startActivity(intent);
```

### 1.1.9 打开自建歌单页面

| Scheme Uri | qqmusiccar://?action=9&mb=true&m1=true | |
| --- | --- |--- |
| 功能 | 打开自建歌单页面 ||
| 参数及说明 | action（int类型） | 对应action名称，必填字段 action=9 |
|   | mb (boolean) | 按返回键是否返回退回到launch，true返回到launch，false返回到QQ音乐的上一层，默认false |
|   | m1 (boolean) | 是否进入后直接开始播放，默认false |

- 使用示例

```java
Intent intent = new Intent();
String uri = "qqmusiccar://?action=9&mb=true&m1=true";
intent.setData(Uri.parse(uri));
startActivity(intent);
```
