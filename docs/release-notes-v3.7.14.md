# GenTV TV v3.7.14 更新日志

电视版 `com.gentv.tv` · 版本 3.7.14 / code36 · 最低 Android 4.2（API 17）· 沿用正式签名证书。基于远程 `main` 的 `b03b1f0`，包含 v3.7.13 的已有功能；手机版版本不变。

## 更新内容

- 修复旧版升级包体积阻碍：v3.7.12 只接受不超过 8 MiB 的更新 APK，而 v3.7.13 正式包为 18,655,742 字节。本版压缩内置素材，正式包为 **8,057,421 字节**，低于 8 MiB（8,388,608 字节）。
- 保留两段内置动态壁纸的 1920×1080、30 fps 和 H.264 Baseline 格式，重新编码以缩小体积；三张玻璃静态壁纸保持原尺寸，改用 WebP；养鱼桌面底图也重新压缩。壁纸项目和功能未减少。
- Release 构建新增体积检查，超过旧版升级器的 8 MiB 上限即构建失败。v3.7.14 自身保留 128 MiB 的后续更新上限；之后的电视版本须使用高于 36 的 versionCode。

## 校验与发布范围

Release 单元测试、`lintRelease`、正式签名构建和仓库检查通过。APK 包名、版本、最低 API 和证书已核对；APK SHA-256 为 `e90d180b57a460caaaf1355b1224f42ae343730c8acad213b396d6146efa5d6c`。完整视频逐帧与原片对比的 SSIM 分别为 0.9932、0.9928；电视实屏画质与 Android 4.2 真机兼容仍待验证。

本仓库提供此版的[更新配置](releases/v3.7.14/config.json)。目前服务端仍返回 v3.7.13 / code35；仅发布 GitHub tag 不会自动切换应用内更新清单。测试盒子的 DNS 对 `api.gentv.app` 解析间歇性失败，此网络问题也需单独修复，才能完成旧版自更新实机验收。

English summary: GenTV TV 3.7.14 (code 36) is a compact update bridge from the current `main` branch. It preserves both 1080p/30 fps bundled video wallpapers and existing features while keeping the signed APK below the 8 MiB limit in the v3.7.12 updater. The phone app is unchanged. Real-TV self-update validation and server rollout remain pending.
