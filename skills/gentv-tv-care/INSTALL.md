# 首次使用

此 skill 需要本地终端和用户指定电视的网络访问权限。云端聊天环境通常无法直接操作家庭电视。

1. 将 `gentv-tv-care` 文件夹安装到目标助手的技能目录，按该助手要求重新加载。
2. 已有 GenTV 工作区时，运行 `python3 SKILL_DIR/scripts/setup.py --repo GENTV_REPO`，检查此发布所需文件是否一致。
3. 没有匹配工作区时，获取与 skill 同一发布附带的维护 ZIP。当前候选包仅含 macOS ARM64；不要在其他系统运行它。运行：

   `python3 SKILL_DIR/scripts/setup.py --bundle DOWNLOADED_RUNTIME.zip --destination NEW_DIRECTORY`

   脚本按 skill 中的 release-lock.json 校验压缩包 SHA-256，安全解压到新目录，检查依赖并输出工作区路径；不覆盖已有目录、不联网、不连接电视。
4. 在输出工作区运行 `python3 tools/tv_care.py --help`。维护包自带 Java，无需 JDK/Gradle；仍需要 Python 3。原生 ADB 或独立客户端的依赖、安装 APK 所需 aapt 见 `docs/tv-care.md`。
5. 告诉助手指定电视地址和目标，例如：“使用 GenTV 电视管家，给我这台电视安装并切换 GenTV 桌面，先备份。”调试端口和授权提示由用户提供或确认。

源码分发里的 setup.py 需要打包时生成的 release-lock.json；开发 checkout 按仓库文档运行，不把当前 HEAD 或 latest 当成此 skill 的固定版本。版本不匹配时使用对应运行包或对应源码发布，不跳过检查。

当前 release.json 标记为 local-candidate / published=false，表示尚未公开发布。不要宣称远端已存在下载或商店已收录。发布者完成公开上传后才更新发布状态。
