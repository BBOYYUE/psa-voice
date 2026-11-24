# 原车语音能力本地分析（2026-11-24）

范围：分析此前从车机拉取的 PSA_VoiceAssistant.apk（版本 v202608051057）。本轮未连接或操作车机，未读取个人声纹、未录音、未改设置。DEX 使用 Androguard 反汇编，以下是静态代码结论，不等于运行验收。

## 查询接口与权限

服务组件：com.cusc.voiceassistant/com.cusc.voiceassistant.SpeechService。
Manifest 明确 enabled/exported 为 true，服务声明未见 permission。onBind 按 action 分发，在已检查的 onBind、Binder Stub、查询实现和下游查询路径未见 UID、签名、调用方权限检查；普通应用可访问仍需实机绑定确认。

| Action | Binder descriptor | 已核对事务 |
| --- | --- | --- |
| android.intent.action.CUSC_VOICE_MANAGER_SERVICE | com.cusc.manager.voicemanager.IVoiceManagerService | 1: isVoiceWakeupOpened()，reply.readInt()!=0；6: getWakeupWords()，reply.readString() |
| android.intent.action.CUSC_VOICEPRINT_MANAGER_SERVICE | com.cusc.manager.voicemanager.IVprintManagerService | 2: isCanUse()，reply.readInt() |

调用须 writeInterfaceToken、检查 transact 返回值、readException，再读取结果；失败必须记为查询失败，不能套用 AIDL Default 的返回值。语音管理后端未就绪时可能返回 false/空字符串，不能直接解释为用户关闭或无唤醒词。

## 唤醒

SpeechService$8 → AiSpeechVoiceManager → VoiceUtil。
- isVoiceWakeupOpened 读取 VoiceUtil 的内存缓存，首次从原车 ACache 的 voice_wakeup_opened 读取；不是 Android settings 键。此前 settings 查询无值不能判断关闭。
- getWakeupWords 实际调用 getAccessoryWakeupWords，读取 accessory_wakeup_words；只返回附加/自定义词，不保证覆盖默认主唤醒词。空值不能解释成不支持唤醒。
- IVoiceManagerCallback 只有设置变化（onVoiceWakeupOpened、onSetWakeupWords 等），没有实际唤醒事件；不能用它直接启动我们的会话。
- CuscAgent.startFespCarEngine 实际构建 AIFespCarConfig，设置波束处理模型 sspe/sspe_ula-bss-wkp_98.6mm_ch2_2mic_0ref_release-v2.0.0.115.bin 和唤醒模型 wakeup/wkp_aicar_dongfeng_peugeot_20220629_v1_vp20221214.bin，并启用自定义喂音频。属于有初始化调用路径的实现，仍未证明本次运行初始化成功。

## 回声消除与降噪

CuscAgent.init 实际创建 EchoKernel，并配置 EchoConfig：setChannels(4)、setMicNumber(2)、setAecResource(echo/sspe_aec_ch4_2mic_2ref_asr_release-v2.0.0.115_20220614.bin)，随后 setAIEchoConfig/newKernel。这比仅有模型文件更明确，但四通道/双麦是软件配置，不是实车硬件接线证明。

原车存在 SDK 内部回声处理路径，不能因为 AudioFlinger 的录音会话显示 0 Effect Chains 就认定没有 AEC。也不能由这些配置断言路噪降噪有效、或普通 MIC 录音已获得同样处理。尚未发现对外暴露处理后 PCM 的接口；本次没有进行完整全部代码的否定性审计。

此前运行快照：原车语音 PID 22056，VOICE_RECOGNITION，16 kHz、双声道、内置麦克风，采集 active，0 Effect Chains。只证明采集状态，不单独证明唤醒开关或唤醒成功。

## 声纹与播报音色

SpeechService.onBind 对声纹 action 还检查 CuscSpeechConstants.IS_CAN_VPRINT。此前会员中心已成功绑定此服务，是该设备有返回 Binder 的运行证据。

声纹服务实际实现支持 isCanUse、isRegister(name)、startRegister(name)、startLogin、startUpdate(name)、delete(name)，以及输入/登录结果回调。VprintManager.isCanUse 在声纹引擎已初始化且 StateManager.isCanInteraction(3) 时返回 0，否则 -1；-1 混合了初始化和场景限制，不能解释为硬件不支持。

普通应用是否能绑定及成功查询仍未验证。后续先仅调用 isCanUse，不枚举身份、不注册、不登录、不删除。

播报音色是另一组接口：语音管理服务有 getAllSpeaker/getSpeaker/setSpeaker，支持配置音色的代码路径；实际可用音色与普通应用调用结果尚未读取。

## 其他外部接口的边界

system.middleware.action.VoiceService 返回 InteractionExtraService（VoiceController）。它提供 requestVoiceRecorder 和 setVoiceActionListener，后者是应用打开/关闭及媒体动作通知，不是已确认的唤醒回调或 PCM 输出。

setVoiceActionListener 会替换单个现有监听器；此前桌面已绑定这条服务，所以只读探针不应注册此监听器。requestVoiceRecorder 会改变录音/交互状态，不属于只读查询。本轮未调用上述方法。

## 后续实机验证

1. 独立普通 UID 探针显式绑定两个管理 action，记录绑定和 descriptor，执行上表三项查询后立即解绑。
2. 同时区分 Binder 成功、后端未就绪与真实关闭；不自动修改唤醒设置。
3. 用户在车旁时确认主/附加唤醒词、实际触发以及原车与自研助手的麦克风竞争。
4. 分别实测原车链路与自研 MIC/VOICE_COMMUNICATION 的空调噪声和播放回声；不把库加载或静态配置当成效果验收。

原始 APK 和本地反汇编保存在 /tmp/psa-capability-check/，未放入仓库。该目录可能被系统清理。应用源码未修改，本轮未构建或安装 APK。
