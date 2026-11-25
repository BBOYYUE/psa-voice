# 2026-11-25 安装与原车只读查询

通过 ADB 安装当前构建主助手 com.sollado.psavoice（0.6.0/versionCode 13）和独立探针 com.sollado.psaprobe。旧 com.genee.psavoice 保留并停止；私有 server.json 仅经内存迁移，校验内容一致且目标权限600，未输出凭证。麦克风已授权，通知监听旧助手项替换为新助手项，其他监听器保留。音乐长按属性已切换 com.sollado.psavoice；没有修改 SRC 厂商分发或原厂 APK。

独立普通 UID 探针实际成功绑定 IVoiceManagerService 和 IVprintManagerService，查询后已解绑。

| 查询 | 实机结果 | 边界 |
| --- | --- | --- |
| isVoiceWakeupOpened | 1 | 原车设置为开启，未测试实际唤醒 |
| getWakeupWords | 空字符串 | 附加/自定义词为空，不能解释成没有默认唤醒词 |
| isCanUse | 0 | 声纹当前初始化且场景可用，未注册或验证个人声纹 |
| getAllSpeaker | 7项 | 只读列表，未切换或试听 |

音色：阳光男声(value=4)、绅士男声(3)、甜美女声(1)、温柔女声(2)、可爱童声(5)、乖巧童声(6)、个性女生(7，typeName=情绪)。

本轮未启动主助手会话或音频测试，没有录音、播放、声纹注册、设置唤醒词、切换播报音色。主助手实际对话和物理按键仍需用户联调。

后续按用户要求卸载旧包 com.genee.psavoice，ADB 返回 Success；当前只保留 Sollado 主助手与探针。SRC 接管研究已停止，原车切源行为保持原样；未配置自定义语音唤醒，未录入声纹。
