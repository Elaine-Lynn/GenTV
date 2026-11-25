# 按需加载游戏插件

游戏运行时代码和资源由 `launcher/game` 构建。每种 ABI 生成一个完整的独立 APK 文件：`game-armeabi_v7a.apk` 或 `game-arm64_v8a.apk`。主应用下载对应文件，校验后存入私有目录，通过 `DexClassLoader` 加载代码和原生库；**不调用 Android 安装程序**。这个 APK 只是插件文件的容器，不是需要用户安装的另一个应用。

主 APK 保留下载校验、文件存储、Activity 宿主及 Gecko 子进程入口。游戏页面、手机遥控器、输入扩展、Gecko 代码、`omni.ja` 和对应 ABI 的 `.so` 均放在插件里。运行时加载要求 Android 9 / API 28 或更高版本。Android 8 会显示不支持提示。

## 资源命名

插件自有资源以 `game_` 开头，包括 `game_remote.html`、`game_input/` 和 `game_controls.js`。Gecko 强制使用的 `assets/omni.ja`、`libxul.so` 等文件名，以及 WebExtension 的 `manifest.json` 保留原名。这些文件只在游戏插件中，不进入主 APK。

## 构建与发布

使用同一份源码及正式签名构建主包与插件：

```sh
./launcher/gradlew -p launcher --no-daemon :app:assembleRelease :game:assembleRelease
python3 tools/package_game_runtime.py \
  --sdk "$ANDROID_HOME" \
  --base-apk launcher/app/build/outputs/apk/release/app-release.apk \
  --game-dir launcher/game/build/outputs/apk/release
```

脚本验证两个插件是完整 APK、包名和版本与主包匹配、签名相同、每个包仅含一个 ABI 的原生库，并确认主包没有游戏资源或 `libxul.so`。输出位于 Git 忽略的 `work/game-runtime-release/<versionCode>/<abi>/`，每个 ABI 有一个 APK 和一个含文件大小及 SHA-256 的 `manifest.json`。下载器再次验证清单、大小、SHA-256 和 Android 签名。

将两个 APK 作为本仓库 `game-runtime-v<versionName>` Release 的附件。将各 ABI 的 `manifest.json` 放到 `update-manifest` 分支的 `game-runtime/<versionCode>/<abi>/`。确认附件和清单可公开下载后，再发布引用它们的主包。旧 `game-modules-v*` 安装型 split 包不适用于此加载器。

本地隔离测试可对 Debug 构建运行相同脚本并加 `--allow-debug`。Debug 插件仅用于本地 QA，不可发布给正式包。

## 验收状态

已在 `10.192.10.25:5555`（Android 9、ARMv7）将单个 Debug 插件存入 `com.gentv.tv.moduleqa` 的私有目录，未安装插件。游戏画面和二维码已显示，Gecko 子进程从插件 DEX 启动，手机遥控网页返回 HTTP 200。正式版的在线下载、签名校验、手机指令及 ARM64 真机仍需验收；当前尚未发布插件附件或清单。隔离 QA 包不替换设备上的正式桌面。
