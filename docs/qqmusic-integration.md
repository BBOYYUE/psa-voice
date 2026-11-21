> 后续普通 App 实测修正：CLEAR_TASK 在 QQ 3.16.0.1 的热运行状态下仍可能产生空白页。当前实现已改为先启动 AppStarterActivity 并传 KEY_SWITCH_APP_SCENE=true，再发送第三方 action=8、Base64 search_key、m1=true。普通 App 搜索播放及实际音乐键语音请求均已验证成功。以下保留早期排查记录。
>
> 2026-11-22（0.3.0）：按官方《外部拉起 QQ 音乐车机版协议》（[external.md](https://y.qq.com/music_developer/static/docs/car/external.md)，v1.9.8）接入 action=0~9。实现沿用同一已验证传输通道（显式 `DispacherActivityForThird` + `action` int extra），页面映射：recent=2、local=3、favorite=4、car_playlist=5、radio=6、rank=7、my_playlist=9；`m1`（进入后直接播放，默认 false）与 `mb`（固定 true，返回键回 launcher）作为 boolean extra 传递。action=0、1、2~7、9 的发送代码已完成，页面实际打开与 m1 效果待车机实测；官方推荐的 `qqmusiccar://` scheme 方式未迁移，避免破坏已验证行为。服务端/客户端均不允许 LLM 直接输出协议数字、Activity 或 URI。

# QQ 音乐车机版搜索接口验证

验证日期：2026-11-21。设备：3VS121 / Android 9。QQ 音乐包名 `com.tencent.qqmusiccar`，版本 `3.16.0.1`（3160001）。

## 已确认的第三方搜索入口

显式 Activity：`com.tencent.qqmusiccar.app.activity.DispacherActivityForThird`（exported=true，无组件权限要求）。

Intent action：`com.tencent.qqmusiccar.action`。

| Extra | 类型 | 含义 |
| --- | --- | --- |
| action | int | 8：搜索 |
| search_key | String | 搜索词的 UTF-8 字节经标准 Base64 编码，不能传原始中文 |
| m1 | boolean | false：显示搜索；true：请求自动播放 |

`AppStarterActivity.dispatcherThirdAction()` 会将 `search_key` Base64 解码为 UTF-8，再写入 `search_word`。`m1` 经第三方入口转为 `direct_play`。搜索歌曲页自动播放逻辑取歌曲结果中的第一项，不保证与用户意图精确匹配。

## 实测

- 热运行时，`action=8, m1=false` 成功显示“周杰伦 晴天”等搜索结果，原有音乐状态不变。
- `m1=true` 在已打开的搜索页内重复请求时，多次只刷新搜索结果，未切歌。不是简单的 Intent 失败：Fragment 参数中已收到 `direct_play=true`。
- 重新启动 QQ 音乐后，同一请求成功自动播放周杰伦《晴天》。媒体会话显示目标歌名/歌手及 STATE_PLAYING，播放器进度前进，AudioTrack 为 started。
- 因热运行复用问题，不能把这个协议目前描述为“随时可靠自动播放”，也不应每次 force-stop 作为正式实现。
- 同一 QQ 进程 PID 8955 内，通过 `AppStarterActivity` 携带 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`（0x10008000）、`open_the_first_page=8`、Base64 `search_key`、`direct_play=true`，成功由《晴天》自动切到朴树《平凡之路》。这会重建 QQ 界面任务栈，但无需杀进程，不清应用数据。仅验证一次，仍需在多次连续调用中验证。
- 普通 App 发起调用尚未单独实测；上述调用由 ADB 发起。组件导出配置表明可尝试普通显式 Intent，但仍需在我们的 App 中验证。
- 标准 `MEDIA_PLAY_FROM_SEARCH` 查询返回 No activity found；会话 actions=2360311，未声明 ACTION_PLAY_FROM_SEARCH。

## Hermes 与车机的建议协议（尚未实现）

Hermes 返回语义动作，车机端处理 Base64 与 QQ 音乐协议，不让模型生成 Android Intent 或 ADB 命令。

```json
{
  "request_id": "req-001",
  "action": {
    "type": "music.search",
    "app": "qqmusic",
    "query": "周杰伦 晴天"
  }
}
```

需要自动播放时使用 `music.search_play`，由车机端选择已验证适配策略。返回 `dispatched` 只代表发送；只有匹配目标歌曲并观察播放状态后才返回 `playing`。搜索展示与返回歌曲列表 JSON 是不同能力：本次入口显示 QQ 音乐 UI，不返回搜索列表给调用方。

首轮接入可用 `music.search`；自动播放可继续验证重建 QQ 界面任务的适配方式，并必须保留执行验证与失败结果，不应提前播报“已为你播放”。QQ 音乐账号、会员与网络决定最终可播放内容。
