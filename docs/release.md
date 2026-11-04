# GenTV 正式签名与发布

电视包 `com.gentv.tv`，官网与升级接口已发布 3.7.8 / code30；手机版 `com.gentv.mobile` 已发布 3.7.4 / code20。两者使用同一正式证书，但应用身份与更新清单独立。

正式证书 SHA-256 指纹（已用 v3.7.0 发布的 `gentv-tv-3.7.0.apk` 和 `gentv-mobile-3.7.1.apk` 核对）：

```text
8D:5B:53:78:F9:05:68:80:F3:03:6D:44:BC:2F:4A:48:32:21:41:FB:B8:D8:BC:91:95:1D:61:A4:65:2D:99:7F
```

每次发布后用 `apksigner verify --print-certs` 核对公网 APK 的证书与此一致。更换证书时必须同步更新本文件、README 和安装指南中的指纹。

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
