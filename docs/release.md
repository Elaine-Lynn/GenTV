# GenTV 正式签名与发布

电视包 `com.gentv.tv`，官网与升级接口已发布 3.7.8 / code30；手机版 `com.gentv.mobile` 已发布 3.7.4 / code20。两者使用同一正式证书，但应用身份与更新清单独立。

正式证书 SHA-256 指纹（已用 v3.7.0 发布的 `gentv-tv-3.7.0.apk` 和 `gentv-mobile-3.7.1.apk` 核对）：

```text
8D:5B:53:78:F9:05:68:80:F3:03:6D:44:BC:2F:4A:48:32:21:41:FB:B8:D8:BC:91:95:1D:61:A4:65:2D:99:7F
```

每次发布后用 `apksigner verify --print-certs` 核对公网 APK 的证书与此一致。更换证书时必须同步更新本文件、README 和安装指南中的指纹。

## 本地正式签名候选 3.7.12

电视版 3.7.12 / code34 移除观看间隔中的“30秒（测试）”选项，仅提供 5、10、15、20 分钟，默认 10 分钟。升级时若旧版选中了 30 秒测试，将移除旧标记、恢复 10 分钟并清零累计观看时长；正常分钟配置继续保留。正式构建的 `CHILD_TIMER_TEST_MODE=false`，即使残留旧偏好也不会执行 30 秒计时分支；仅独立隔离 QA 构建可显式启用短计时测试，不在正式 UI 提供入口。

344 项 Release 单元测试、lintRelease、正式签名构建及仓库规定检查通过；核对正式 APK 不含测试选项资源、包名为 `com.gentv.tv`、证书与既有正式版本一致。APK 为 4,934,224 字节，SHA-256 为 `58fe65a199b9dde4951e0686bb63c9fe20480a8fd335672a96694ee6865dd8d1`。本次仅生成本地安装包及含许可分发 ZIP，未安装到 B3p、未发布官网或更新接口，也未重跑真实 10 分钟计时验收。

## 本地兼容性修复候选 3.7.11

电视版 3.7.11 / code33 修复防沉迷设置在 720p / 高显示密度下卡片和按钮裁切的问题，允许遥控焦点滚动到开启按钮，并调整两行选项布局；修复部分 adbd 延迟返回旧流关闭消息导致后续指令偶发失败的问题。扩展 AOSP Android 9、Android TV 16、Google TV 16 三种模拟环境验证；本机 ADB 能力与授权后的计时能力分别记录，Android 9 镜像的系统授权由测试工具模拟，不能记作自连成功。详见[兼容性验证记录](child-compatibility-matrix-2026-11-05.zh-CN.md)。候选沿用正式证书，仅在本地生成，尚未发布官网，也未覆盖安装到 B3p。

## 本地修复候选 3.7.10

电视版 3.7.10 / code32 增加防沉迷系统的本机 ADB 辅助开启。点击“开启计时服务”直接连接系统实际报告的本机端口；只有服务真正连接后按钮才显示“计时服务已开启”。保留其他无障碍服务，写入前保留本地恢复基线，失败时尝试带并发检查的精确回滚。无标准无障碍入口的固件可采用此路径，但需先开启传统网络 ADB 并接受系统授权；不支持仅无线配对/TLS 调试的路径。

本地候选使用现有正式证书。B3p / Android 9 上的独立测试包已验证 App 连接自身及服务绑定；Android 16 TV 模拟器上的正式签名隔离 Release 已通过 29 项完整流程检查，包括按钮状态及连续两轮 30 秒弹题、答对后恢复观看；补测也确认模拟器关闭 ADB 调试以及保持关闭重启后，均在约 30.5 秒正常弹题并可答对恢复播放；B3p 正式包完整流程仍待在场复测。详见[验收记录](child-self-adb-validation-2026-11-05.zh-CN.md)。此候选尚未发布官网或更新接口，不将其他设备视作已验证。新加的 8 项协议与设置回滚单元测试、Release 单元测试及 lintRelease 均通过。

