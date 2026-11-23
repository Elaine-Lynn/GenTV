# GenTV TV 3.7.13

电视版 `com.gentv.tv` · 版本 3.7.13 / code35 · 最低 Android 4.2（API 17）· 沿用正式签名证书。此 Release 仅包含电视 APK；手机版版本不变。

## 更新内容

- 将电视 launcher 的源码构建最低版本适配到 Android 4.2（API 17）。Android 4.2 实机尚未验证，安装与各功能的兼容性仍需在真实设备上确认。
- 完善首页壁纸与天气：视频壁纸改用 ExoPlayer 静音播放，减少视频切换和 U 盘壁纸场景的内存压力；自定义壁纸可添加多张，并可通过局域网页上传图片和视频。图片上限 4 MB，视频上限 30 MB。
- 改进个性化页面的本地文件选择、U 盘拔出容错、预览与遥控焦点；首页保持亮屏，并保留可单独配置的 GenTV 航拍屏保。
- 小工具箱改为分组的电视列表，更新图标和间距；首页应用菜单增加多语言适配。
- 儿童模式受管应用选择增加固定的确认操作区，并强化焦点框；修复电视自更新对旧 Android 版本及较大 APK 的校验限制。

## 验证与发布范围

正式签名构建、355 项 Release 单元测试、`lintRelease` 和仓库检查通过。APK 包名、版本、最低 API 和证书已核对；APK SHA-256：`2ea8072795e577bd1334cda44365762a6a304cb7ac80a4e43edf397c7862ef42`。Android 4.2 真实设备验证待完成；不同电视的系统网页、媒体解码和 U 盘权限仍受固件影响。

本次仅发布 GitHub Release，未将官网或应用内更新清单切换到 3.7.13。`feature/game` 的独立游戏组件不属于此版本。

---

**English summary:** GenTV TV 3.7.13 (code 35) adds a source-built Android 4.2 / API 17 compatibility path, improves silent video wallpapers, multi-item custom wallpapers and LAN transfers, refines the TV toolbox and child-mode app picker, and fixes legacy update checks. The Android 4.2 path has not yet been validated on a physical device. This is a GitHub-only TV release; the phone app and in-app update channel are unchanged.
