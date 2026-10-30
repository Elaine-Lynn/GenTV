# 设备操作流程

## 1. 目标、环境与偏好

确认用户指定的电视 IP/序列号、型号、清理/安装/恢复范围。检查 `adb version`、JDK、SDK；构建时遵循仓库指南。用户手动开调试、接受授权。所有 adb 操作带 `-s SERIAL`，不自动选择第一台设备。只连接设备显示的端口。

一次询问常用流媒体、直播/HDMI、语音助手、Chromecast/Miracast、更新服务、程序坞应用顺序、是否有 USB 外设/USB 网卡和路由器 DNS 过滤。保留用户选择，不把没有回答视为同意关闭。桌面主题通过 GenTV 已支持的配置/通信协议设置，先读 `docs/desktop-protocol.md`；使用本地协议，不套用其他桌面的配置。

读兼容性参考，记录型号、主板、固件与限制。对有匹配成功记录的机型，用户界面统一显示“已兼容”；不增加来源或验证状态标签。内部仍保留来源提交、机型和固件条件。该标签不等于本次设备已经操作成功，也不能替代后续验收。没有匹配记录时说明尚无匹配资料，不伪造兼容记录。

## 2. 私有备份与恢复入口

创建唯一的 `work/tv-care/<时间>/` 目录，目录权限 700，文件 600。日志和备份不提交。保存以下实际输出（不向聊天粘贴完整敏感内容）：

```sh
adb -s SERIAL shell getprop
adb -s SERIAL shell pm list packages -f
adb -s SERIAL shell pm list packages -d
adb -s SERIAL shell settings list global
adb -s SERIAL shell settings list secure
adb -s SERIAL shell settings list system
adb -s SERIAL shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s SERIAL shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

失败/空输出不能当备份完成。记录明确的原 Home 组件及其包启用状态；选择器或多 Home 时先确认原桌面。记录设备 fingerprint 以避免把恢复记录用到另一台设备。备份设置是参考，不代表可以整表回灌。

电脑端先运行 `tools/tv_care.py backup --serial SERIAL --session SESSION` 建立新会话。所有写操作带相同 SESSION，默认计划；具体动作已授权后加 `--apply`。适配器复用 FileActionLog，修改前 fsync intent，修改后核对实际状态，断线后先对账。

恢复入口统一为 `python3 tools/tv_care.py restore --serial SERIAL --session SESSION`；默认计划，授权覆盖本次恢复后加 `--apply`。不再要求临时生成另一套恢复脚本。会话绑定有效硬件身份或经用户确认建立的设备标记及固件签名，保留最初禁用项、default/enabled 状态及设置缺失值；恢复不卸载 GenTV，也不清其他会话的撤销记录。备份不是固件或用户数据镜像。

## 3. 清理预装与广告服务

从真实包列表分类：厂商遥测、广告/推荐内容、可选后台服务、预装应用、用户常用应用。对候选包查 `dumpsys package PACKAGE` 的活动、服务、权限及 PERSISTENT 标志；动态识别输入、按键、键盘和 Home 处理器，加入保留项。未知功能不禁用。

在保护清单外，按用户选择生成独立的已审核列表，写明每项原因与恢复操作。不得直接执行机型目录中的整份参考列表。包名只接受 Android 包名，不接受 shell 片段；拒绝整份无效清单。

电脑直连使用 `tools/tv_care.py clean`，经 JVM 适配器复用本地 Debloater、Protected、ActionLog。先看计划和 skipped 原因，不改用裸 adb 绕过保护。手机管理端可继续用现有 App 执行链。保护状态不明时停止该项。

每批最多 5 个包，每包单独 `pm disable-user --user 0 PACKAGE`，核对 `pm list packages -d` 的差异。仅命令返回码或文本不是证明。失败即停止该批，不继续补刀；连接丢失先恢复连接和对账。批间截图并确认系统设置、输入和遥控器仍能用。禁用所有包均被拒绝时停止清理，不改用卸载。

APK 通常仍占存储；仅报告观察到的后台负担、广告变化和空间指标，不承诺“释放存储”。用户要求释放缓存时，按本地 `TvFacts.freeCaches()`、`Commands.kt` 的缓存清理流程执行，以实时 `df /data` 衡量，不用滞后的 diskstats 当作释放量；说明缓存不能恢复、应用会重新生成。只有用户清理请求覆盖缓存清理时才执行。不用 `pm clear` 代替缓存清理。用户要求删文件时先单独核对路径与数据用途，不扩展清理权限。

## 4. GenTV 安装与 Home 交接

优先官方兼容 APK 并校验 SHA256SUMS；否则按指南构建 Debug。SDK <26 或厂商拒绝 HOME APK 时停止桌面分支。`INSTALL_FAILED_UPDATE_INCOMPATIBLE` / `INSTALL_FAILED_VERSION_DOWNGRADE` 保留数据并按 `docs/release.md` 处理，不卸载、不强降级。

```sh
adb -s SERIAL install -r APK_PATH
adb -s SERIAL shell am start -W -n com.gentv.tv/.MainActivity
```

检查原始 `Success`、`Status: ok`、实机画面、方向键与返回。截图使用 `adb -s SERIAL exec-out screencap -p`，以二进制保存，不经过文本转换。按照 GenTV 通信协议配置常用应用、主题和信号源。

完整安装将 GenTV 设为默认 Home，记录 intent 和原组件后执行：

```sh
adb -s SERIAL shell cmd package set-home-activity com.gentv.tv/.MainActivity
adb -s SERIAL shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

