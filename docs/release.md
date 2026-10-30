# GenTV 正式签名与发布

电视包 `com.gentv.tv`，官网与升级接口已发布 3.7.2 / code24；手机版 `com.gentv.mobile`。两者使用同一正式证书，但应用身份与更新清单独立。

正式证书 SHA-256 指纹（已用 v3.7.0 发布的 `gentv-tv-3.7.0.apk` 和 `gentv-mobile-3.7.1.apk` 核对）：

```text
8D:5B:53:78:F9:05:68:80:F3:03:6D:44:BC:2F:4A:48:32:21:41:FB:B8:D8:BC:91:95:1D:61:A4:65:2D:99:7F
```

每次发布后用 `apksigner verify --print-certs` 核对公网 APK 的证书与此一致。更换证书时必须同步更新本文件、README 和安装指南中的指纹。

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

以桌面视觉效果为先，保留壁纸画质，将 Release APK 预算从 576 KiB 调整为不超过
1 MB（1,000,000 字节，含上限）。仓库检查和 TV 发布工具使用同一上限，报告实际大小，
超出时阻止通过。签名、校验和、应用身份检查继续执行。
