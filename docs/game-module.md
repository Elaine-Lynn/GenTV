# 按需下载卡丁车游戏

从电视版 3.7.14 / code36 开始，基础 APK 不包含 GeckoView 的原生库和游戏代码，但保留内核启动必需的 `omni.ja` 资源。游戏仍是 `com.gentv.tv` 的一部分；首次进入游戏时，用户下载与电视 CPU 架构对应的两个已签名 split APK，由 Android 安装确认。没有独立的游戏应用图标或第二个包名。游戏仅在 Android 6 / API 23 及以上可启动，旧系统提示不支持。

## 构建与打包

使用与正式基础包相同的源码和正式签名，构建 `:app:assembleRelease :app:bundleRelease :app:makeApkFromBundleForRelease`。基础包在 `launcher/app/build/outputs/apk/release/app-release.apk`，APK set 在 `launcher/app/build/intermediates/apks_from_bundle/release/makeApkFromBundleForRelease/bundle.apks`。

设置 Android SDK 路径后运行：

```sh
python3 tools/package_game_modules.py \
  --sdk "$ANDROID_HOME" \
  --base-apk launcher/app/build/outputs/apk/release/app-release.apk \
  --apks launcher/app/build/intermediates/apks_from_bundle/release/makeApkFromBundleForRelease/bundle.apks
```

脚本核对基础 APK 和各分包的包名、版本与正式证书，确认基础 APK 没有 `libxul.so`，并且其 `omni.ja` 与游戏分包一致。默认输出位于未跟踪的 `work/game-modules/36/armeabi-v7a/` 和 `work/game-modules/36/arm64-v8a/`；每个目录包含 `game-master.apk`、对应的 ABI 分包和 `manifest.json`，其中有准确的大小与 SHA-256。重新构建同一版本时，使用新的 `--output work/<构建批次>` 目录；脚本会拒绝覆盖内容不同的旧文件。不要改名或重新压缩，下载后 Android 还会执行同包名、同版本和同签名校验。

## GitHub 发布顺序

1. 在本仓库创建 GitHub Release，标签 `game-modules-v3.7.14`；上传一份 `game-master.apk`、一份 `game-armeabi_v7a.apk`、一份 `game-arm64_v8a.apk`，来自同一次脚本输出。两个 ABI 目录中的 `game-master.apk` 内容相同。
2. 将两个 `manifest.json` 分别放到本仓库 `update-manifest` 分支的 `game-modules/36/armeabi-v7a/` 和 `game-modules/36/arm64-v8a/`。核对 raw GitHub 清单公开可读、三个 Release 附件可下载，且 SHA-256 与清单一致。
3. 再发布 3.7.14 的基础 APK，最后切换正式更新清单。不要把旧的 3.7.13 清单指向新的 code36 基础包，也不要用已有 3.7.13 APK 搭配 code36 游戏分包。

基础包自更新仍沿用现有整包安装。每次基础包升级后，游戏分包需要与新 `versionCode` 匹配，按上述顺序先发布新版本游戏分包。旧版本分包保留给尚未升级的客户端。

首次安装分包需要 Android 的“安装未知应用”权限和系统确认；应用不能绕过。网络或安装失败时保留已校验的本地缓存，用户可重试。游戏分包下载不依赖 GenTV 自建 API 域名。

## 验收

在 Android 6 及以上的 ARMv7 和 ARM64 设备上分别检查：基础包可以安装并启动首页；首次进入游戏下载正确 ABI 的两个分包；系统安装确认后游戏启动；再次进入无需下载；升级基础包后重新获取同版本游戏分包。Android 4.2–5.1 应仅运行基础桌面，不出现游戏安装操作。隔离 QA 已在 `192.168.31.85`（Android 9、ARMv7）完成基础包加两个分包的安装，游戏画面正常显示；联网下载和应用内安装确认仍需 GitHub 附件发布后验收。
