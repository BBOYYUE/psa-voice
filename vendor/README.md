# 第三方研究依赖

Git 保存 QPlay IPC 1.1.6 小型 SDK 构件，供离线接口编译与校验；其来源及 SHA-256 见 docs/qplay-ipc/README.md，分发条款以官方 SDK 协议为准。

以下本机材料不提交 Git：
- Hermes-Agent-3af22c0.tar.gz：与服务器同版本的上游源码归档；版本及摘要见 deploy/hermes/runtime.json。可从上游 https://github.com/NousResearch/Hermes-Agent.git 的对应 commit 用 git archive 重建。
- qplay-ipc-demo/：腾讯音乐官方 Demo 的独立 Git checkout；仓库和固定 commit 见 docs/qplay-ipc/README.md。可重新 clone 并 checkout 对应版本。

真实凭证位于根目录 .private/，构建缓存、运行数据库、录音与安装包也不提交。
