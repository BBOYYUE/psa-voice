# PSA 音乐语音助手

Android 9 车机上的无界面音乐助手，包名 `com.genee.psavoice`，当前版本 `0.2.3`。

## 已接通的流程

长按音乐键约 2 秒后松开 → 暂停音乐 → 原车音色播报“我在，请说” → 录音 → 说完约 0.8 秒静音自动提交 → `ai.sollado.com` 转写和分析 → 控制 QQ 音乐 → 播报结果。

- 没有人声约 5 秒结束，单次录音最多 10 秒。
- 处理中再次长按音乐键会取消当前交互。
- 0.2.1 保持工具箱原有的 2 秒长按阈值；助手关闭入口切换动画、不保留最近任务。
- 0.2.1 在提示语前取得临时音频焦点，保持到录音和请求结束；提示语完成后直接录音。
- 没有操作页面；录音/处理期间使用前台服务通知，结束后关闭。
- 只接音乐场景。短按音乐键保持启动 QQ 音乐，其他按键未改。
- 原车 TTS 接口不可用时，以短提示音降级；正式音色跟随原车当前设置，当前在线音色是 `qianranf`。

支持搜歌播放、仅搜索、暂停、继续、上一首和下一首。没有确认 QQ 音乐实际状态时，只播报指令已发送或请确认结果，不宣称已播放。

## 车机上的配置

当前测试车机 ADB：`192.168.43.8:5555`。
工具箱 `com.bandwa.openadb 5.3.4` 已提供音乐键长按启动 App 的配置：

```sh
adb shell setprop persist.bandwa.music.longclick com.genee.psavoice
```

原绑定为 `com.tencent.qqmusiccar`；回退按键时将值改回即可。没有修改工具箱或原厂 APK。

启动入口是 `TriggerActivity`，使用 NoDisplay 主题，启动前台服务后立即退出。旧音频测试界面源码存于 `experiments/audio-test/`，不编入当前 APK；历史测试 APK 仍可单独回装。

配置文件为应用私有目录 `files/server.json`（权限 600）：

```json
{"base_url":"https://ai.sollado.com","token":"<单独配置，不打包进APK>"}
```

当前设备已配置凭证，源码和 APK 均不含真实 token。安装更新保留该配置；清应用数据或卸载会丢失配置，需要重新写入。

必要权限：

```sh
adb shell pm grant com.genee.psavoice android.permission.RECORD_AUDIO
adb shell cmd notification allow_listener com.genee.psavoice/.MediaAccessService
```

通知监听组件仅用于授权 MediaSessionManager 访问 QQ 音乐会话，不读取或上传通知内容。

## 音频与临时文件

录音采用 MIC，PCM 16-bit、16 kHz、单声道 WAV；上传原始数据，不上传音频测试版的回放增益副本。
客户端仅在交互期间使用 `cache/voice-requests/`，上传结束或失败后删除；进程异常退出的遗留 WAV 在下次启动时清理（超过 10 分钟）。没有开启录音归档。升级到当前版本会删除本 App 两份旧音频测试录音。

服务端上传文件和转码文件也会清理，见 [server/README.md](server/README.md)。服务端源码已部署至 `ai.sollado.com`，本地 server 目录不含实际凭证或模型。

## QQ 音乐适配

实测 QQ 音乐车机版 `3.16.0.1`。
搜索使用第三方 `action=8` 和 Base64 UTF-8 `search_key`。
自动播放先通过 `AppStarterActivity` 的 `KEY_SWITCH_APP_SCENE=true` 刷新主场景，稍后再发送搜索播放指令。这避免旧搜索 Fragment 复用，也避免 CLEAR_TASK 可能造成的空白主界面。

后台会话控制使用 QQ 音乐的 MediaController，限定包名为 `com.tencent.qqmusiccar`。
服务器动作会在客户端再次校验，仅允许预定义音乐动作；request_id 必须匹配本次请求，同一请求不会重复执行。

旧的研究记录见 [docs/qqmusic-integration.md](docs/qqmusic-integration.md)，以本次普通 App 的实测方式为准。

## 构建与验证

使用完整 JDK 17/21、Android SDK 34：

```sh
bash ./gradlew assembleDebug lintDebug
```

Android 9 上运行前台麦克风服务；较新 Android 的后台启动限制尚未实机验证。
当前部署 APK 为内部验证用 debug 签名，未发布应用商店。

Debug 构建包含一个受系统 `DUMP` 权限保护的 ADB 文字探针，普通 App 不能使用，release 构建不包含：

```sh
adb shell am broadcast -n com.genee.psavoice/.DiagnosticReceiver \
  -a com.genee.psavoice.DEBUG_TEXT --es text '暂停一下'
```

设备最近一次交互结果在 `shared_prefs/assistant_status.xml`，不含服务 token；录音处理前台服务在交互结束后关闭，媒体访问组件由系统维持绑定。

## 2026-11-21 验证结果

- 构建与 Android Lint 通过。
- 端点检测检查通过：静音超时、人声结束、短噪声抑制、最长时长。
- 普通 App → 公网服务 → QQ 音乐：仅搜索、自动播放、暂停均已实测。
- 用户实际长按音乐键说出“播放周杰伦的晴天”后，记录到新的语音请求，QQ 元数据显示《晴天》/周杰伦且处于播放状态；用户确认成功。
- 该交互结束后，车机录音缓存为空，录音前台服务已停止。
- 行驶中的路噪、弱网、断网、连续多次语音切歌和两种按键短长按交替，还需要后续使用验证。

## 服务端备份

服务器部署前备份：`/home/sollado/projects/voice-gateway/backups/20261121T135956Z-music-v1/`，仅保留在服务器。包含部署前代码、服务单元与原配置。

0.2.2：分别提示语义服务无响应、无法理解指令和实际连接失败。车载 Hermes voice profile 固定 `model.context_length: 65536`，避免初始化自动探测阻塞；音乐指令独立解析，不重放历史中尚未执行的指令。通用聊天会话行为不变。

0.2.3：车载 Hermes 显式关闭思考模式，DeepSeek 单次请求/停滞超时 12 秒、api_max_retries=1；暂停、继续和上一首/下一首等精确指令直接解析，其他请求仍由 Hermes 处理。成功的独立音乐解析结果在内存缓存 10 分钟，最多 128 条，未执行结果和错误不缓存，不缓存录音。车机保存各阶段耗时用于排查偶发延迟。

## 本地完整代码与配置

客户端、服务端、部署模板、私有配置备份和同版本 Hermes 源码快照统一保存在本项目目录。具体结构、服务器路径和恢复说明见 [deploy/README.md](deploy/README.md)。`.private/` 含真实凭证，已忽略且限本机访问。

## QPlay IPC SDK 接入研究

官方 SDK 1.1.6、Demo、车载能力矩阵与授权/接入计划见 [docs/qplay-ipc/README.md](docs/qplay-ipc/README.md)。普通 IPC 支持的功能和语音捷径的 car=null 限制已分开记录；离线封装未接入生产客户端。