## 电视版 3.7.8

电视版 3.7.8 / code30 于 2026-11-04 发布，加入互动养鱼桌面。APK 为 4,414,362 字节，使用现有正式证书，更新策略为可选。当贝 DBB3 Pro 已验证从 3.7.7 覆盖升级后仍进入原先选择的养鱼桌面。官网 APK、含许可的分发 ZIP 和应用内更新 APK 已与本地签名构建逐一核对。

## MAC 辅助统计更新

电视 3.7.7 / code29、手机 3.7.4 / code20 支持 schema 3：有效本机 MAC 优先、安装 ID 回退，服务端只存 MAC 的加密哈希。系统可能限制 MAC 读取，不能据此保证精确物理设备数。两端升级后默认关闭本版统计，需用户阅读新说明并重新开启；历史无 MAC 的重装记录不能追溯合并。官网、隐私说明与升级接口同步发布，更新保持非强制。验收见 [发布记录](mac-analytics-release-2026-11-03.md)。

## 构建

在私有环境提供 `phone/keys/signing.properties` 及其引用的 keystore，字段与手机版一致：storeFile、storePassword、keyAlias、keyPassword；正式构建另加 `apiBaseUrl=https://api.gentv.app/tv/`，否则更新、在线壁纸、反馈与统计均关闭。文件均在忽略目录，禁止提交、上传服务器或写入日志。正式构建缺配置或发现 Android Debug 证书会失败。

使用 JDK 17、Android SDK 34 / build-tools 34.0.0：

```sh
./launcher/gradlew -p launcher --offline --no-daemon testReleaseUnitTest lintRelease assembleRelease
python3 tools/check_repository.py
```

手机资产构建 `tools/build_phone_assets.py` 仅从本工程源码生成 GenTV 电视包，并核对包名、正式证书及 SHA-256。

独立分发使用 `launcher/app/build/outputs/distribution/GenTV-TV-release.zip`，其中包含 APK 和完整适用许可。手机安装包内也包含其内置电视包的许可。

## 证书迁移

3.6.1 / code21 及以前的电视开发包采用 debug 证书。Android 不允许不同证书对同一包名直接覆盖。不要通过关闭签名验证或静默卸载来绕过。

已有 debug 安装需先通过手机管理端导出桌面配置并保存主题/壁纸文件，人工确认后移除开发包、安装正式包，再恢复配置并核对默认 Home。当前没有执行这一步，也未删除设备数据。正式签名用户后续可正常使用已验证的同签名升级通道。

服务器为旧 `GenTV/3.0` 至 `GenTV/3.6.x` 客户端保留 `update-legacy.json`；正式通道使用 `update.json`。不向旧开发安装强推无法安装的正式包。候选默认 `forceUpdate=false`、`minSupportedVersionCode=0`。

## 发布

工具复用本机 SSH 认证，不携带私钥。以下 host 为示例，实际参数由部署环境提供：

```sh
python3 tools/deploy_tv_api.py --host admin@host
python3 tools/publish_tv_release.py --host admin@host
```

发布工具验证 APK 大小、身份、正式证书与 keystore 一致性，版本路径不可覆盖为不同内容，最后原子更新清单。可通过重复 `--ssh-option` 传入代理等 SSH 配置。发布后核对公网 APK SHA256，并分别检查旧版和新版 User-Agent 的更新通道。

## 尚需验收

真实电视上的启动、Home 交接、实体遥控器、HDMI、待机/恢复与性能基线复核。当前仅完成模拟器及服务端验证。正式签名候选可供验收，不等同全部电视机型上线通过。

## TV APK 大小策略

互动养鱼桌面的高清贴图计入电视安装包，Release APK 上限为 5 MB（5,000,000 字节，含上限）。仓库检查和 TV 发布工具都报告实际大小并阻止超限包。
