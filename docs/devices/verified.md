# Verified devices 已验证机型

Real-device results for GenTV itself, one row per model and firmware. Unlike the [252 community cleanup references](README.md), every row here comes from someone running GenTV on that device, and failures are listed too.

这里记录 GenTV 本身在真实设备上的测试结果，每个机型和固件一行。和 [252 款社区清理参考](README.md)不同，这里的每一行都来自有人在该设备上实际运行过 GenTV，失败的结果也会列出来。

✅ works 正常 · ⚠️ works with limits 有限制 · ❌ fails 失败 · — not tested 未测试 · 🚫 the system has no such option 系统没有该选项

| Brand and model 品牌和型号 | System 系统 | GenTV | Install 安装 | Remote 遥控 | Default Home 默认桌面 | Home key 主页键 | Cleanup 清理与恢复 | Inputs 信号源 | Report 报告 |
|---|---|---|---|---|---|---|---|---|---|
| *No real-device results yet. Yours can be the first.* *还没有真机结果，欢迎你来提交第一份。* | | | | | | | | | |

## What each column means 各列含义

| Column 列 | Checked by 检查方法 |
|---|---|
| Install 安装 | GenTV installs and opens. 能安装并打开 GenTV。 |
| Remote 遥控 | The physical remote moves focus, opens apps and returns to GenTV with Back. 实体遥控器能移动焦点、打开应用，并能用返回键回到 GenTV。 |
| Default Home 默认桌面 | GenTV can be chosen in the system Home setting. 系统主屏幕设置里能选择 GenTV。 |
| Home key 主页键 | After choosing GenTV, the Home button, power-on and waking from standby all land on GenTV. 设为默认后，按主页键、开机、待机唤醒都回到 GenTV。 |
| Cleanup 清理与恢复 | Disabling an app from the phone works, and restoring it brings it back. 用手机停用应用后能用恢复功能还原。 |
| Inputs 信号源 | Input source shortcuts open the right HDMI or other input. 信号源快捷入口能打开正确的 HDMI 或其他信号源。 |

Emulator, unit-test and build results are not listed here. 模拟器、单元测试和构建结果不计入本表。

## Add your device 提交你的设备

- Everything worked: open a [Works on my TV 实测可用](https://github.com/Elaine-Lynn/GenTV/issues/new?template=works_on_my_tv.yml) issue.
- Something was limited or failed: open a [Device report 机型报告](https://github.com/Elaine-Lynn/GenTV/issues/new?template=device_report.yml) issue.

Don't include TV IP addresses, ADB keys, backups, account details or Wi-Fi names. A maintainer adds the row and links your issue in the Report column.

请不要填写电视 IP、ADB 密钥、备份、账号或 Wi-Fi 名称。维护者会添加对应的行，并在「报告」列链接你的 issue。
