<p align="center">
  <a href="https://www.gentv.me/"><img src="docs/media/readme/overview/01-project-cover.png" alt="GenTV：免费开源、没有广告的 Android TV / Google TV 电视桌面，配套手机 App，可以安装桌面、清理预装应用、当遥控器用。不用电脑、不用 Root、不用账号。" width="800"></a>
</p>

<h1 align="center">GenTV — 无广告的 Android TV 电视桌面</h1>

<p align="center"><b>给电视换一块干净的桌面，用手机就能装、能清理、能遥控。不用电脑，不用 Root，不用账号。</b></p>

<p align="center">
  <a href="README.md">English</a> · 简体中文
</p>

<p align="center">
  <a href="https://www.gentv.me/"><img alt="下载" src="https://img.shields.io/badge/%E4%B8%8B%E8%BD%BD-gentv.me-34a853?style=for-the-badge&logo=android&logoColor=white"></a>
  <a href="#install"><img alt="安装指南" src="https://img.shields.io/badge/%E5%AE%89%E8%A3%85-%E6%8C%87%E5%8D%97-1f6feb?style=for-the-badge"></a>
  <a href="#faq"><img alt="常见问题" src="https://img.shields.io/badge/%E5%B8%B8%E8%A7%81-%E9%97%AE%E9%A2%98-d97757?style=for-the-badge"></a>
</p>

<p align="center">
  <a href="LICENSE"><img alt="MIT 许可证" src="https://img.shields.io/badge/%E8%AE%B8%E5%8F%AF%E8%AF%81-MIT-555555?style=flat-square"></a>
  <img alt="Android 8.0 及以上" src="https://img.shields.io/badge/Android%20TV-8.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white">
  <img alt="电视安装包不到 600 KB" src="https://img.shields.io/badge/%E7%94%B5%E8%A7%86%E5%AE%89%E8%A3%85%E5%8C%85-%3C600%20KB-2997ff?style=flat-square">
  <img alt="不用电脑" src="https://img.shields.io/badge/%E7%94%B5%E8%84%91-%E4%B8%8D%E9%9C%80%E8%A6%81-555555?style=flat-square">
  <img alt="无需 Root" src="https://img.shields.io/badge/Root-%E4%B8%8D%E9%9C%80%E8%A6%81-555555?style=flat-square">
</p>

**GenTV 是一款免费开源、没有广告的 Android TV / Google TV 电视桌面（Launcher），配套一个 Android 手机 App。** 在手机上就能把 GenTV 装到电视上，清理预装应用、释放存储空间、恢复改动，还能把手机当遥控器和键盘用。不需要电脑，不需要 Root，也不用注册 GenTV 账号。

| 传统电视桌面 | GenTV |
|:---:|:---:|
| <img src="docs/media/readme/comparison/01-traditional-tv.png" alt="传统电视桌面：赞助横幅、推荐片单，以及会员、新片、限免角标"> | <img src="docs/media/readme/comparison/02-gentv-home.png" alt="GenTV 桌面：时钟、森林晨雾壁纸和一排应用磁贴"> |

