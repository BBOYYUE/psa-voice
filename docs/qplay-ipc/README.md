# QQ 音乐 QPlay IPC Android SDK 接入研究

研究日期：2026-11-22。范围：官方 QPlay IPC 文档、官方 Demo、SDK 1.1.6 二进制合同，以及已有 QQ 音乐车载 3.16 APK 的静态代码。不连接车机，不修改现有 APK、客户端生产代码或线上服务。

## 结论

车载客户端优先采用 QPlay IPC 的普通搜索、歌曲/目录播放、状态、收藏和模式接口；Hermes 保留音乐理解、推荐、偏好及多轮对话职责。原 Scheme/MediaSession 路径在新接口完成授权与实测前保留。

**不能把 voicePlay / voiceShortcut 当作车载已支持功能。** 官方介绍页统称支持语音捷径，但接口级版本表将这两个方法的 car 标为 null，FAQ 也要求以各平台的版本表为准。当前能证实的是普通 IPC 车载接口，不是 QQ 内置的语音解析能力。

## 来源与版本

- [官方介绍](https://y.qq.com/music_developer/#/docs/qplayaidl#/overview)
- [官方接入指南](https://y.qq.com/music_developer/#/docs/qplayaidl#/guide)
- [官方接口表](https://y.qq.com/music_developer/#/docs/qplayaidl#/interface)
- [官方实体、常量和错误码](https://y.qq.com/music_developer/#/docs/qplayaidl#/error_code_and_return)
- [官方 Demo](https://github.com/tencentmusic/QQMusic_Innovation_QPlay_AIDL_OpenID_Demo)，本地 `vendor/qplay-ipc-demo`，partner 分支 commit `6bd8a9e1734e8927b08160440540f44658677f8a`。
- `upstream/` 保存八份官方 Markdown，来源前缀为 `https://y.qq.com/music_developer/static/docs/TMEConnect/QPlayAIDL/`。
- 官方介绍页版本 1.1.5；官方 Maven metadata 的 release 为 1.1.6，latest 为 1.1.7.1-SNAPSHOT；官方 Demo 自带依赖为 1.1.4-SNAPSHOT。本研究固定稳定版 1.1.6，不采用 SNAPSHOT。
- SDK 坐标：`com.tencent.qqmusic.innovation:aidl-api-sdk:1.1.6`。官方仓库使用 HTTPS `https://ct.y.qq.com/TVapp/nexus/repository/maven-public/`，只使用 Demo 公开的仓库下载账号，不使用 HTTP。
- 本地 AAR、POM、解出的 classes.jar：`vendor/qplay-ipc-sdk/1.1.6/`。
- AAR SHA-256：`ec51699e3032fdd4e4d7a3864bc7b25b0ab47baf4c33506faf127897f733d0af`。
- Demo 的 Apache 2.0 许可保留在其仓库内；不能据此推定 SDK 商业授权条款相同。本地 SDK 只用于研究和接入准备，分发前以开发者平台协议为准。

## 平台、授权与申请资料

我们的包名：`com.genee.psavoice`。目标平台：`car`。对端包名：`com.tencent.qqmusiccar`。

官方要求先申请 AppID，获批并配置合作方 RSA 公钥；私钥自行保管。Demo 指定 RSA 1024-bit、PKCS#8，实际申请时若平台要求不同，以平台当前要求为准。RSA 公钥不是 APK 签名证书；不要把两者混淆。本轮没有生成新密钥，也没有复用 Demo 身份。

两种授权不同：

1. `CommonCmd.verifyCallerIdentity` 验证合作方身份，是 SDK 功能使用前提。输入涉及 AppID、包名、加密签名串和回调地址。
2. `requestAuth` 获取个人资产授权，返回的加密材料需按 Demo 验签、解密并校验 nonce，再使用 openId/openToken 调用户目录接口。QQ 登录状态也是独立状态。

若个人开发者或申请的应用类型不能获得相应接口权限，需要向 QQ 对接方确认；下载 SDK 成功不代表 AppID 获准全部能力。

建议注册专用回调 Scheme `psavoice-qplay://auth`，后续实现时必须与签名 payload 和 callback Activity 一致。它是待接入设计，不是已经部署的入口。

长期 App 私钥优先放服务端签名配置，移动端通过现有鉴权通道领取短时授权材料，避免在 APK 常量里嵌入。此方式需要按官方 nonce、callbackUrl 验签协议验证后实现；当前未新增签名服务。openToken 等用户凭证仅保存在应用私有存储，不进入模型上下文或日志。

## 车载接口能力

以下是官方文档声明，尚未授权实测。最低版本不等于当前具体安装包、账号和 AppID 必然获准。

| 需求 | 方法 | car 最低版本 | 关键点 |
|---|---|---|---|
| 搜索歌曲 | search | 1.9.5 | keyword、searchType=0、firstPage；异步回调 |
| 搜索歌单 | search，SearchType=3 | search 整体标为 1.9.5 | 枚举存在；具体类型在车载端的返回格式和支持程度待实测 |
| 指定歌曲播放 | playSongId / playSongIdAtIndex | 1.12.0 | **使用 songId**，保留原始标识字符串，不与 MID 混用 |
| 指定 MID 列表播放 | playSongMid / playSongMidAtIndex | null | 不作为车载播放主路径 |
| 当前歌曲、播放状态 | getCurrentSong / getPlaybackState | 1.9.5 | 确认实际结果，再回报 server |
| 当前播放列表 | getPlayList | null | 不承诺读取当前完整播放队列；先维护我们自己发起的候选/播放列表 |
| 歌单/电台/榜单目录 | getFolderList / getSongList | 1.12.0 | 使用返回的目录 ID 和类型，不按名称猜 ID |
| 用户目录 | getUserFolderList | 1.9.5 | 需要用户资产授权 |
| 用户目录歌曲 | getUserSongList | 1.12.0 | 需要用户资产授权 |
| 播放指定目录 | playFolderType | 2.0.0 | folderId、folderType、起播 index |
| 收藏/取消收藏 | addToFavourite / removeFromFavourite | 2.0.3 | **使用 MID**；先查状态，执行后验证 |
| 收藏状态 | isFavouriteMid | 1.9.5 | 区分 MID、ID、type 参数 |
| 读取/设置播放模式 | getPlayMode / setPlayMode | 1.9.5 | 优先使用带结果的方法 |
| 设置播放模式并返回结果 | setPlayModeWithRet | 2.5.0 | 成功后再 getPlayMode 验证 |
| 语音捷径 | voicePlay / voiceShortcut | null | SDK 存在，但车载平台未声明支持 |
| 创建歌单、向指定自建歌单添加歌曲 | 未发现公开方法 | 未确认 | SDK 1.1.6 Methods 接口和公开目录均未找到，不承诺支持 |

机器可读的 54 个文档方法与 car 版本表见 `car-capabilities.json`。原始 SDK 公共 API 和常量见 `sdk-1.1.6-public-api.txt`。

播放模式的实际 SDK 常量：列表循环=0、单曲循环=1、随机播放=2。搜索类型包括歌曲=0、歌手=1、专辑=2、歌单=3、歌词=7、相似歌曲=9、综合=100、综合新版=101。各搜索子类型仍需车载验证，枚举存在不是能力证明。

## 调用链与实现约束

```text
绑定指定 car 服务 → CommonCmd.init("car") → hi / 身份授权 / QQ登录
    → 读取状态与注册事件
    → search 得到候选（此时不播放）
    → Hermes 或用户确认候选
    → playSongIdAtIndex 执行
    → 事件＋状态读取确认
    → 回报 request_id、实际歌曲、播放/收藏/模式状态
```

绑定 Intent 必须同时使用 car action 与 car 包名：

```java
new Intent("com.tencent.qqmusiccar.third.api.QQMusicApiService")
    .setPackage("com.tencent.qqmusiccar");
```

接口传输是 `execute(method, Bundle)`、`executeAsync(method, Bundle, callback)`，不是把 method 当作 Android Intent action。包含 IQQMusicApiCallback 的方法走异步；同步 Binder 也须在工作线程执行。

- 连接与业务调用都设置超时；Binder 死亡使待处理请求失败，再有限重连。
- late callback 用 request_id/generation 拦截，不执行已经取消的动作。
- 缺少 Bundle、code 或 data 不得默认成功；0 才是 ERROR_OK。业务执行接受不等于已经播放。
- 保留完整 Bundle，拿到真实返回样本后按接口解码，不一律把 data 当成 JSON String。
- 搜索 firstPage 的类型为 boolean，不是页码数字；翻页顺序、会话和 hasMore 行为待真机验证。搜索并发是否共享游标也需要测试，第一阶段串行搜索。
- songId 与 MID 两个字段均保存：播放走 ID，收藏走 MID。
- 事件使用 SDK 的 Events 常量，不照抄 Demo 的示例字符串。车载事件是否齐全需要实测，并使用状态查询兜底。
- 收藏、模式变更和播放均依赖明确的用户动作，不因建议或一次搜索就改变账号/播放状态。
- 不开启 PCM 传输。当前需求是控制 QQ 原播放器，且该接口 car 标为 null；继续保留既有原车音频焦点处理。

重点错误：3=操作不支持/调用方式错误，5=应用无权限，7=需要用户登录，11=播放服务未就绪，12=未同意隐私协议，14=当前应用不支持 IPC，203/204=授权相关失败，300=不支持语音助手。实际错误与必要操作应分开提示，不统一说连接失败。

## Demo 中不能直接照搬的地方

1. Config 默认平台是 phone，必须选 car。
2. QPlayBindHelper 对 car 使用的包名是 com.tencent.qqmusiccar；接入指南简化示例却统一写 com.tencent.qqmusic，不能机械复制指南这一行。
3. QQMusicApiWrapper.getQQMusicVersion 内部把 platformType 写为 phone，应改 car。
4. OpenIDHelper 把 callbackUrl 写成 qqmusicapidemo://，必须替换并与回调 Activity 保持一致。
5. ApiSample.registerEventListener 示例使用的 onPlayStateChanged/onSongChanged 与 SDK Events 的实际字符串不同，使用常量。
6. Demo 某些同步封装用 `?: 0` 处理空 Bundle，会把失败当作成功；我们的适配不得照搬。
7. Demo getEncryptString 签名流程需完整保留；回调 Activity 的未校验广播不能作为我们已授权的唯一证据。
8. Demo 老 Gradle、Support Library 和 SNAPSHOT 依赖不适合直接合入现有 Java/AGP 8.3 项目。使用固定 AAR，并核对 Kotlin 运行时依赖；POM 没有声明依赖不代表代码无运行时引用。

## 已完成与后续验证

已完成：官方资料快照、Demo 固定版本、SDK 1.1.6 下载与 SHA-256 固定、54 个方法的平台矩阵、公共类/常量核对，以及独立调用封装的离线编译。封装位于 `experiments/qplay-ipc/src/.../QplayCarCalls.java`，不参与当前 App 构建，没有自动绑定、授权或执行行为。

复核命令（项目根）：

```sh
python3 tools/verify_qplay_sdk.py
```

脚本只校验本地文件和 javac 编译，不连接设备或发起模型调用。需要 JDK 和 Android SDK 34，支持 JAVA_HOME / ANDROID_HOME。

AppID 到位后按顺序验证：

1. 绑定、hi、实际 QQ 版本、身份授权与错误提示；不先测试写操作。
2. 搜索返回样本、分页、歌曲 ID/MID；查询当前状态。
3. 指定歌曲播放，校验回调和实际歌曲；保持语音提示→录音之间音乐不恢复。
4. 事件注册/注销、进程重启、取消和过期回调。
5. 用户资产授权、本人歌单读取、按目录播放。
6. 在明确测试歌曲上验证收藏/取消收藏，以及三种播放模式的设置与回读。
7. 再把稳定动作接进服务端白名单、Hermes 会话反馈。生产客户端版本与线上动作协议暂不改变。

## 尚未读取的语音专用文档

官方导航链接到微信《QQ音乐语音接入文档》。此次浏览器被站点安全策略阻止，未读取正文，没有尝试绕过；故 slotList 结构、捷径枚举等不作推测。普通 IPC 车载接口的研究可继续进行，不依赖这一文档；若后续官方确认 car 特殊开放语音接口，再补充核对。

## 2026-11-22 账号注册条件核对

用户实际找不到注册入口后，进一步核对官方平台前端。当前 /register 页面在无 authCode 时显示“请联系商务获取注册链接”；注册表单组织名称提示“目前不面向个人开发者开放”，提交也要求邀请码。登录页为开发者账号/密码，不等同于普通 QQ 音乐用户登录。

因此 AppID 申请不是自由个人自助流程，需要先联系官方商务取得注册链接并经过审核。是否接受个人项目或有企业合作通道应由官方确认，不代填组织、不复用 Demo 身份、不绕过身份验证。

官方入口：https://y.qq.com/music_developer/#/register 。本次读取的官方前端资源：
- https://y.qq.com/music_developer/assets/index-D7oFBN_o.js （注册）
- https://y.qq.com/music_developer/assets/index-BBfo71Hv.js （登录）

快照保存在 upstream/developer-register.js 与 developer-login.js；只保存公开页面代码，没有个人登录态。SDK 资料及离线封装可继续研究，但正式 SDK 授权联调等待合法 AppID。未改动线上服务或已安装车机客户端。
