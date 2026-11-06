# 防沉迷系统 / Child viewing controls

电视版 3.7.12 / code34 提供防沉迷系统。中文入口位于 **控制中心（GenTV 设置一级列表）→ 桌面主题切换下方 → 防沉迷系统**；英文入口为 **Control center → Child mode**。

## 开启和使用

1. 首次进入创建并确认 4–8 位数字家长密码，以后每次进入需验证。离开设置页后重新锁定。
2. 选择要管理的视频应用，可多选；选择计算、英语或混合题型和 1–6 级难度，先预览样题。
3. 观看间隔提供 5、10、15、20 分钟，默认 **10 分钟**。正式包不提供 30 秒测试入口。旧版选中 30 秒测试的配置在升级后恢复为 10 分钟，并清零该段观看累计；正常分钟配置保留。
4. 开启计时无障碍服务，并打开防沉迷模式。页面必须显示服务已连接、保护生效，自动计时才可用。模式开关与服务权限是两个状态。
5. 屏幕亮起且受管应用在前台时累计时长；受管应用之间切换继续累计，离开受管应用或待机暂停。播放器内的暂停可能继续计时。
6. 到点自动弹题，答对后解除本轮限制。连续答错会进入休息，家长可验证密码处理。是否恢复原播放进度由播放器自身行为决定。

忘记密码没有内置通用密码。请保管家长密码；不要把本功能作为不可绕过的系统级管控。

## 无障碍服务与本机 ADB

“开启计时服务”按钮可以在支持传统网络 ADB 的设备上连接本机回环地址辅助授权。用户需先开启调试、接受系统授权；未报告端口时填写设备实际显示的端口。App 不扫描端口、不启动 adbd、不自动获取 root，也不授予自身通用安全设置写入权限。既有其他无障碍服务会保留。

仅无线配对/TLS 调试、没有本机监听或厂商禁止相关写入的设备可能无法使用此路径，应使用固件提供的无障碍设置。按钮仅在服务实际绑定后显示已开启。完成授权后可关闭 ADB 调试；后台清理、强行停止、升级和固件策略可能影响服务，重启后应核对状态。

服务仅为识别受管应用前台状态和呈现题目使用无障碍能力，不上传题目、屏幕内容或前台应用列表。本地 ADB 密钥位于 App 私有且排除备份的目录；详见[隐私说明](../PRIVACY.zh-CN.md)。

## 题库与限制

计算题可离线使用；新增英语题库仍含待教学审校草稿，仅供家长预览。正式英语出题沿用已有回退题库，只有达到审校门槛的题目才纳入新的正式英语池。1–6 级是产品难度参考，不保证完整覆盖小学课标或适用于所有国家。

本功能需要 Android TV / Google TV 系统能力，不能保证覆盖所有厂商固件，也不能代替系统级家长控制。未经测试的强退、系统快捷入口、HDMI 输入和第三方应用行为仍可能绕过或影响限制。此版本不提供通用 Device Owner 部署入口。

## 验证记录

- [3.7.12 正式包默认 10 分钟实测](child-release-3.7.12-validation-2026-11-06.zh-CN.md)
- [Android 9、Android TV16、Google TV16 模拟兼容性矩阵](child-compatibility-matrix-2026-11-05.zh-CN.md)
- [早期自连、关闭 ADB 和重启验证](child-self-adb-validation-2026-11-05.zh-CN.md)；其中 30 秒结果来自旧候选或独立 QA 构建，不是当前正式包可选设置。
- [离线题库说明](child-question-bank-v1.zh-CN.md)

English: default viewing interval is 10 minutes; available intervals are 5/10/15/20 minutes. Automatic questions require a connected child accessibility service. Local legacy ADB activation remains subject to Android authorisation and vendor support. These controls are not tamper-proof system parental control. New English question drafts are preview-only until reviewed; grade levels are difficulty references.