适用于小米、Redmi、TCL、雷鸟、海信、Vidda、创维、酷开、索尼、飞利浦等品牌的 Android 电视，以及 Google TV、电视盒子和投影仪（具体限制见[机型参考](#devices)）。

官方渠道只有 [gentv.me](https://www.gentv.me/) 和本仓库。

## 从这里开始

| 你手上有 | 去这里 |
|---|---|
| **一台 Android 电视 + 一部 Android 手机** | [用手机安装](#install-phone)，不用电脑 |
| **一台电脑，或能运行命令的 AI 助手** | [让 AI 助手安装](#install-ai) |
| **只有 U 盘** | [U 盘安装](#install-usb) |
| **三星（Tizen）、LG（webOS）、海信 VIDAA 电视** | GenTV 装不了，[原因看这里](#non-android) |
| **想先看看效果** | [界面预览](#screenshots) |

## 为什么用 GenTV

- **全程手机操作。** 安装桌面、清理电视、遥控、打字、传文件、整理桌面，都在同一网络下的 Android 手机上完成，不用电脑，也不用数据线。
- **没有广告的桌面。** 不塞推广，不放推荐流，只有你的应用、壁纸和信号源。大磁贴方便用遥控器操作。正式电视安装包不到 600 KB，老电视也带得动。
- **把电视里的垃圾清一清。** 查看存储空间，清理缓存，检查并停用或移除用不上的预装应用。系统组件受保护，每一步都有记录。
- **改了也能恢复。** 在手机上就能重新启用停用的应用，恢复符合条件的预装应用。GenTV 不会自作主张改你的默认桌面。

<a id="install"></a>

## 安装

手机和电视都需要 **Android 8.0（API 26）或以上**。用手机管理电视，需要先在电视上打开 ADB 调试并授权。

<a id="install-phone"></a>

### 用手机安装（推荐）

只要一部 Android 手机，和电视连在同一个 Wi-Fi 或局域网里。

1. 在手机上从 [gentv.me](https://www.gentv.me/) 安装 **GenTV Remote**。
2. 在电视上[打开开发者选项和网络调试或无线调试](#adb)。
3. 打开 GenTV Remote，选择电视，在电视上确认配对或调试授权。
4. 选择 **安装 GenTV 桌面**，电视上会打开 GenTV。
5. 可选：想把 GenTV 设为默认桌面，到电视系统的主屏幕设置里选择。GenTV 不会替你改。

装好后，清理、遥控、打字和整理桌面都继续在手机上操作。

<a id="install-ai"></a>

### 让 AI 助手安装

要用能在电脑上运行命令的 AI 助手，例如豆包电脑版、WorkBuddy、Trae、千问电脑版、[Claude Code](https://claude.com/product/claude-code)、[Codex](https://github.com/openai/codex)、[Gemini CLI](https://github.com/google-gemini/gemini-cli)。手机上或网页里的聊天助手只能讲步骤，不能替你安装。把下面这段话发给它：

```text
请帮我把开源电视桌面 GenTV（https://github.com/Elaine-Lynn/GenTV）装到我的 Android 电视上。
电视 IP：<电视IP>
请先读完 docs/getting-started.md，包括 “Instructions for an AI assistant” 一节。
先检查我电脑上的工具，再选用兼容的电视版 APK，或从源码构建 Debug APK。
通过已授权的 ADB 安装并打开，然后验证结果。
保留我当前的默认桌面和已有应用。需要我在电视上确认时提醒我。
```

电视上的配对和安装提示需要你自己确认。完整步骤见[安装指南](docs/getting-started.md#install-with-an-ai-assistant)（英文），给 AI 助手的规则见 [AGENTS.md](AGENTS.md)。

<a id="install-usb"></a>

### U 盘安装

把**电视版 APK**（`com.gentv.tv`，不是手机版）拷进 U 盘，在电视的文件管理器里打开，按提示允许安装。这种方式不含手机清理和 DNS 过滤功能。有些电视不允许从 U 盘安装。详见 [U 盘步骤](docs/getting-started.md#install-the-tv-app-from-a-usb-drive)（英文）。

<a id="adb"></a>

### 在电视上打开 ADB 调试

| 电视 | 在哪里找 |
|---|---|
| **大多数 Android TV / Google TV**（索尼、飞利浦、TCL Google TV、电视盒子） | 设置 → 系统（或"设备偏好设置"）→ 关于 → 连按 **Android TV OS 版本号**（或"内部版本号"）7 次，再到开发者选项里打开 **USB 调试**，有"网络调试"的话也一起打开 |
| **Android 11 及以上、只有"无线调试"的电视** | 开发者选项 → 无线调试 → **使用配对码配对设备**，把配对码输入 GenTV Remote |
| **小米、Redmi、TCL、海信、创维 / 酷开等国内电视** | 各品牌、各固件的菜单名都不一样，一般在"关于"或"账号与安全"里找"ADB 调试"或"开发者选项"。有些固件隐藏或去掉了这个开关 |

电视弹出"是否允许调试"时，选**始终允许**。用完记得把调试关掉。

小米、TCL、雷鸟、索尼、海信、Vidda、创维、酷开、飞利浦、Google、Nvidia、极米、Fire TV 的分品牌说明（每条都附了出处机型）：[各品牌电视怎么打开 ADB 调试](docs/devices/adb-by-brand.zh-CN.md)。

<a id="screenshots"></a>

## 界面预览

<p align="center">
  <img src="docs/media/readme/overview/05-tv-showcase.png" alt="GenTV 电视桌面：风景壁纸上的应用磁贴" width="800">
</p>

<p align="center">
  <img src="docs/media/readme/screenshots/tv/01-alpine-lake.png" width="49%" alt="GenTV 桌面，雪山湖泊壁纸">
  <img src="docs/media/readme/screenshots/tv/05-milky-way.png" width="49%" alt="GenTV 桌面，银河夜空壁纸">
  <img src="docs/media/readme/screenshots/tv/06-autumn.png" width="49%" alt="GenTV 桌面，秋日湖畔壁纸">
  <img src="docs/media/readme/screenshots/tv/08-aurora.png" width="49%" alt="GenTV 桌面，极光壁纸">
</p>

**手机端：清理入口、遥控器、桌面管理、应用管理**

<p align="center">
  <img src="docs/media/readme/screenshots/phone/01-home.png" width="24%" alt="GenTV Remote 首页：存储清理、应用管理和广告过滤入口">
  <img src="docs/media/readme/screenshots/phone/02-remote.png" width="24%" alt="GenTV Remote 手机遥控器">
  <img src="docs/media/readme/screenshots/phone/03-desktop-management.png" width="24%" alt="在手机上整理电视桌面">
  <img src="docs/media/readme/screenshots/phone/04-app-management.png" width="24%" alt="在手机上检查并停用电视应用">
</p>

电视画面是 Android TV 模拟器的实际截图；手机画面由界面测试渲染，使用演示数据。另见 [8 套壁纸总览](docs/media/readme/overview/02-wallpaper-gallery.png)和[全部素材与来源说明](docs/media/readme/README.md)。

## 功能

| 功能 | 能带来什么 |
|---|---|
| 无广告桌面 | Dock 加应用网格，大磁贴方便遥控器操作；信号源快捷入口（支持创维 / 酷开的信号源）；应用库列出所有已安装应用 |
| 文件夹与个性化 | 应用排序、改名、隐藏、放进文件夹，自定义磁贴图片，保存、导入、导出桌面配置；手机和遥控器都能操作 |
| 壁纸 | 本地图片或可选的在线壁纸；下载过的图片可以离线显示 |
| 时钟屏保 | 时间和日期，加一层缓慢变化的光晕；不联网、不加载图片，1 GB 内存的电视也能用 |
| 农历与节日主题 | 可选显示农历日期（中国 / 越南 / 韩国历法）和伊朗历日期，还有节日主题；默认关闭 |
| 手机遥控 | 方向键、触控板、键盘输入、文件传输 |
| 存储与应用清理 | 查看存储空间、清理缓存，检查、停用或移除预装应用；有系统组件保护、备份和操作记录 |
| 恢复 | 重新启用停用的应用，恢复符合条件的、从当前用户移除的预装包 |
| 广告过滤 | 在支持的 Android 9+ 电视上可选配置私人 DNS，减少广告和追踪请求 |
| 游戏手柄 | 可选手柄直通，让游戏正常识别手柄按键 |
| 多语言 | 电视界面翻译超过 25 种语言 |
| 更新与隐私 | 更新包签名和 SHA-256 校验；不用账号；匿名统计默认关闭 |

### 能做什么，不能做什么

| 能做 | 做不到 |
|---|---|
| 把满屏广告的桌面换成无广告桌面（需在系统里选它为默认桌面） | 去掉开机广告，那是电视固件在桌面启动前播放的 |
| 停用或移除你选的预装应用，并留下记录方便还原 | 保证清单里每个应用在你这台机型上都能安全停用；动手前请先核对 |
| 在支持的电视上用私人 DNS 减少广告和追踪请求 | 屏蔽所有应用内广告和视频广告；效果取决于应用和 DNS 服务商 |
| 在 Android 8+ 的电视、盒子和投影仪上运行 | 在三星 Tizen、LG webOS、海信 VIDAA 上运行 |

<a id="devices"></a>

## 机型参考

手机 App 内置 **252 款电视、盒子和投影仪**的社区清理参考，数量最多的是 TCL（57）、索尼（32）、小米（29）、飞利浦（12）、海信（10）、创维（8）、亚马逊 Fire TV（7），也覆盖 Redmi、雷鸟、Vidda、极米、松下、一加、Nvidia、夏普等品牌。这些清单来自社区，用来帮你判断哪些应用可以清理，不代表每台机型都已通过兼容认证。能不能安装、更换默认桌面、切换信号源，仍可能受厂商限制。到[机型索引](docs/devices/README.md)里查你的型号。

<a id="faq"></a>

## 常见问题

### 不用电脑能装吗？

能。在 Android 手机上装 GenTV Remote，通过局域网和电视配对，然后在手机上安装 GenTV。只有想用命令行或从源码构建时才需要电脑。

### iPhone 能用吗？

暂时不能，手机端目前只有 Android 版。用 iPhone 的话，可以选 [AI 助手安装](#install-ai)或 [U 盘安装](#install-usb)。

### 装好后会自动变成默认桌面吗？

不会。安装后会打开 GenTV，但不会改你现在的默认桌面。想换的话，在电视系统的主屏幕设置里选择 GenTV。有些固件没有这个选项。

### 会不会把电视弄坏？改错了怎么恢复？

GenTV 不 Root、不刷机。清理只针对你选中的应用，系统组件受保护，每一步都有记录。在手机上可以重新启用停用的应用，恢复符合条件的预装应用。彻底卸载的用户应用需要重新安装。建议先备份，每次少改几个。

### 能去掉开机广告吗？

不能。开机广告是电视固件在桌面启动之前播放的。GenTV 去掉的是桌面上的广告；可选的私人 DNS 过滤还能减少部分应用里的广告请求。

### 原来的应用、会员和语音助手还在吗？

在。安装 GenTV 不会删除或停用任何东西，你的应用、账号和原来的桌面都还在电视上。只有你在清理时选中的应用才会被改动。

### 我的电视能用吗？

GenTV 面向 Android TV / Google TV 8.0 及以上的电视、盒子和投影仪，例如索尼、飞利浦、TCL、小米、Redmi、海信、创维 / 酷开、Nvidia，以及 Chromecast with Google TV。有些固件会限制安装应用、更换默认桌面或信号源控制。详见[机型参考](#devices)。

<a id="non-android"></a>

### 三星、LG、海信 VIDAA 电视能用吗？

不能。三星 Tizen、LG webOS 和海信 VIDAA 都不是 Android 系统，装不了 Android 应用。

### GenTV 会收集我的数据吗？

没有 GenTV 账号，匿名统计默认关闭。从源码构建的版本不会连接官方服务。详见[隐私说明](PRIVACY.md)（英文）。

### 怎么卸载？

先在电视设置里把默认桌面换回原来的，确认能正常打开；想保留桌面布局的话先导出配置；然后在电视的应用设置里卸载 GenTV。最后关闭 ADB 调试。

## 开发者

| 组件 | 应用 ID | 源码目录 |
|---|---|---|
| GenTV 电视版 | `com.gentv.tv` | `launcher/` |
| GenTV Remote 手机版 | `com.gentv.mobile` | `phone/` |
| 手机调试版 | `com.gentv.mobile.debug` | 独立的测试安装 |

开发构建不需要签名密钥，也不需要服务器权限。以下命令在仓库根目录运行。

电视版（JDK 17、Android SDK 34、build-tools 34.0.0）：

```sh
./launcher/gradlew -p launcher --no-daemon testDebugUnitTest lintDebug assembleDebug
```

手机版（Gradle 用 JDK 21，Android SDK 37，ADB 单元测试用 JDK 25）。手机版构建时会编译并内嵌电视版，所以要把 `GENTV_HOME_JAVA_HOME` 设为 JDK 17 的路径。没有私有发布密钥时，内嵌的电视版是调试签名，只能本地测试。详见[手机端说明](phone/README.md)。

```sh
./phone/gradlew -p phone --no-daemon :core:test :adb:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

仓库检查：

```sh
python3 tools/check_branding.py
python3 tools/check_repository.py
python3 -m unittest discover -s server/wallpaper -p 'test_*.py'
```

### 在线服务

更新、在线壁纸、反馈和匿名统计，只有在构建时配置了 GenTV API 地址才会启用。直接克隆的源码（包括各种 Fork）都不会连接官方服务。要连接你自己部署的服务器：

```sh
./launcher/gradlew -p launcher -PgentvApiBaseUrl=https://api.example.com/tv/ assembleDebug
```

也可以用环境变量 `GENTV_API_BASE_URL`。服务端代码见[壁纸服务](server/wallpaper/README.md)。

### 正式版本

正式版用不进 Git 的私有密钥签名，发布构建不会退回调试签名。详见[发布与签名](docs/release.md)。单独分发电视版时使用 `launcher/app/build/outputs/distribution/GenTV-TV-release.zip`，里面有 APK 和许可文件，请一起分发。

### 文档（英文）

- [环境与安装指南](docs/getting-started.md) · [参与贡献](CONTRIBUTING.md)
- [实现与验证](docs/implementation.md) · [发布前验证](docs/pre-release-validation.md)
- [桌面协议](docs/desktop-protocol.md) · [模块卡片约定](docs/reference/modules.md)
- [性能流程](docs/reference/perf-ab.md) · [模拟器性能记录](docs/emulator-performance.md) · [开发计划](docs/desktop-optimization-plan.md)

## 项目来源

GenTV 基于三个 MIT 许可的开源项目：一个带社区机型清单的原生 Android TV 桌面、一个手机 ADB 伴侣框架，以及一个电视桌面安装器（框架沿用了它的安装和撤销方式）。它们的名称、链接、基线版本和许可证见[第三方声明](THIRD_PARTY_NOTICES.md)。GenTV 与电视厂商及第三方应用均无关联，文中出现它们的名称只是为了说明用途和来源。

## 使用提醒

源码构建和模拟器测试不代表每台真实电视的默认桌面、HDMI、实体遥控按键和恢复功能都已验收。安装 GenTV 不等于建议你停用原厂桌面。请保留备份，维护完成后关闭 ADB 调试。

## 联系与许可

问题反馈：[GitHub Issues](https://github.com/Elaine-Lynn/GenTV/issues)。安全问题：见 [SECURITY.md](SECURITY.md)。

GenTV 以 [MIT 许可证](LICENSE)发布。来自其他项目的组件保留原有版权和许可证，见[第三方声明](THIRD_PARTY_NOTICES.md)和 `licenses/` 目录。
