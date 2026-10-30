# TV 3.7.2 发布验证

2026-10-30 发布电视包 `com.gentv.tv`，versionName `3.7.2`、versionCode `24`。手机版保持 `3.7.1 / code17`。

## 正式产物

- APK：`gentv-tv-3.7.2.apk`，840,356 bytes，低于 1,000,000 bytes 预算。
- SHA-256：`4e458cc05adc266f85144a9d3e2c5bf5c2f800de7b4cfd2cfd61ba2abfe4a729`。
- 正式证书 SHA-256：`8d5b5378f9056880f3036d44bc2f4a48322141fbb8d8bc91951d61a4652d997f`。
- 公网地址：https://api.gentv.app/tv/releases/gentv-tv-3.7.2.apk
- 官网备用地址：https://www.gentv.me/downloads/gentv-tv-3.7.2.apk

## 验证结果

- JDK 17 下 `testReleaseUnitTest lintRelease assembleRelease` 成功；310 项 Release 单元测试通过，Lint 无错误，已有告警保留。
- branding、repository、server/wallpaper 单元测试、`git diff --check` 通过；服务端测试有两项 Linux 专属用例在 macOS 跳过。
- 模拟器 `emulator-5554` 从正式 3.7.1 覆盖安装：`Success`；启动 `Status: ok`；配置与默认 Home 保持一致。
- 公网中英文首页均指向新 TV 包，手机版链接保留 3.7.1；`data/release.json` 的 TV 为 code24，手机版为 code17。
- 以 `GenTV/3.7.1` 和 `GenTV/3.7.2` 请求升级接口均得到 3.7.2 / code24，非强制更新。`GenTV/3.6.1` 仍得到旧证书兼容通道 code21。
- API 与官网两个公网地址下载所得 APK 均与本地产物 SHA-256 一致；公网 APK 经 apksigner 验证为正式证书。
- 服务端手机版、旧版兼容清单与发布前备份逐字节一致。

本次未操作真实电视。模拟器不能验证品牌电视的 HDMI 热插拔及真实信号状态；此前上线前检查与该项限制见 `pre-release-validation-2026-10-30.md`。

## 部署与回退

官网按带时间戳的发布目录部署，通过 `current` 符号链接原子切换；上一版本目录保留不删。发布前已备份服务端 update、legacy、mobile 清单及官网目录记录。具体服务器路径记录在维护者的私有部署记录中，不放入公开仓库。

需要回退时，将官网 `current` 原子切回上一版本目录，并用备份的 `update.json` 通过临时文件原子替换线上清单。正式 APK 为不可变发布文件，保留各版本产物；回退不删除新包。
