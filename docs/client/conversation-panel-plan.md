# 连续语音面板技术评估（不实施客户端）

范围：SRC 唤醒/再次按下退出，提示“我在。”，会话支持音乐与闲聊，语音结束。
本轮仅确认技术方案，不修改 APK、工具箱、原厂 APK 或按键配置。

## 候选窗口

优先普通半透明 Activity：windowIsTranslucent + 透明窗口背景 + 半透明内容区域；
不依赖 SYSTEM_ALERT_WINDOW。原有 NoDisplay TriggerActivity 继续作为唤醒入口，未来拉起面板。
车机窗口合成、原车地图/视频底层是否可见、进出动效、后台限制仍需实机评估，不能保证透明效果。
麦克风、网络和会话状态属于前台服务；Activity 只订阅状态。面板重建不重放动作、不重新发起会话。

## 展示与数据

- 当前连接状态与会话状态分别显示，心跳仅证明连接可用。
- 同一 turn_id 的临时转写在同一处替换；final 后固定成一条用户消息。
- speech.index 对应增量回答片段，result.answer 为最终完整文本，不重复追加/播报。
- 动作展示具体 query，执行状态严格区分 confirmed / sent / failed。
- session_id 隔离唤醒会话，seq 单调递增；取消后旧 session 的事件全部丢弃。
- 服务端 processing 表示不接收新话语；客户端自身 TTS 队列状态决定“正在播报”。
  awaiting_execution/awaiting_resume 不能显示成“正在听”。
- 仅当前会话的内存历史，不持久化聊天和录音。

## 恢复监听

result 到达不等于播报结束。音乐动作先回执行结果；所有 TTS 片段播放结束且动作结束后，
回带正确 turn_id 的 resume，收到 listening 再展示可说话。
首版不支持语音打断，SRC 取消立即停本地 TTS/录音/队列并使会话失效，再通知服务端 cancel。
服务端 error.recoverable 决定可否恢复本轮后的监听；ended 或断线清理整个会话。

## 待实机确认

SRC 原始事件及原行为能否消费；音频焦点与音乐压低；TTS 完成回调；
音乐播放后的拾音与误触发；窗口透明效果；Activity 被销毁后的服务生命周期。
