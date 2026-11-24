# PSA 车机开发基线（2026-11-23）

本次通过 ADB 只读采集。原始命令、返回状态及输出保存在 `2026-11-23/`；未收集账号、凭证、设备序列号、位置、目的地历史、录音或声纹。没有启动导航、录音或修改系统配置。包管理输出含系统组件与权限信息，可用于后续本地排查。

## 系统与应用

| 项目 | 本机观测 |
| --- | --- |
| 系统 | Android 9 / API 28，安全补丁 2020-11-05 |
| 固件 | 3VS222_SW6.0.8_20260819001253 |
| 型号 | 3VS121；厂商属性 alps |
| 平台 | ro.hardware=mt8666，ro.board.platform=mt6771，不能只按属性推断确切芯片型号 |
| ABI | arm64-v8a / armeabi-v7a / armeabi |
| 内存 | MemTotal 3,918,208 kB；首次采样 MemAvailable 约 1.14 GiB，属于瞬时值 |
| 数据分区 | 8.0G，已用 6.2G，可用约 1.6G，属于瞬时值 |
| 屏幕 | 1920×720，213 dpi |
| 助手 | com.genee.psavoice，0.5.1 / versionCode 12 |
| QQ 音乐 | com.tencent.qqmusiccar，3.16.0.1 |
| 高德车机版 | com.autonavi.amapauto，当前更新包 7.5.0.600082；包管理还保留系统底包 5.0.7.604198，不能混为当前版本 |
| 工具箱 | com.bandwa.openadb，5.3.4 |
| 原车语音 | com.cusc.voiceassistant，v202608051057 |
| 原车语音设置 | com.cusc.voicesettings，v202312081540 |
| 按键/执行线索 | com.cusc.execengine；输入设备 chinatsp_keypad |

系统声明 GPS、网络定位、麦克风、低延迟音频、蓝牙和 automotive 等能力。声明不等于实际功能验收。未读取位置服务运行状态或当前位置。

## 语音聊天与按键

- 音乐长按属性 `persist.bandwa.music.longclick=com.genee.psavoice`；短按为 QQ 音乐。
- `getprop` 当前仅看到音乐短长按和 card.alpha，没有电话键绑定属性。不能由此认定工具箱不支持电话键，也不应猜属性直接写入。
- chinatsp_keypad 声明 KEY_PHONE 等事件；Generic.kl 有 CALL/MUSIC/VOICE_ASSIST 映射，但未做真实按键抓取，不能断言方向盘电话键的实际事件路径。
- 助手已包含 ChatTriggerActivity。电话键绑定格式与包名/组件名支持程度仍待验证。
- 系统 voice_interaction_service、voice_recognition_service、tts_default_synth 查询均为 null；这只表示所查设置键无值，不代表原车语音/TTS 不存在。
- 本项目 CarTtsClient 当前通过显式绑定 `com.cusc.voiceassistant.SpeechService`，action `android.intent.action.CUSC_TTS_SERVICE` 调用厂商 Binder；并非依靠 Android 默认 TTS 设置。
- 后续验证：真实电话键短长按事件；聊天提示结束后录音是否干净；取消、蓝牙来电抢占、播报结束与再次录音、跨次上下文。此次没有发起聊天请求。

## 导航接入线索

- 高德 MainMapActivity 注册 `androidauto://`，包括 `launch`、`poi`、`route`、`keywordNavi`、`navi`、`naviExit`、`searchBase`、`searchAround`、`voiceOper` 等 host，完整列表见 package-com.autonavi.amapauto.txt。
- 只读 resolve-activity 实测：`androidauto://navi` 能解析到 `com.autonavi.amapauto/.MainMapActivity`。
- `androidamap://route`、`geo:0,0?q=test`、`androidauto://search` 本机均未解析到 Activity；不要套用手机高德协议或猜 search host。
- 存在 `com.autonavi.amapauto.provider.search` 等 Provider 线索，但尚未查询 Provider，也没有证明第三方可读、参数格式或返回内容。
- URI 可解析只证明路由注册；经纬度坐标系、参数、导航预览/开始、路线偏好、结果回执和普通 App 权限仍未验证。
- 下一步先核对本版本协议，再做“打开地图→目的地候选→用户确认→导航”的实机验证。LLM 输出应由客户端动作和参数白名单校验；没有导航状态回执时只说指令已发送。

## 降噪、回声消除与音色识别

本节把“音色识别”暂按说话人/声纹识别记录；播报音色选择是另一项能力。此次未录音、未建立声纹、未枚举个人语音资料。

### 已有证据

- 本项目 VoiceRecorder 当前使用 MIC，16 kHz、单声道 PCM；没有在 Java 中显式创建 NoiseSuppressor、AcousticEchoCanceler 或 AutomaticGainControl。
- `/vendor/etc/audio_effects.xml` 实际注册 aec，且只给 voice_communication 配置自动应用 aec；MIC 未见对应 preprocess。该文件的 ns/agc 仅出现在注释示例中，不能算已注册或启用。
- vendor 的 32/64 位 soundfx 目录均有 libaudiopreprocessing.so；库文件存在不证明某效果可用或生效。system 旧 conf 与 vendor XML 都已保存，实际加载和运行时效果仍待查询。
- ALSA 列出了多种采集/回放端点，包括 VOIP、EchoRef 和 ANC 命名端点；名称不能证明普通应用可使用、真实麦克风数量、阵列波束成形或硬件降噪能力。
- 服务端源码默认预处理为 `afftdn,loudnorm=I=-18:TP=-2:LRA=11`，支持通过 VG_ASR_PREPROCESS 关闭或覆盖。本次没有查线上环境变量，不能据源码默认值认定线上实际配置。
- 本次采集没有找到可确认的声纹识别接口；不能据此宣称不存在。原车语音包的 Service 注册也不足以证明普通应用能获取说话人特征或切换音色。

### 后续验证顺序

1. 做不录音的 Android AudioEffect 查询探针，读取 AEC/NS/AGC 的可用性和 descriptor；配置文件只能提供线索。
2. 在用户知情的短时测试中比较 MIC、VOICE_RECOGNITION、VOICE_COMMUNICATION；记录真实音频源、效果创建/启用结果及识别成功率，先检查切换音源对路由和音量的影响。
3. 对比停车安静、空调、行驶路噪、播报回声；分别观察误触发、漏字、延迟和小声识别。端侧与服务端处理分开开关，避免多重处理损伤人声。
4. 当前按键交互可继续用暂停音乐、播报完成后再录音来减少回灌；未来边说边听/打断聊天需要单独验证回声参考和 AEC，不能只靠稳态降噪。
5. 明确“音色识别”究竟用于区分主副驾/不同用户、识别说话风格，还是选择播报音色。声纹验证需要独立设计注册、未知说话人拒识、噪声下误判、模板存储/删除；本次未实现这些功能。

## 证据与边界

原始目录共 17 个文本文件：系统、包列表、8 个应用的包管理信息、输入与键位、音频端点、效果配置/库、入口设置及两组 URI 路由查询。各文件保留采集命令；命令失败/文件缺失也保留，便于区分未发现与未采集。

本报告仅为开发基线，不代表电话键聊天、导航、端侧降噪或声纹识别已经接通。前一轮 0.5.1 播放反馈修改仍在工作区，本次采集没有改动那些实现。
