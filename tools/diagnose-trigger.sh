#!/bin/sh
# 诊断“长按音乐键闪一下”的来源。
# 用法：先 adb connect 192.168.43.8:5555，然后运行本脚本，在 10 秒窗口内
# 完整地做“一次短按”和“一次长按”。结束后对比两段日志：
# - 短按只应出现 qqmusiccar 的启动记录；长按只应出现 psavoice 的启动记录。
# - 如果长按时也出现 qqmusiccar 启动记录，说明工具箱没有抑制短按动作（嫌疑1）。
# - 如果长按时只有 psavoice 启动记录，闪帧来自我们自己的入口（嫌疑2）。
set -e
adb -s 192.168.43.8:5555 logcat -c
echo ">>> 现在请短按一次音乐键（5 秒窗口）..."
sleep 5
adb -s 192.168.43.8:5555 logcat -d | grep -E "START u0|ActivityTaskManager.*(qqmusiccar|psavoice)" > /tmp/psa-short.log || true
adb -s 192.168.43.8:5555 logcat -c
echo ">>> 现在请长按一次音乐键（8 秒窗口）..."
sleep 8
adb -s 192.168.43.8:5555 logcat -d | grep -E "START u0|ActivityTaskManager.*(qqmusiccar|psavoice)" > /tmp/psa-long.log || true
echo "==== 短按窗口的启动记录 ===="; cat /tmp/psa-short.log
echo "==== 长按窗口的启动记录 ===="; cat /tmp/psa-long.log
echo "==== 工具箱按键绑定 ===="
adb -s 192.168.43.8:5555 shell 'getprop | grep -i bandwa'
