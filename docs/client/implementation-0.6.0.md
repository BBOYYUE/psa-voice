# 客户端 0.6.0：实时会话与半透明面板

状态：0.6.0 已安装到车机，实际收到转写和助手回答；实机联调尚未完成。物理 SRC 绑定和原有切源行为接管仍待实机验证，当前工具箱/原厂 APK 均未修改。

## 操作

现有应用入口（TriggerActivity 或 ChatTriggerActivity）打开统一的半透明会话面板。
原车 TTS 播报“我在。”后开始收音；每约100毫秒上传一帧16 kHz单声道16-bit PCM，文件系统不保存录音。
临时转写在同一条消息中替换，最终转写固定成用户消息。
音乐和闲聊共用会话，回答按短句排队播报；音乐执行结果和播报完成后恢复下一轮监听。

“结束对话”“退出助手”“不聊了”精确匹配时本地退出；再次触发入口、面板“结束”按钮和返回键也会中断。
实时网关也可根据上下文识别“没事儿”“退下吧”等自然结束语，并通知客户端退出。
结束立即停麦、停止TTS、取消执行器、关闭网络连接并释放焦点，丢弃迟到回调，不自动重连或重放动作。
已发给QQ音乐的指令无法通过退出撤销。

原有 debug DiagnosticReceiver 的文字/TTS诊断仍走原 AssistantService，不改成新的实时会话探针。
诊断前先结束实时会话，避免同时占用音频资源。

## 组件

- ConversationActivity：普通半透明 Activity，不申请悬浮窗权限。仅显示当前进程的会话；SHOW返回面板不会切换会话。
- ConversationStore：主线程内存状态，最多显示12轮，无持久化聊天记录。
- LiveAssistantService：前台服务和主线程状态机，独立录音/音乐执行线程；显示层重建不重复唤醒。
- LiveGateway：OkHttp WSS，复用设备 files/server.json，保持域名和TLS校验；不把凭证放入源码或APK。
- PcmRecorder：只发内存音频帧，网络队列超过约2秒时结束交互，不积压旧语音。
- LiveProtocol / LiveTurn：校验连接身份、事件顺序、轮次、回答片段与结果；动作去重、执行确认和TTS完成共同控制恢复监听。
- LocalMusicCommand：暂停/继续/上下首客户端整句解析，最终转写即可开始执行，服务端对应快路径结果只用于核对与确认，不执行第二次。
- MusicExecutor：保留已有QQ音乐适配与20秒搜索确认；补充confirmed/sent/failed状态，无法确认时不宣称播放成功。

## 音频焦点

唤醒提示、回答播报和听取都请求 MAY_DUCK，让音乐可在降低音量后继续播放。播报期间停止录音。
执行音乐动作时释放焦点，允许QQ音乐正常切歌和检测状态。
MAY_DUCK 的实际降音幅度取决于 Android 和播放器。播放中的实车拾音仍需验证。
首版不支持助手讲话时用语音打断；按键取消不受这个限制。

## 本地验证与构建

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 bash ./gradlew assembleDebug lintDebug testDebugUnitTest
```

测试覆盖协议身份/顺序、坏动作、回答去重、执行确认、否定指令、凭证地址约束，以及API28框架下的面板加载、转写替换、SHOW不切换、再次唤醒取消和重建不重发唤醒。
真实公网测试 LiveGatewayIntegrationTest 显式启用，通过客户端实际Java传输发送官方样例两轮，不调用音乐执行器。
通过 VOICE_GATEWAY_TOKEN 与 PSA_TEST_WAV 环境变量注入私有凭证和样例路径；PSA_LIVE_INTEGRATION=1 启用，默认跳过外部测试。

```sh
PSA_LIVE_INTEGRATION=1 PSA_TEST_WAV=/absolute/path/mono-16k.wav \
JAVA_HOME=/opt/homebrew/opt/openjdk@17 bash ./gradlew testDebugUnitTest --tests com.genee.psavoice.LiveGatewayIntegrationTest --no-daemon
```

VOICE_GATEWAY_TOKEN 应提前安全注入，不直接写在命令或日志中。

APK：app/build/outputs/apk/debug/app-debug.apk；同包名更新保留files/server.json，不能先卸载或清数据。
车机连接后可用：

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.genee.psavoice/.TriggerActivity
```

再次执行最后一条会结束当前会话。

## 尚待车机验证

原车TTS是否按队列正常结束；麦克风被占用时的退出；播放中拾音；QQ音乐拉起后返回面板；
透明窗口底层合成效果；SRC编号和事件接管。当前本地没有连接车机，不能把构建/框架测试当作这些验证已通过。

## 2026-11-23 本地验证结果

- assembleDebug / lintDebug 通过，客户端版本0.6.0、versionCode 13，APK约3.5MB。
- 9项协议/轮次测试和6项API28框架下的界面/生命周期测试通过；常规测试默认跳过外部服务测试。
- 显式启用的Java WSS真实联调通过两轮：每轮10次临时转写、回答、恢复监听、取消；未执行任何QQ音乐操作。
- Android实际布局经Robolectric原生图形模式渲染为1280×720预览，并检查了排版；不是车机截图。
  预览文件：app/build/reports/conversation-preview.png。
- APK与客户端源码的真实凭证扫描通过。
- 完成本地验证后已连接车机并以 install -r 更新到0.6.0；私有配置和麦克风权限保留，未改SRC绑定。已观察到实际转写和回答，完整TTS/拾音/焦点/窗口合成验证仍待完成。

## Windows 本地运行

1. 配置JDK17或21、Android SDK34；用Android Studio打开仓库也可以。
2. 将本机的Android SDK路径写入 local.properties（该文件不提交）。
3. PowerShell执行 `.\gradlew.bat assembleDebug`，然后在Android模拟器或连接的车机安装调试APK。
4. 模拟器没有原车TTS Binder和QQ音乐车机环境：可验证界面与网络，但不代表原车播报和播放控制已验证。
   新装实例需要单独写入私有files/server.json，仓库和APK不含真实凭证。
