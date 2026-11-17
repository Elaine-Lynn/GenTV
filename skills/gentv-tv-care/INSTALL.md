# 首次使用

Skill 需要本地终端及用户指定电视的网络访问权限。云端聊天通常无法直接操作家庭电视。

发布包中 release-lock.json 绑定源码依赖与平台运行包，不得用 latest 代替。已有工作区可用 `python3 SKILL_DIR/scripts/setup.py --repo GENTV_REPO` 检查；开发副本没有锁时按仓库指南运行。

没有匹配工作区时，使用系统原生准备脚本，不需要系统 Python：

- macOS / Linux：`sh SKILL_DIR/scripts/prepare.sh NEW_DIRECTORY`
- Windows：`powershell -File SKILL_DIR/scripts/prepare.ps1 -Destination NEW_DIRECTORY`

脚本识别平台，下载固定 tag 附件并核对内置 SHA-256，解压到新目录，不覆盖已有环境。离线时 macOS/Linux 第二参数传本地 ZIP，Windows 使用 `-Bundle LOCAL_RUNTIME.zip`。不修改脚本执行策略；缺少系统 curl、unzip、哈希工具或 PowerShell 时明确报告。

若已有 Python，可用 `python3 SKILL_DIR/scripts/setup.py --download --destination NEW_DIRECTORY`；本地 ZIP 使用 `--bundle ZIP --destination NEW_DIRECTORY`。Python 下载限制 HTTPS 和文件大小。

准备后运行输出目录的 `bin/gentv-care --help`（Windows 为 bin/gentv-care.exe）。冻结运行包自带 Python、Java 和传统 TCP ADB 客户端，不需手动安装 Python、JDK、Gradle、aapt。USB 或加密无线调试仍需原生 adb；不切换调试模式迁就备用客户端。源码入口仍需 Python 和构建环境。

向助手提供准确电视地址、已知端口和需求。调试由用户开启，电视提示由用户接受。准备脚本不连接电视，不安装 APK，不改 Home。

单平台本地候选包只含本机平台，CI 合并包才含矩阵通过的平台。固定 tag 附件未真实发布前，下载将失败，请使用本地 ZIP，不能宣称远端已发布或商店已收录。
