# QPlay IPC 离线接入准备

此目录不参与生产 App 构建。QplayCarCalls 使用官方 1.1.6 SDK，封装 car 绑定 Intent、hi、搜索、按 ID 播放、按 MID 收藏、模式和事件，已做 javac 离线编译。

它不包含完整的连接/授权/重试/超时/回调解析生命周期。调用前必须由正式适配层完成授权、工作线程调度、请求取消和真实结果校验；不能直接替换当前 MusicExecutor。

根目录执行 `python3 tools/verify_qplay_sdk.py` 可复核。完整研究见 `docs/qplay-ipc/README.md`。
