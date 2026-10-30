# 上线前检查 — 2026-10-30

> 历史记录：本文的检查对象是递增版本号之前的候选包。其中的版本冲突阻断项已在发布时解决，正式发布为 3.7.2 / code24，最终产物和线上核对见 [TV 3.7.2 发布验证](tv-3.7.2-validation.md)。

结论：已验收源码快照的构建、测试和模拟器回归通过；检查期间新增改动的本地测试和末次完整性复核亦通过，但当前候选不能直接作为新版本发布。尚未修改线上清单、上传 APK、调整版本号或执行正式发布。

## 发布阻断项（已解决）

1. **版本冲突**：候选仍为 `3.7.1 / versionCode 23`，与实测线上正式通道相同，但候选 APK 内容已经变化。客户端只在远端 versionCode 更大时提示更新；发布工具也不允许同一版本路径被不同内容覆盖。发布前须分配新版本名并递增 versionCode，例如 `3.7.2 / 24`，重新构建、校验和验收最终包。

## 本轮检查结果

| 项目 | 结果 |
|---|---|
| TV Release 单元测试 | 310 项，全部通过 |
| TV Release Lint、构建、签名 | 通过；无 Error/Fatal |
| 手机 core | 267 项，全部通过 |
| ADB 模块 | 14 项，全部通过 |
| 本地 TV care 适配器 | 初次快照 27 项通过；检查期间新增改动后补跑 31 项，全部通过 |
| 手机 app 常规测试 | 196 项：195 通过，1 项安装测试默认跳过 |
| 显式启用手机→模拟器安装测试 | 另行执行 1 项，通过；原配置及默认 Home 保持不变 |
| 手机 Debug Lint、构建 | 通过；无 Error/Fatal |
| 模拟器桌面集成 | 62 项，通过 |
| 模拟器更新弹窗焦点 | 6 项，通过 |
| 模拟器 HDMI 提醒及 Inputs 入口 | 7 项，通过；状态为夹具模拟 |
| 模拟器强制/可选更新策略 UI | 12 项，通过 |
| 服务端单元测试 | 20 项：18 通过，2 项 Linux/GNU 部署测试在 macOS 跳过 |
| tools 单元测试 | 4 项，通过 |
| 品牌检查 | 通过 |
| 框架锁定清单、仓库边界 | 末次通过，430 个框架文件校验一致；中途发现过锁与并发修改不一致，已在锁同步后复核 |
| `git diff --check` | 通过 |

模拟器集成合计 87 项；另有真实 ADB 安装链路测试 1 项。

本轮发现并修正了 `DesktopPreviewEmulatorTest.kt` 的过时产物引用：`gentv-home-preview.apk` 改为当前构建产物 `gentv-home.apk`。显式启用该测试后已实际安装、打开桌面并比对前后配置、Home 和临时文件清理结果。

检查期间，`LauncherHandover.kt` 与本地 TV care 的 Care、Main、CareTest 出现新增修改，中途框架锁校验失败。末次检查时框架锁已同步；已对当时工作区补跑 core、TV care、手机 app 测试、Lint 和构建，全部通过（TV care 测试增至 31 项）。独立源码快照建立在这些后续修改之前，后续变更的证据为工作区增量复核，而非另一轮完整干净快照构建。本次未覆盖或回退其他任务的修改。

## 干净源码构建

将工作区已跟踪及待纳入的源码复制到独立临时目录，排除私有 keys、local.properties、既有 build/.gradle、work、shots、output。使用已安装 Android SDK、JDK 17/21 和本机依赖/工具链缓存：

- TV Debug：310 项测试、Lint、APK 构建通过。
- 手机 core、ADB、TV care、app 测试、Lint、APK 构建通过。
- 无正式签名配置时，手机确实嵌入源码构建且 SHA-256 校验一致的 Debug TV APK。
- 修正测试产物引用后，在独立源码目录重跑手机 app 测试通过。未对这个 Debug APK 执行覆盖正式签名安装。

此项证明源码不依赖私有密钥和旧构建输出；没有验证全新操作系统上的首次依赖下载、Windows 或 Linux 构建。

## 候选产物与线上只读检查

| 项目 | 实测 |
|---|---|
| Application ID | `com.gentv.tv` |
| 版本 | `3.7.1 / 23`，需要发布前递增 |
| Android 最低版本 | API 26 |
| Release APK 大小 | 840,360 字节，低于 1,000,000 字节限制 |
| 正式证书 SHA-256 | `8d5b5378f9056880f3036d44bc2f4a48322141fbb8d8bc91951d61a4652d997f` |
| 候选 APK SHA-256 | `c6b961e030456f715ecdac4b59c2c64b96e79229c4266eb053a22d6d85d5db4e` |
| 信号源适配表 | TV、手机均为同一份 72 条记录 |
| 手机内嵌 TV APK | 与本地 Release 候选逐字节一致，构建清单 SHA-256 一致 |
| 独立分发 ZIP | APK 与候选一致，包含许可/声明文件 |
| 正式更新接口，`GenTV/3.7.1` | HTTPS 200，返回 `3.7.1 / 23`，`forceUpdate=false`、`minSupportedVersionCode=0` |
| 正式通道 APK | 使用客户端 User-Agent 下载 HTTP 200；SHA-256 与清单一致，签名与正式证书一致 |
| 旧更新通道，`GenTV/3.6.1` | 返回 `3.6.1 / 21`；APK 下载及清单 SHA-256 通过，未混入当前正式签名版本 |

未执行线上清单变更，也没有验证“新版本清单→自动提示→下载→系统安装确认→升级成功”的完整新版本链路。应在确定新的 versionCode 后使用最终候选再验收。普通更新、强制策略、下载元数据校验和弹窗焦点已有本地/模拟器检查。

## 非阻断性遗留项

- TV Release Lint：148 个 Warning、135 个 Information；手机 Debug Lint：26 个 Warning。构建可通过，但不应称作零警告。
- 手机包含 3 个依赖来源的 `TrustAllX509TrustManager` 警告，属于既有 Kadb/Bouncy Castle 配对/传输信任模型审查项。本轮没有证实应用 HTTPS 校验被绕过，也未改变其信任实现。
- 工作区还有其他待提交功能改动及新增文件。发布时应提交完整源码、适配表及框架锁定清单，记录对应 Git 提交；不能只提交本次 UI 的几个文件。
- 私有设备恢复记录、签名文件、APK、截图及构建输出应继续留在忽略目录，不进入提交。

## 复核命令

```sh
./launcher/gradlew -p launcher --no-daemon testReleaseUnitTest lintRelease assembleRelease
./phone/gradlew -p phone --no-daemon :core:test :adb:test :tv-care:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./phone/gradlew -p phone --no-daemon -PemulatorSerial=emulator-5554 :app:testDebugUnitTest --tests com.gentv.mobile.DesktopPreviewEmulatorTest
python3 tools/check_branding.py
python3 tools/check_repository.py
python3 -m unittest discover -s server/wallpaper -p 'test_*.py'
python3 -m unittest discover -s tools -p 'test_*.py'
git diff --check
```

构建所需 JDK/SDK 变量及授权流程见 getting-started.md。模拟器序列号应以实际 `adb devices` 为准；设备验收结束后关闭 ADB 调试。
