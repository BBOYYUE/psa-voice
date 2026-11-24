# 车机能力与音频诊断工具

## 独立只读探针

构建：`bash ./gradlew :capability-probe:assembleDebug :capability-probe:lintDebug`。
APK：`capability-probe/build/outputs/apk/debug/capability-probe-debug.apk`。

安装后从桌面打开“PSA 只读能力查询”，手动点击查询。包名 com.sollado.psaprobe，不覆盖助手，无麦克风/网络权限。仅显式绑定原车两个管理服务，使用已反汇编核对的 getter 事务，不注册回调、不调用设置或声纹操作。BIND_AUTO_CREATE 可能启动原车服务，查询结束/页面销毁/8秒超时解绑。同步 Binder 调用不能被超时强制中止；每次打开页面只允许发起一轮查询。

查询内容：唤醒开关、附加/自定义唤醒词、声纹 isCanUse、可选播报音色。音色按本车 SpeakerBean 的五个字符串字段解码，不兼容版本须停止解释结果。仅针对已分析固件；descriptor 相同不保证未来固件的事务编号和格式兼容。

false、空词表、空音色列表可能是原车后端未就绪。声纹 0 是初始化且当前场景可用，-1 包含初始化失败和场景受限。查询成功不等于唤醒/声纹实测通过。

## 助手 debug 音频测试页

构建：`bash ./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`。
需安装此轮 app-debug.apk 才包含入口；当前车机安装版未更新。

通过 ADB 打开（仅 debug，入口要求 DUMP 权限）：

```sh
adb shell am start -n com.sollado.psavoice/.AudioDiagnosticActivity
```

先结束助手会话，再手动选择 MIC / VOICE_RECOGNITION / VOICE_COMMUNICATION。第一次授权麦克风后需再次点“开始5秒测试”。默认不显式创建效果；系统/HAL 对音源的默认处理仍可能生效。勾选后仅在本次 session 创建并尝试启用 AEC/NS/AGC，分别显示可用性、控制权、setEnabled 返回码及 enabled 状态。

采样为16kHz单声道PCM16；最多5秒，只显示RMS/峰值和效果描述，不存储、不上传、不播放。点击停止或离开页面停止采集，释放录音及效果对象。不改变正式 PcmRecorder/VoiceRecorder 的音源或配置。

建议在车旁停车测试：安静、空调、音乐播放，各按三个音源比较基线；有可用效果时再对比显式开启。该页不能给出识别准确率、AEC衰减量或音质结论，需结合后续有人在场的听感/识别测试。开启效果不等于有效消除回声，也未确认 VOICE_COMMUNICATION 对全车音频路由/音量是否有额外影响。

## 本轮本地验证

两个 debug APK 构建、两个模块 lintDebug、现有 app testDebugUnitTest 通过。未安装到车机，普通 UID Binder 调用、效果启用和真实音频路由仍未验证。
