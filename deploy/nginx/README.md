# 反向代理

公网入口为 https://ai.sollado.com，语音网关监听 127.0.0.1:8650。
服务器站点配置在 `/etc/nginx/sites-available/ai.sollado.com`，由 sites-enabled 中同名软链加载，需要 root 维护。

2026-11-23 在现有 TLS server 中添加 `location = /v1/live`，支持 WebSocket Upgrade，关闭缓冲，读写超时 30 秒。
配置片段见 `live.location.conf`；其他 HTTP 路径和证书配置保留。
修改后 `nginx -t` 通过再 reload；公网已验证无凭证拒绝、音频增量转写、多轮回答、恢复与取消。

原始配置备份：`/etc/nginx/sites-available/ai.sollado.com.before-live-20261123T090300Z`。
迁移到其他机器时仍需单独配置 HTTPS 证书；私钥和证书不随源码保存。
