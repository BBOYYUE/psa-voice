# 本地代码与部署配置

根目录：`/Users/genee/my_files/my_hermes/tools/psa-voice`。

- `app/`：Android 助手客户端，0.5.0，包含 Gradle 构建入口。
- `server/`：当前部署的语音识别、音乐语义分析、Hermes HTTP 客户端、缓存与清理逻辑，含测试和 `requirements.lock.txt`。
- `deploy/systemd/`：服务器实际使用的两个 user service 单元。
- `deploy/hermes/`：车载 voice profile 的脱敏配置模板、SOUL.md 和运行版本信息。
- `deploy/client-server.example.json`：车机私有 `files/server.json` 的配置模板。
- `vendor/Hermes-Agent-3af22c0.tar.gz`：服务器同版本、干净工作树的上游 Hermes 源码快照；解压后为 `Hermes-Agent/`。
- `.private/server/`：实际 `gateway.env`、`hermes-voice.env` 和 `hermes-voice.config.yaml`。目录权限 700，文件权限 600，已被 Git 忽略。这里不保存 SSH 登录密码。

模板中的 `<configure-separately>` 需要单独配置；不要直接拿模板覆盖线上真实配置。真实配置只用于本人本机备份与恢复，不要提交或分享。

## 当前部署映射

语音网关：`/home/sollado/projects/voice-gateway`，Python 3.11，`voice-gateway.service`，监听 `127.0.0.1:8650`。
Hermes：`/home/sollado/Hermes-Agent`，commit 见 `hermes/runtime.json`，`hermes-voice.service` 使用 profile `voice`，监听 `127.0.0.1:8643`。
profile 路径：`/home/sollado/.hermes/profiles/voice`。保留与其他 Hermes profile 的隔离。

网关 Python 依赖：进入 `server/`，执行 `python3.11 -m venv venv` 后用 `venv/bin/pip install -r requirements.lock.txt`；系统还需要 ffmpeg。ASR 模型由 `server/fetch_model.py` 对照实际模型目录准备，不复制运行中的模型文件、日志、会话数据库或录音。
Hermes 源码使用上游自身的安装流程，先在其目录安装依赖，再放回 voice profile 配置和单独配置的凭证。

两个 service 单元保留服务器的绝对路径；部署到其他机器须调整 ExecStart、WorkingDirectory 和相关路径。
Nginx 的读取限制见 `nginx/README.md`。

## 校验

`server-source.sha256` 记录服务器当前 16 个源码、测试及说明文件的 SHA-256。2026-11-23 连续会话版本部署并完成公网验证后，已与线上重新核对一致。备份与回退方法见下方连续会话部署记录。
客户端构建：在项目根执行 `bash ./gradlew assembleDebug lintDebug`，需 JDK 17/21、Android SDK 34。
服务端测试：在 server 目录执行 `venv/bin/python -m unittest discover -s tests -v`。

## 2026-11-23 连续会话服务端

已部署 `/v1/live` 至原网关，公网 WSS 通过 Nginx 精确 location 转发；配置片段见 `nginx/live.location.conf`。
服务端加载在线中英 Zipformer int8 模型，位于网关 `models/streaming-zipformer-zh-en-int8/`。
实际 `.env` 启用 VG_LIVE_ENABLED 并指定四个模型文件，敏感配置未复制到仓库。
启动预热后 `/health.live` 应显示 enabled/configured/ready 全为 true。

部署脚本 `install-live.py` 要求已通过测试的 staging 目录和已核对的 app.py 哈希；
替换前备份源码及 `.env`，后者仍为权限 600。不会修改 Hermes、客户端或按键绑定。
原始版本备份：`/home/sollado/projects/voice-gateway/backups/20261123T090300Z-live`。
Nginx 原始备份：`/etc/nginx/sites-available/ai.sollado.com.before-live-20261123T090300Z`。

语义退出与最终转写重字复核的发布前源码备份为 `/home/sollado/projects/voice-gateway/backups/20261123T142629Z-exit-asr`。
公网验收覆盖自然退出、带后续请求的继续对话、重字复核和原 HTTP 转写接口。
2026-11-24 两次连续重字也进入离线复核；服务器 40 项测试及公网 WSS 回归通过。
发布前备份为 `/home/sollado/projects/voice-gateway/backups/20261124T131709Z-double-repeat`。

源码回退（服务器网关目录执行）：

```sh
venv/bin/python staging/live-20261123/install-live.py --rollback /home/sollado/projects/voice-gateway/backups/20261123T090300Z-live
```

完整回退公网路由还需以 root 恢复上述 Nginx 备份，`nginx -t` 通过后 reload。
公网验证脚本为本地 `tools/verify_live.py`，从 VOICE_GATEWAY_TOKEN 环境变量读取凭证，只发送测试音频，不执行音乐动作。
实际结果及当前边界见 `server/LIVE.md`。客户端半透明界面技术评估见 `docs/client/conversation-panel-plan.md`，尚未实施。
