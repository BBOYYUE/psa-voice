# AGENTS.md — PSA 车机语音音乐助手

无界面车机语音音乐助手：长按方向盘音乐键 → 录音 → 公网网关转写/语义 → 控制 QQ 音乐车机版 → 原车 TTS 播报。当前版本 0.4.0。

## 仓库结构

- `app/` — Android 客户端（Java，Gradle KTS，Android SDK 34，最低 Android 9）。核心类：
  - `AssistantService` 前台录音交互服务；`TriggerActivity`（NoDisplay）启动入口
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
bash ./gradlew assembleDebug lintDebug   # JDK 17/21，Android SDK 34
```

- 测试车机 ADB：`192.168.43.8:5555`
- 文字链路诊断（debug）：`adb shell am broadcast -n com.genee.psavoice/.DiagnosticReceiver -a com.genee.psavoice.DEBUG_TEXT --es text '暂停一下'`
- 服务端改动先跑 `server/` 下 smoke/e2e 测试再部署

## 硬性约定

- 严禁提交真实 token / 凭证 / `.private/` 内容；客户端凭证在设备私有目录 `files/server.json`（权限 600），源码和 APK 均不含 token
- 不修改工具箱或原厂 APK；音乐键长按绑定用 `setprop persist.bandwa.music.longclick com.genee.psavoice`
- 未确认 QQ 音乐实际状态时，只播报"指令已发送/请确认"，不宣称已播放——保持这一诚实约定
- 录音只在交互期间使用 `cache/voice-requests/`，结束后删除；不开录音归档
- QQ 音乐适配以实测的普通 App 方式为准（`action=8` 搜索 + Base64 UTF-8 `search_key` + `KEY_SWITCH_APP_SCENE` 刷新主场景），旧研究笔记仅供参考
- 精确指令（暂停/继续/上一首/下一首）客户端直接解析，不走 LLM；新增指令类型时保持这条快路径
