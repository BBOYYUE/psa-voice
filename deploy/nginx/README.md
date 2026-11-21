# 反向代理

公网入口为 https://ai.sollado.com，语音网关监听 127.0.0.1:8650。
服务器当前站点配置在 /etc/nginx/sites-enabled/ai.sollado.com，普通 SSH 用户无法读取，本次未复制该文件，也未修改线上 Nginx。
迁移时需为目标域名配置 HTTPS，并将请求反向代理至网关；TLS 私钥和证书不随源码保存。
