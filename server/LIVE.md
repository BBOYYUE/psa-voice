# 连续语音会话（本地实验，默认关闭）

目标：SRC 唤醒后播报“我在。”，多轮音乐/闲聊共用会话，语音退出或再次按 SRC 取消。
此处只实现服务端和 WAV 测试入口，尚未接入 Android/SRC，不替换现有 HTTP 协议。

## 本地启动

新增模块复用 requirements.lock.txt 中的 sherpa-onnx、httpx、websockets。
当前离线 Paraformer 文件不能用于在线识别。需要独立在线 transducer 模型。
本次接口按 sherpa-onnx OnlineRecognizer.from_transducer 核对：
https://github.com/k2-fsa/sherpa-onnx/blob/master/python-api-examples/streaming_server.py

可用于首轮验证的中英模型（并非已确定生产模型）：
https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2

在私有环境中设置，不在命令行传 token：

- `VOICE_GATEWAY_TOKEN`：沿用网关鉴权
- `VG_LIVE_ENABLED=true`：显式启用实验端点
- `VG_LIVE_ASR_ENCODER`、`VG_LIVE_ASR_DECODER`、`VG_LIVE_ASR_JOINER`、`VG_LIVE_ASR_TOKENS`：对应模型文件的绝对路径
- `VG_LIVE_ASR_SILENCE_S`：尾静音秒数，默认 0.8，可调范围 0.3–2.0
- Hermes 沿用 `VG_HERMES_BASE_URL` / `VG_HERMES_API_KEY` 等配置

在 server 目录用已安装依赖的 Python 运行 `python -m uvicorn app:app --host 127.0.0.1 --port 8650`。
探针：`python tools/live_probe.py --url ws://127.0.0.1:8650/v1/live first.wav second.wav`。
探针只显示事件，不执行音乐；收到动作时如实返回 failed。

生产必须使用 WSS；Nginx Upgrade 配置片段见 deploy/nginx/live.location.conf。
启用后服务启动会预热在线模型，/health.live 区分 configured、enabled 和 ready。

## 协议

连接 `/v1/live`，握手头 `Authorization: Bearer ...`，不接受 URL 中的凭证。
第一帧：

```json
{"event":"start","format":"pcm_s16le","sample_rate":16000,"channels":1,"client_version":"0.5.0"}
```

服务端 `ready` 后发送二进制 PCM：单声道、16 kHz、16-bit little endian，不含 WAV 头。
建议每 100 ms 发送 3200 字节，每帧最大 6400 字节（200 ms）。
持续发送包括静音在内的音频，端点判断依赖这部分音频，不能停发后等服务端猜停顿。

下行事件均包含 session_id、单调递增的 seq、当前 state 和连接内 elapsed_ms：

| event | 含义 |
| --- | --- |
| ready / listening | 可以发送有效输入 |
| transcript | 临时/最终转写，包含 final 和 turn_id |
| stage | understanding / generating |
| heartbeat | 每三秒连接心跳，包含 state，不等于模型确有进展 |
| speech | 从正在生成的 answer 字段提取的完整短句，可排队播报 |
| result | 最终 decision / action / answer、agent_ms、first_speech_ms、speech_count |
| execution_ack | 已接收音乐执行结果 |
| error / ended | 失败或会话结束 |

临时转写不触发动作。最终转写后调用 agent；动作完整校验后才下发。
流式 speech 可能已播出后模型最终输出失败，客户端收到 error 应停止尚未播出的短句。
result.answer 是完整回答，不应重复播放已通过 speech 播出的内容。

收到 result 后先完成播报/执行，再发送 `{"event":"resume","turn_id":1}` 恢复听取，turn_id 必须对应刚完成的一轮。
音乐动作必须先回 `{"event":"execution_result","turn_id":1,"status":"sent"}`，status 只能为 confirmed/sent/failed；
不能把 sent 当作 confirmed。会话记录只接收枚举结果，不接收可注入的任意执行文本。
处理中/播报中收到的音频被丢弃，首版不支持语音打断；SRC 可随时发 `{"event":"cancel"}`。
客户端取消时也必须立刻停止本地 TTS、清除待执行动作，并忽略迟到事件。
状态包括 starting、listening、processing、awaiting_execution、awaiting_resume、ended；
客户端根据本地 TTS 播放队列另外显示“正在播报”，不能把等待恢复状态显示为正在听。
error 带 recoverable，恢复时也须回正确 turn_id；错误或断线后不重放旧动作。

