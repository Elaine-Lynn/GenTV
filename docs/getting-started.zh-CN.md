# 构建和安装 GenTV

[English](getting-started.md) · 简体中文

本指南是个人用户和 AI 助手使用本仓库的入口。中英文内容不一致时，以[英文版](getting-started.md)为准。

GenTV 由 Android 应用组成，不是电脑上的 Python / Node 程序，也不是 AI 助手插件，没有单独的 GenTV 命令行工具。命令行安装用的是 Android SDK Platform Tools（`adb`）；手机 App 在获得 ADB 授权后也能把 GenTV 装到电视上。

## 选择安装方式

- 用正式版：从 [GitHub Releases](https://github.com/Elaine-Lynn/GenTV/releases/latest) 或 [GenTV 官网](https://www.gentv.me/)下载。每个 GitHub Release 的 `SHA256SUMS.txt` 列出了 SHA-256 校验值；仓库本身不存放 APK。
- 只构建电视版：按下面的电视版步骤操作，手机版可以不装。
- 构建手机版：需要安装下面列出的两个 Android SDK 平台和两个构建用 JDK。手机版构建时会从源码生成并内嵌电视版 APK。
- 参与开发，或让 AI 助手在本地构建：步骤相同。Debug 构建不需要服务器账号或签名密钥。

两个应用都需要 Android 8 / API 26 或以上。用手机管理电视或用命令行安装，电视需要支持安装 Android APK，并允许已授权的 ADB 连接。三星 Tizen 和 LG webOS 不能运行这些 Android 应用。即使是 Android 电视，厂商限制也可能阻止安装、配对、切换信号源或更换默认桌面。

<a id="verify-a-download"></a>

## 校验下载的安装包

电视版和手机版的正式版都用同一张证书签名。证书的 SHA-256 指纹是：

```text
8D:5B:53:78:F9:05:68:80:F3:03:6D:44:BC:2F:4A:48:32:21:41:FB:B8:D8:BC:91:95:1D:61:A4:65:2D:99:7F
```

在下载目录里运行，第一条核对文件内容，第二条核对签名证书（`apksigner` 在 Android SDK 的 build-tools 目录下）：

```sh
shasum -a 256 -c SHA256SUMS.txt --ignore-missing
apksigner verify --print-certs gentv-tv-3.7.0.apk
```

`apksigner` 输出的 `certificate SHA-256 digest` 去掉冒号、不分大小写后应与上面的指纹一致。不一致就不要安装。

## 本地环境

安装 Git、Python 3.10 或以上、Android SDK Command-line Tools，以及下表列出的 JDK。手机版的电视资产构建和仓库检查需要 Python。不需要 Node.js / npm。

| 任务 | 需要 |
|---|---|
| 电视版构建和测试 | JDK 17、SDK platform 34、build-tools 34.0.0 |
| 手机版构建和 app / core 测试 | JDK 21、SDK platform 37.0、build-tools 36.0.0，外加电视版的全部要求 |
| 手机版 ADB 单元测试 | JDK 25；Gradle 配置的工具链解析器可以联网自动获取 |
| 命令行安装 | SDK Platform Tools（`adb`） |
| 可选的截图冒烟测试工具 | `python3 -m pip install -r tools/requirements-dev.txt` |

用 Android Studio 的 SDK Manager 安装 SDK 组件，或者用 Android SDK Command-line Tools 里的 `sdkmanager`：

```sh
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "platforms;android-37.0" "build-tools;36.0.0"
sdkmanager --licenses
```

请逐条阅读并接受 SDK 许可。把下面的变量设成你自己的安装路径（POSIX shell 示例）：

```sh
export ANDROID_HOME="/absolute/path/to/android-sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
export GENTV_HOME_JAVA_HOME="/absolute/path/to/jdk-17"
export JAVA_HOME="/absolute/path/to/jdk-21"
java -version
"$GENTV_HOME_JAVA_HOME/bin/java" -version
python3 --version
adb version
```

只构建电视版时，把 `JAVA_HOME` 设成 JDK 17 的路径。仓库已包含 Gradle wrapper，不要另装全局 Gradle。Windows 上用 PowerShell 的环境变量和 `gradlew.bat`。两种 wrapper 都已包含，但目前只在 macOS 上验证过构建，Windows 尚未验证。

首次构建需要能访问 Gradle 分发服务、Google Maven、Maven Central、Gradle 插件门户、JitPack，必要时还有 Java 工具链解析器。`--offline` 只有在所需依赖全部缓存后才能用。构建前先处理好 SDK 许可和代理 / 防火墙设置。

## 从源码构建

在终端里运行：

```sh
git clone https://github.com/Elaine-Lynn/GenTV.git
cd GenTV
python3 tools/check_branding.py
python3 tools/check_repository.py
python3 -m unittest discover -s server/wallpaper -p 'test_*.py'
```

电视版，在仓库根目录运行：

```sh
JAVA_HOME="$GENTV_HOME_JAVA_HOME" ./launcher/gradlew -p launcher --no-daemon testDebugUnitTest lintDebug assembleDebug
```

输出：`launcher/app/build/outputs/apk/debug/app-debug.apk`，包名 `com.gentv.tv`。

手机版，`JAVA_HOME` 设为 JDK 21，`GENTV_HOME_JAVA_HOME` 设为 JDK 17：

```sh
./phone/gradlew -p phone --no-daemon :core:test :adb:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

输出：`phone/app/build/outputs/apk/debug/app-debug.apk`，包名 `com.gentv.mobile.debug`。没有私有签名配置时，内嵌的电视版 APK 使用调试签名。不同开发机的 Android Debug 证书可能不同。Debug 电视版不能覆盖签名不同的正式版。

源码构建默认关闭在线更新、在线壁纸、反馈和统计，除非明确配置了 API 地址。电视桌面本身的使用和手机到电视的 ADB 操作都不需要 GenTV 服务。自建服务需要可信的 HTTPS 地址、发布清单、APK 托管，以及你想提供的壁纸素材，见[服务端说明](../server/wallpaper/README.md)。正式签名是单独的[发布流程](release.md)。

<a id="install-the-tv-app-from-a-usb-drive"></a>

## 用 U 盘安装电视版

这种方式不需要手机或 ADB，但电视要能读取 U 盘，并能通过文件管理器安装 Android APK。

1. 从官方下载入口获取**电视版 APK**（`com.gentv.tv`），或按上面的电视版步骤自己构建。不要用手机版 APK。如果下载的是电视版分发 ZIP，先解压，再分发时请保留其中的许可文件。
2. 把 APK 拷到电视支持的文件系统格式的 U 盘里，插到电视上。
3. 在电视的文件管理器里打开 U 盘，选择 APK。如有提示，允许该文件管理器安装未知应用，再确认 Android 的安装对话框。
4. 从电视的应用列表打开 GenTV，确认遥控器操作正常。设为默认桌面是系统设置里的另一步操作。

有些厂商会禁止从 U 盘安装 APK，或者没有合适的文件管理器，这时如果条件允许，请改用手机或 ADB 安装。签名冲突或降级错误不代表可以卸载现有应用；请保留数据，按[发布与签名](release.md)里的迁移步骤处理。只用 U 盘安装电视版，不包含手机端的清理和 DNS 配置功能，这些需要手机获得授权连接后才能用。U 盘安装已写入文档，但在这次审核中还没有在真实电视上实际操作过。

<a id="install-with-an-ai-assistant"></a>

## 让 AI 助手安装

请使用能访问你本地终端和 Android SDK 工具的 AI 助手。只能聊天的助手可以讲解步骤，但不能替你安装。不需要任何 GenTV 专用的 AI 插件。

可以把下面这段话发给它：

> 请帮我从 https://github.com/Elaine-Lynn/GenTV 安装 GenTV。先读 docs/getting-started.md。检查我的本地环境，必要时让我确认要装的是哪台电视。使用兼容的电视版 APK，或从源码构建 Debug APK。通过已授权的 ADB 安装并打开，验证结果，保留当前的默认桌面和已有应用。设备上的配对和安装提示由我来确认。

助手应按照[命令行安装步骤](#connect-and-install-with-the-cli)和[给 AI 助手的说明](#instructions-for-an-ai-assistant)操作。要装哪台设备必须由用户指定，设备上的提示必须由用户确认；不能编造签名凭据、悄悄卸载不兼容的应用，或停用原厂桌面。这是有人参与的辅助安装流程，不是无人值守的一键安装。

<a id="connect-and-install-with-the-cli"></a>

## 用命令行连接和安装

在目标设备上打开调试，并在该设备上确认授权提示。USB 连接的手机可以直接使用。对于开启了传统网络 ADB 的电视，用 `adb connect TV_IP:ADB_PORT`。对于 Android 无线调试，使用设备上显示的配对地址和连接地址：

```sh
adb pair TV_IP:PAIRING_PORT
adb connect TV_IP:CONNECTION_PORT
adb devices -l
```

按提示输入配对码。配对端口和连接端口通常不同，也不一定是 5555。不要靠猜端口去开网络调试，也不要操作无关的设备。每台设备都必须显示为 `device` 状态；如果是 `unauthorized` 或 `offline`，先解决再继续。

始终用 `adb devices -l` 里的序列号指定目标设备，有多台设备时尤其如此。下面的命令假设你已完成上一节的构建，并在仓库根目录运行：

```sh
# 电视：把 TV_SERIAL 换成已授权电视的准确序列号或地址。
adb -s TV_SERIAL shell getprop ro.build.version.sdk
adb -s TV_SERIAL shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s TV_SERIAL install -r launcher/app/build/outputs/apk/debug/app-debug.apk
adb -s TV_SERIAL shell pm path com.gentv.tv
adb -s TV_SERIAL shell am start -W -n com.gentv.tv/.MainActivity
```

确认安装输出 `Success`、启动输出 `Status: ok`，再在电视上看到 GenTV 的界面。重新运行查询默认桌面的命令，确认原来的默认桌面没有变。`pm path` 能查到 APK，并不代表界面和遥控器都能正常使用。

```sh
# 手机：把 PHONE_SERIAL 换成已授权手机的准确序列号。
adb -s PHONE_SERIAL install -r phone/app/build/outputs/apk/debug/app-debug.apk
adb -s PHONE_SERIAL shell am start -W -n com.gentv.mobile.debug/com.gentv.mobile.MainActivity
```

正式版手机 APK 的包名是 `com.gentv.mobile`，不是 `.debug`。打开手机 App 后，让手机和电视处在同一个可互通的局域网里，连接或配对并在电视上确认，然后选择安装 GenTV。目前的安装器会安装并打开 GenTV、检查桌面通信是否正常；它会保留默认桌面，也不会自动授予可选权限。

安装和选择默认桌面是两件事。只有电视主人想把 GenTV 设为默认时，才去系统的主屏幕设置里选择。有些固件没有这个选项。无障碍辅助功能和各机型的 HDMI / 信号源功能需要单独设置和验收；不要为了绕过被锁定的设置去停用原厂桌面。

## 错误与恢复

| 现象 | 下一步 |
|---|---|
| 找不到 SDK 路径或平台 | 检查 `ANDROID_HOME`、已安装的组件名和 SDK 许可 |
| 电视版构建拒绝当前 Java 版本 | 把 `GENTV_HOME_JAVA_HOME` 设为 JDK 17，电视版的 `JAVA_HOME` 也设成同一路径 |
| ADB 测试找不到 Java 25 | 允许工具链自动获取，或安装 JDK 25 并配置 Gradle 工具链发现 |
| 依赖解析失败 | 检查仓库连通性、代理和首次构建的网络访问；去掉 `--offline` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 证书冲突：停下来；保留用户数据，按 release.md 里的迁移步骤处理 |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | 使用兼容的新版本；不要自动卸载或强制降级 |
| 应用装好了，但默认桌面没变 | 正常安装就是这样；选择默认桌面是用户单独的操作 |
| 在线功能不可用 | 没有配置 API 时属于正常；已运行的服务器还需要有效的清单和素材 |
| ADB 能连上，但某个功能失败 | 检查机型和固件的支持情况，以及该功能本身；能连上不等于兼容认证 |

卸载 GenTV 之前，先在系统设置里选回原来的桌面，确认它能打开。卸载前导出桌面配置、保留恢复记录：Android 卸载应用会删除应用数据。维护结束后撤销 ADB 授权并关闭调试。

## 社区机型参考

`phone/framework/data/devices/` 下的机型清单是继承来的社区记录。部分注释提到了上游的设备文档或工具，这些没有随本仓库提供；它们是历史参考，不是可执行的 GenTV 操作步骤，也不代表每个机型都受支持。不要在没有核对当前 GenTV 保护规则和实际设备、固件的情况下运行其中提到的清理命令或停用任何应用。GenTV 本身的真机结果见[已验证机型](devices/verified.md)。

<a id="instructions-for-an-ai-assistant"></a>

## 给 AI 助手的说明

先阅读本指南、README.md、CONTRIBUTING.md 和相关组件的文档。检查本地工具版本和 Git 状态，先构建并运行检查，再尝试安装。占位符就是占位符：不要猜设备序列号、私钥、服务器地址或配对码。不要输出签名材料，也不要提交密钥、备份、截图或设备记录。

如果只是构建，验证完构建产物就停下。如果要安装到设备，先确定用户指定的已授权设备，记录当前默认桌面，安装选定的兼容 APK，再验证包、启动结果和设备界面。设备上的提示需要用户操作。签名或版本被拒绝是阻断性结果，不代表可以卸载。不要仅仅为了安装就更改默认桌面、停用应用或执行清理。构建和测试结果要和模拟器或真机验收结果分开报告。
