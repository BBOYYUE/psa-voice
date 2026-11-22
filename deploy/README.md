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

`server-source.sha256` 记录服务器 10 个核心源码及配置文件的 SHA-256。2026-11-22 第三次部署（追问上下文显式清除接口 `POST /v1/music/context/clear`）后已与线上重新核对一致。本次部署前备份在服务器 `backups/20261121T194224Z-clarify-clear/`；更早的备份在 `backups/20261121T192950Z-knowledge-clarify/`（知识派+流式）与 `backups/20261121T182647Z-open-page/`（action=0~9）。源码同步不包含本地新增说明文档。
客户端构建：在项目根执行 `bash ./gradlew assembleDebug lintDebug`，需 JDK 17/21、Android SDK 34。
服务端测试：在 server 目录执行 `venv/bin/python -m unittest discover -s tests -v`。