客户端无音频发送时应至少每五秒发送 `{"event":"ping"}`；十秒无入站消息以 connection_timeout 结束。

确认的转写精确命中“结束对话”“退出助手”“不聊了”时直接结束，不调用 agent。
最长会话十五分钟；监听状态六十秒无人声退出；等待音乐执行结果三十秒超时；
等待播报/恢复监听最多一百二十秒。所有超时都返回明确 ended.reason，不只发送心跳。暂限一个实时会话，避免两核主机推理堆积。
关闭连接后不恢复、不重放旧动作；重新唤醒会生成新 session_id。

## 当前边界

- 在线声学端点规则先取 0.8 秒非空语音后的尾静音；仅对少数明显不完整的前缀延长等待。
  不是已完成的语义端点模型，不能保证自然停顿不被截断。
- agent 使用 Hermes 的真实 SSE 接口，沿用单个 JSON 结果协议。网关仅在 decision 为 answer/clarify/unsupported 时，
  从增量 answer 字符串中提取完整短句；不会根据部分 action 执行。若字段顺序不利于增量提取，完成后补发回答。
  已在当前线上 Hermes 完成音乐/闲聊上下文和逐句输出联调。
- 音频只在内存送入在线解码器，不创建 WAV，不做录音归档；上下文只保留当前连接最近记录。
- 未实现车内回声消除、播放中拾音、SRC 消费、Android TTS 分句队列。
- 默认关闭；不得把模拟协议测试通过视为真实 ASR/车机体验通过。

测试：server 目录执行 `python -m unittest discover -s tests -v`。

## 2026-11-23 本地验证

- 27 项测试通过：原有 20 项接口/契约检查，新增 7 项实时会话与流式输出检查。
- 官方中英模型的四段样例（约 4.7–10 秒）均在逐帧输入时产生增量转写；
  本机非实时回放解码约 0.85–1.79 秒，含补入尾静音，不代表线上两核主机延迟。
  首轮测量使用 0.6 秒端点阈值，其中一个样例被分成两轮；默认已调整为可配置的 0.8 秒。
- 真实在线解码器经过 WebSocket 路由输入样例，收到 10 次临时转写、最终转写、回答短句、最终结果，恢复监听与取消均通过。
  此项 agent 使用桩响应；不代表线上 Hermes 已联调通过。
- Hermes 本地 vendor 快照的 API Server 源码确认支持 SSE delta、多轮 messages、断连中断。
- 尚未测试真实 Hermes 对早期 NDJSON 协议的遵守情况（已在后续实现中移除该要求）、T-ARA 的真人发音、服务器负载和车内噪声。

## 2026-11-23 服务器部署验证

部署主机：39.100.229.171；入口 `wss://ai.sollado.com/v1/live`。
在线模型已启用并在网关启动时预热，旧 HTTP 接口继续工作；客户端未更新。

- 本地和服务器各 37 项测试通过，包含 Unicode/转义分片、完整短句提取、坏格式拒绝、阶段超时、过期恢复消息和取消。
- 真实 Hermes 测试覆盖 T-ARA 搜索、跟进国家问题、改搜周杰伦、普通词义问答、三句介绍和流式取消。
- 公网 WSS 使用官方音频样例连续两轮：每轮 10 次临时转写和一次最终转写、两段回答、一个最终结果，恢复监听和取消通过。
  无凭证握手返回 403。
- 公网预热后就绪约 151 ms；样例发送结束到最终转写分别 1410/1193 ms；
  最终转写到首句回答分别 1532/1033 ms；agent 完整结果分别 1619/1106 ms。
  这些是两次样例测量，不是车内实际说完后的延迟保证。
- 旧接口 smoke 和真实音频 e2e 回归返回 HTTP 200；缺少鉴权仍返回 401。
- 逐句 NDJSON 曾在公网测试中出现无效格式，最终实现已取消该模型输出要求，改为单个 JSON + 网关增量读取 answer。
- 仍未验证真人 T-ARA 发音、车内噪声、播放中拾音、SRC 接管与客户端界面。

原始部署前备份：`/home/sollado/projects/voice-gateway/backups/20261123T090300Z-live`。
Nginx 原始备份：`/etc/nginx/sites-available/ai.sollado.com.before-live-20261123T090300Z`。
后续修正也各自保留了备份，以备检查。恢复原始版本用 install-live.py 的 rollback 功能，
如需完全回退公网入口，还应恢复 Nginx 原始文件并校验、reload。
