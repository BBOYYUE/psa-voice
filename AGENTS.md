# AGENTS.md — PSA 车机语音音乐助手

车机语音助手：按键唤醒 → 半透明会话面板 → 实时收音/公网识别与对话 → QQ音乐操作或闲聊 → 原车TTS播报。客户端开发版本 0.6.0：统一连续会话与半透明面板（已安装，实机联调中）；0.5.x 的独立聊天/音乐链路仍用于 debug 诊断。

## 仓库结构

- `app/` — Android 客户端（Java，Gradle KTS，Android SDK 34，最低 Android 9）。核心类：
  - `LiveAssistantService` 连续会话前台服务；`ConversationActivity` 半透明面板；`TriggerActivity` / `ChatTriggerActivity`（NoDisplay）统一唤醒入口
  - `LiveGateway` / `PcmRecorder`：实时WSS与内存PCM上传；`LiveProtocol` / `LiveTurn`：会话与轮次校验
  - `AssistantService` 原有HTTP录音链路，保留供debug文字/TTS诊断使用
  - `SpeechGate` 端点检测（静音 0.8s 提交、无人声 5s 结束、最长 10s）
  - `CarTtsClient` 原车 TTS；`MusicExecutor` 音乐动作执行与 request_id 校验
  - `MediaAccessService` 通知监听，仅用于 MediaSessionManager 访问 QQ 音乐会话
  - `app/src/debug/` 的 `DiagnosticReceiver` 是 ADB 文字探针，仅 debug 构建
- `server/` — Python 网关（部署在 `ai.sollado.com`）：`app.py` / `asr.py` / `hermes_client.py` / `music.py`；测试 `smoke_test.py`、`test_e2e.py`、`test_asr.py`、`verify_chain.py`、`verify_music.py`
- `deploy/` — systemd 单元、nginx、Hermes 配置模板、恢复说明（见 `deploy/README.md`）
- `docs/` — QQ 音乐集成研究、QPlay IPC SDK 调研（未接入生产）
- `experiments/` — 旧音频测试源码，不编入 APK
- `vendor/` — Hermes 源码快照（tar.gz 不入库）、QPlay IPC SDK
- `tests/SpeechGateCheck.java` — 端点检测本地检查；`tests/TrackMatcherCheck.java` — 播放结果匹配本地检查

## 构建与验证

```sh
bash ./gradlew assembleDebug lintDebug testDebugUnitTest   # JDK 17/21，Android SDK 34
```

- 测试车机 ADB：`192.168.43.8:5555`
- 文字链路诊断（debug）：`adb shell am broadcast -n com.genee.psavoice/.DiagnosticReceiver -a com.genee.psavoice.DEBUG_TEXT --es text '暂停一下'`
- 服务端改动先跑 `server/` 下 smoke/e2e 测试再部署

## 硬性约定

- 严禁提交真实 token / 凭证 / `.private/` 内容；客户端凭证在设备私有目录 `files/server.json`（权限 600），源码和 APK 均不含 token
- 不修改工具箱或原厂 APK；音乐键长按绑定用 `setprop persist.bandwa.music.longclick com.genee.psavoice`；电话键长按绑定语音聊天入口（0.5.0 起，用户明确选择）
- 未确认 QQ 音乐实际状态时，只播报"指令已发送/请确认"，不宣称已播放——保持这一诚实约定
- 录音只在交互期间使用 `cache/voice-requests/`，结束后删除；不开录音归档
- QQ 音乐适配以实测的普通 App 方式为准（`action=8` 搜索 + Base64 UTF-8 `search_key` + `KEY_SWITCH_APP_SCENE` 刷新主场景），旧研究笔记仅供参考
- 精确指令（暂停/继续/上一首/下一首）客户端直接解析，不走 LLM；新增指令类型时保持这条快路径
