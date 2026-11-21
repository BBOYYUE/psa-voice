# Voice Gateway：车机音乐语音接口

服务地址：`https://ai.sollado.com`。代码目录：`/home/sollado/projects/voice-gateway`。
车机录音上传 → 本地 sherpa-onnx Paraformer 转写 → Hermes voice profile → 校验后的音乐动作。

## 客户端协议

所有业务接口需要 `Authorization: Bearer <VOICE_GATEWAY_TOKEN>`。凭证只存服务器 `.env` 与客户端私有配置，不放代码仓库或日志。

主入口 `POST /v1/voice` 使用 multipart：

| 字段 | 内容 |
| --- | --- |
| `file` | WAV 文件，优先 PCM 16-bit、16 kHz、单声道；上传原始录音，不上传回放增益副本 |
| `scene` | `music`，音乐键入口固定传此值 |
| `preferred_app` | `qqmusic`，可省略 |
| `trigger` | `music_key_long_press`，仅用于记录入口语义 |
| `device_id` | 稳定设备标识，例如 `car-001`，非硬件敏感标识也可 |
| `request_id` | 每次交互唯一的 1–64 位字母/数字/下划线/短横线；可省略由服务器生成 |
| `session_id` | 可选；需要多轮上下文时使用；音乐上下文与通用问答会话隔离 |

也可传 `context` JSON，含 `scene/preferred_app/trigger`；与顶层重复时顶层优先。调试可以 `POST /v1/text` 发送相同字段和 `text`，无需音频。原始音频 body 方式仍支持，上下文通过 query 参数传递。

```json
{
  "text": "周杰伦的晴天",
  "scene": "music",
  "preferred_app": "qqmusic",
  "device_id": "car-001",
  "request_id": "request-001"
}
```

音乐场景成功返回 schema v1：

```json
{
  "ok": true,
  "schema": "v1",
  "request_id": "request-001",
  "scene": "music",
  "transcript": "周杰伦的晴天",
  "decision": "execute",
  "action": {
    "type": "music.search_play",
    "app": "qqmusic",
    "query": "周杰伦 晴天"
  },
  "answer": "",
  "meta": {"asr_ms": 168, "hermes_ms": 1700, "duration_s": 3.9}
}
```

`answer` 在 execute 时强制为空：服务器只解析指令，尚未在车机执行，不能提前说“已播放”。客户端必须确认 QQ 音乐状态后再播报结果。`request_id` 用于客户端防重：同一 ID 的动作只执行一次；服务端目前不缓存重复请求。

| action.type | 参数 | 客户端含义 |
| --- | --- | --- |
| `music.search_play` | query | 搜索并尝试播放；只说歌名/歌手时默认此动作 |
| `music.search` | query | 只显示搜索结果，不自动播放 |
| `music.pause` | 无 | 暂停 |
| `music.resume` | 无 | 继续播放 |
| `music.next` | 无 | 下一首 |
| `music.previous` | 无 | 上一首 |

信息不够时返回 `decision=clarify`；不支持的任务返回 `decision=unsupported`。两者 `action=null`，`answer` 是可播报的中文提示。收藏、购买、下载、车控、导航、音量调整尚未开放。

模型输出必须符合白名单、参数和长度约束。非法 JSON 或未知动作返回 HTTP 502、`error.code=invalid_intent`、`action=null`，客户端不得猜测执行。

`GET /v1/capabilities` 返回动作、音频格式与保留策略。`GET /health` 无需凭证。

## 原有接口兼容

未传 scene 或 `scene=general` 仍返回原来的 schema v0 通用问答结构。
`POST /v1/transcribe` 只返回转写，不调用 Hermes。
音频默认最大 25 MB，ffmpeg 最多解码前 120 秒；车机单次录音建议不超过 10 秒。

## 录音与日志清理

- 上传文件和 ffmpeg 中间 WAV 都只放本服务的 `.audio-tmp/` 私有目录。
- ASR 完成后、调用 Hermes **之前**删除文件；成功、异常、取消请求均走清理。
- Multipart 临时上传文件通过 form 上下文关闭；流式请求也受体积限制。
- 启动时及每 60 秒扫描服务自己的 `request-*` / `decode-*` 子目录，删除超过 30 分钟的崩溃遗留文件；跳过活跃目录、未知目录及符号链接，不扫描或清理共享 `/tmp`。
- ASR 执行失败也不长期保存录音；没有开启音频归档。服务日志只记录请求元信息、耗时和错误类型，不记录音频内容或凭证。
- `logging.json` 将 `service.log` 轮转为每份最多 5 MiB，保留 3 份历史，合计约 20 MiB。不要再用 systemd 的 append 文件方式输出到同一日志。

可调配置：`VG_AUDIO_TEMP_DIR`、`VG_AUDIO_TEMP_TTL_S`（最少 300 秒）、`VG_AUDIO_CLEANUP_INTERVAL_S`。

## Hermes

使用独立 voice profile：`http://127.0.0.1:8643`，不改动默认 Hermes 网关。
音乐指令超时默认 30 秒，可通过 `VG_MUSIC_HERMES_TIMEOUT_S` 调整。通用问答保留原超时配置。
模型、凭证、ASR 文件保留原部署配置。

## 运维与验证

```sh
systemctl --user status voice-gateway.service hermes-voice.service
systemctl --user restart voice-gateway.service
venv/bin/python -m unittest discover -s tests -v
venv/bin/python verify_chain.py
```

`tests/test_contract.py` 只使用模拟 Hermes/ASR，覆盖错误动作拒绝、兼容性、鉴权/输入限制、正常/失败/取消清理以及崩溃清理边界，不调用付费模型。
`verify_chain.py` 是原有真实链路检查，会请求 Hermes 并读取服务器上已有的测试音频。

部署前将原代码、`.env`、服务单元备份到服务器 `backups/`，备份目录权限 700；部署脚本校验原文件哈希以免覆盖并发修改。模型和真实凭证不下载到本地代码目录。

### 2026-11-21 语义初始化超时修复

车载专用 Hermes profile 的 `model.context_length` 固定为保守的 `65536`。此前日志出现模型调用前初始化等待 15–64 秒，而模型请求本身约 1 秒，导致音乐接口 30 秒超时；设置后连续测试恢复约 1–2 秒，首次冷启动约 8 秒。只修改 voice profile，备份保存在其配置同目录。音乐请求不再发送 `X-Hermes-Session-Id`，避免旧会话中未执行的播放请求干扰当前暂停等指令；通用会话仍保留原有行为。

### 2026-11-21 延迟优化

voice profile 的 `agent.reasoning_effort: none` 显式关闭 DeepSeek 思考模式；`providers.deepseek.request_timeout_seconds` 和 `stale_timeout_seconds` 为 12 秒，`agent.api_max_retries: 1`。音乐精确控制指令直接解析，复合指令仍走 Hermes；成功解析结果使用 128 条、10 分钟内存 LRU 缓存，不保存音频，服务重启清空。快路径和缓存返回 `hermes_ms=0`；日志记录 route、request_id、ASR 和语义耗时。11 项契约及缓存检查通过。
