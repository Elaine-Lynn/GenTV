# 按需加载游戏插件

游戏运行时代码和资源由 `launcher/game` 构建。每种 ABI 生成一个完整的独立 APK 文件：`game-armeabi_v7a.apk` 或 `game-arm64_v8a.apk`。主应用下载对应文件，校验后存入私有目录，通过 `DexClassLoader` 加载代码和原生库；**不调用 Android 安装程序**。这个 APK 只是插件文件的容器，不是需要用户安装的另一个应用。

主 APK 保留下载校验、文件存储、Activity 宿主及 Gecko 子进程入口。游戏页面、手机遥控器、输入扩展、Gecko 代码、`omni.ja` 和对应 ABI 的 `.so` 均放在插件里。运行时加载要求 Android 9 / API 28 或更高版本。Android 8 会显示不支持提示。

## 资源命名

插件自有资源以 `game_` 开头，包括 `game_remote.html`、`game_input/` 和 `game_controls.js`。Gecko 强制使用的 `assets/omni.ja`、`libxul.so` 等文件名，以及 WebExtension 的 `manifest.json` 保留原名。这些文件只在游戏插件中，不进入主 APK。

## 构建与发布

后端文件清单、接口地址和发布顺序见 [插件更新部署](game-plugin-deployment.md)。

使用同一份源码及正式签名构建主包与插件：

```sh
./launcher/gradlew -p launcher --no-daemon :app:assembleRelease :game:assembleRelease
python3 tools/package_game_runtime.py \
  --sdk "$ANDROID_HOME" \
  --base-apk launcher/app/build/outputs/apk/release/app-release.apk \
  --game-dir launcher/game/build/outputs/apk/release
```

插件独立使用 `versionCode`（默认为 1）和 `versionName`（默认为 1.0.0），与主包版本无关。通过 `-PgameVersionCode=2 -PgameVersionName=1.0.1` 发布下一版插件。`hostApiVersion=1` 表示主包提供的加载接口；修改接口时需升级该协议，普通游戏更新不需要升级主包。

脚本验证完整 APK、正式签名、每个包仅含一个 ABI，并确认主包没有游戏资源或 `libxul.so`。输出位于 Git 忽略的 `work/game-runtime-release/<插件versionCode>/<abi>/`，包含组件 APK 和 `manifest.json`。使用 `--min-host 37 --max-host 2147483647` 指定兼容的主包范围；发布者必须按实际接口兼容情况填写范围。

## 独立更新接口

主接口为 `GET <构建配置中的API_BASE_URL>plugins/game/update?abi=armeabi-v7a`，即服务端路径 `/tv/plugins/game/update`。支持 `armeabi-v7a`、`arm64-v8a`，不支持的 ABI 返回 400，尚无清单时返回 503。服务端从以下文件读取清单：

- `$GENTV_STATE_DIR/plugins/game/update-armeabi-v7a.json`
- `$GENTV_STATE_DIR/plugins/game/update-arm64-v8a.json`

主接口不可用时读取 GitHub 备用清单：`update-manifest` 分支的 `game-runtime/latest/<abi>/manifest.json`。两个接口返回同样的 JSON。插件 APK 作为 `game-runtime-v<插件versionName>` Release 附件，或放在同 API 的 `/tv/releases/game-<abi下划线形式>.apk`。

每次进入游戏检查更新。插件版本大于本地版本且兼容主包时，下载对应架构文件，验证大小、SHA-256、APK版本和签名，再写入独立版本目录并原子切换指针。下载、校验或切换失败时保留旧组件；不接受降级。同一插件版本文件不可修改，修复时必须提高插件版本号。主包升级后可以继续使用已下载的兼容插件，无需跟随主包重新下载。现有按主包版本保存的试验组件不迁移，首次使用新协议需重新下载一次。

发布顺序：先上传插件附件，再把脚本输出的清单复制到服务端及 GitHub 的 `latest/<abi>/manifest.json`。只更新这些清单和附件即可发布插件更新。服务端部署时需同步更新 `app.py` 和 Nginx 路由；本地代码完成不代表线上接口已经部署。

本地隔离测试可对 Debug 构建运行相同脚本并加 `--allow-debug`。Debug 插件仅用于本地 QA，不可发布给正式包。

## 验收状态

已在 `10.192.10.25:5555`（Android 9、ARMv7）将单个 Debug 插件存入 `com.gentv.tv.moduleqa` 的私有目录，未安装插件。游戏画面和二维码已显示，Gecko 子进程从插件 DEX 启动，手机遥控网页返回 HTTP 200。正式版的在线下载、签名校验、手机指令及 ARM64 真机仍需验收；当前尚未发布插件附件或清单。隔离 QA 包不替换设备上的正式桌面。