从系统设置按 Home，并读取 `dumpsys activity activities` 核对前台包；请用户按实体 Home 键确认。系统选择器可作为命令被拒后的正常替代。完整安装按 `docs/tv-care.md` 使用 `home --apk ... --sha256 ...` 自动分阶段接管，必要的可逆交接已在用户安装诉求内，不反复索要授权。普通切换失败后继续识别阻碍；缺少设备证据时完成所需步骤后续办，而不是直接判定不兼容。失败则恢复原 Home 并核验。预览/仅安装明确跳过切换。可选辅助服务单独说明其作用和已知遥控器影响。

## 5. 可选 DNS 过滤

仅 Android 9+。先问路由器是否已有 Pi-hole/AdGuard Home、按域名分流 VPN 或 fake-IP 代理：Private DNS 会绕过这些规则，优先已有路由器方案。用户选择新解析器时记录准确主机名，先备份 `private_dns_mode` 与 `private_dns_specifier` 的值和存在性，再逐项设置。不要猜服务器或静默切换解析器。

检查解析、流媒体播放和网络连通性；失败立即恢复精确原值。DNS 不能保证去除视频应用内广告，不能屏蔽 YouTube 广告。关闭个性化广告也不等于广告消失。

## 6. 验收、恢复与关闭调试

包状态修改后等待至少 15 秒再重启。对比实际禁用列表和本次预期，固件自动恢复项记录为限制，不循环修改。验收：默认 Home 查询、实体 Home/输入/设置/应用快捷键、音量长按、方向键、HDMI 图像声音、直播、常用流媒体、投屏/语音等保留功能、待机恢复和冷启动。按实测填写通过/失败/未测，不把日志推断写成用户确认。

恢复时先核对设备与记录，启用本次误禁用的包、恢复设置和原 Home，核验实体 Home 与保留功能。不要对所有包执行 enable，不自动卸载 GenTV。仅在用户请求时做完整恢复；交接失败执行必要的 Home 回滚，其他更改按实际结果明确报告。

请求用户关闭无线调试、网络 ADB、USB 调试并撤销授权。事先记录已知端口，使用本机 Python 标准库 socket 对该设备已知端口逐项做 TCP 连接检查；无线调试关闭后立刻查其端口，重启后复查已知传统端口和调试开关。Connection refused 是关闭证据，超时/No route to host 仅表示无法确认；`adb disconnect` 不等于关闭服务。有 USB 网卡则重插并确认恢复有线。

交付简短报告：做了什么、实际结果、经脱敏的关键原始输出、本地恢复入口命令、限制和未测项。构建/模拟器证据与真机证据分开。

新增连接、预构建运行包、老系统绑定、隐私审计、实机性能和结构化配置统一按仓库 `docs/tv-care.md` 对应章节执行。优先 `configure` 转换用户自然语言，常用操作不要求用户编辑 JSON。
