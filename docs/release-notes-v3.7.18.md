# GenTV TV v3.7.18 更新日志

电视版 `com.gentv.tv` · 版本 3.7.18 / code40 · 最低 Android 4.2（API 17）· 沿用正式签名证书。手机版版本不变。

## 更新内容

- 天气在尚未选定城市时，可通过 GenTV 服务按公网 IP 识别城市；失败时每 5 分钟重试，24 小时内最多尝试 3 次。用户手动选择的城市不会被自动定位覆盖。
- 个性化页面增加在线壁纸列表。选择后将图片保存到电视的自定义壁纸中，断网后仍可使用。
- 工具箱进入个性化等二级页面后，返回时保留工具箱的展开状态和焦点。
- 自更新增加 GitHub 备用清单与下载地址：主服务请求或下载失败时，尝试经过版本、摘要和签名校验的备用来源。

## 发布信息

正式签名 APK 为 7,503,532 字节，SHA-256 为 `2f83da996a08244a8db709e892d816d251a68a7e152455fa27d79c04a26dbfdd`。此更新为可选更新，`forceUpdate=false`。正式版单元测试、lintRelease 和仓库检查通过；已在当贝 DBZ1 Pro（`.85`）覆盖安装并启动，ADB 返回 `Success` 和 `Status: ok`。Android 4.2 真机、在线壁纸网络链路及完整自更新流程仍待实机验证。

English summary: GenTV TV 3.7.18 (code 40) adds optional IP-based weather city lookup, an online wallpaper gallery, toolbox state restoration, and a verified GitHub update fallback. The phone app is unchanged.
