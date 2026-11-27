# GenTV TV v3.7.19 更新日志

电视版 `com.gentv.tv` · 版本 3.7.19 / code41 · 最低 Android 4.2（API 17）· 沿用正式签名证书。手机版版本不变。

## 更新内容

### 控制中心

- 首页右上角打开的控制中心改为玻璃面板：输入源、网络、显示、内存清理四个快捷按钮，壁纸与屏保，Gen 扩展，GenTV 设置与电视系统设置。
- 底部依次为：分享码、设置开机启动应用、设为系统桌面、防沉迷系统、软件更新、关于 GenTV。
- 内存清理改为火箭动画加角落提示，停顿更短。
- 「关于 GenTV」重新设计：品牌、版本、三项承诺（零广告、开源免费、随时可退）、官网与源代码地址，以及扫码访问官网的二维码。
- 「软件更新」打开即自动检查，状态徽章显示检查中、已是最新、发现新版本、下载进度与失败重试。

### 分享码

- 新增分享码：用手机在同一局域网把 GitHub 仓库等链接发送到电视，电视向 GenTV 服务申请 4 位分享码，任何一台 GenTV 输入同一个码都能打开。纯数字码用完前不会出现字母，字母不含 I、O。
- 输入页数字在第一行，输满 4 位自动打开；「我要分享」固定在「我的分享码」首位。
- 打开 GitHub 仓库时列出可下载的 APK：应用图标、名称、版本、大小，以及下载、安装、打开操作；支持断点续传和 Release 附件。

### 壁纸与屏保

- 壁纸与屏保页改为深色画廊，包含萌宠、自然、唯美、图片分类，可按分类随机轮播，并有自定义壁纸行和局域网传图。
- 屏保可选航拍视频、当前壁纸或关闭；在首页按返回键可直接进入所选屏保。
- 全新安装后会在后台下载壁纸库中的第一个小猫视频并设为壁纸；升级安装或已选过壁纸的电视不受影响。
- 「个性化」页面并入壁纸与屏保。

### 首页

- 「移至文件夹」修复：新建的文件夹出现在原应用的位置，首页应用较多时也不会被收进「更多应用」；文件夹图块显示其中的应用图标、名称和数量。
- 选择文件夹、命名（含一键预设名称）、打开文件夹、管理与解散都改为玻璃样式弹窗。
- 应用图块可使用 GenTV 服务镜像的官方横幅；右上角图标作为一个焦点组。
- 移除首页内置天气、IP 城市定位、养鱼桌面与手动选城市。Gen 插件桌面内容仅在 Android 5.0 及以上显示。

## 隐私

分享码的名称、链接和创建电视的密钥哈希保存在 GenTV 服务端，详见[隐私说明](../PRIVACY.zh-CN.md)。全新安装时的默认壁纸从 GenTV 服务下载。

## 发布信息

正式签名 APK 为 6,575,516 字节，SHA-256 为 `061a661bd82511694d43e40850df7293db02a4ac3fbf457afaa36cdb009a8441`。此更新为可选更新，`forceUpdate=false`。正式版单元测试、lintRelease 和仓库检查通过。上述功能在 Android TV 模拟器上逐项验证；当贝 DBB3 Pro 上验证的是同源码的调试签名包。正式签名包的实机覆盖升级、Android 4.2 真机和完整自更新流程仍待验证。

English summary: GenTV TV 3.7.19 (code 41) rebuilds the Control Center, About and Software update in a glass style, adds four-character share codes any GenTV can open, a dark wallpaper and screensaver gallery with a kitten video default for new installs, and fixes Move to folder with redesigned folder sheets. Built-in weather, IP city lookup and the fish pond desktop are removed. The phone app is unchanged.
