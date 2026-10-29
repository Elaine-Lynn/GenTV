# GenTV

**桌面更清爽，电视少垃圾，广告少打扰。安装也简单。**

[English](README.md) · 简体中文

[下载 GenTV](https://www.gentv.me/) · [快速开始](#快速开始) · [界面预览](#界面预览) · [源码构建](README.md#build)

![GenTV 电视桌面与手机遥控器](docs/media/readme/overview/01-project-cover.png)

GenTV 帮你把电视收拾干净：换上**没有广告的清爽桌面**，用手机**清理不需要的应用、释放存储空间**，再通过可选的广告过滤减少打扰。手机、AI 助手或 U 盘，选适合你的方式安装。

**无需账号 · 无需 Root · 免费开源**

## 四个理由，给电视换个新体验

- **清爽干净的桌面。** 不塞推广、不放广告，只留下你选择的应用、壁纸和信号源。大磁贴适合遥控器操作，正式电视安装包不到 600 KB。
- **把电视里的垃圾清一清。** 在手机上查看存储、清理缓存，审阅并停用或移除不需要的应用；内置系统组件保护和恢复记录，动手前先备份。
- **减少烦人广告。** GenTV 桌面本身无广告，还可在支持的电视上启用私人 DNS 过滤，减少广告和追踪域名请求。过滤取决于应用和服务商，不保证去除开机广告或所有应用内广告。
- **安装方式，按你方便的来。** 用手机连接电视后安装，让有本地工具权限的 AI 助手协助，或把电视 APK 放进 U 盘直接安装；不用改机，也不用 Root。

手机伴侣还提供遥控器、键盘输入、文件传输和桌面整理，日常操作不用在电视上反复按遥控器。

## 快速开始

| 安装方式 | 怎么做 |
|---|---|
| **手机安装（推荐）** | 在手机安装 GenTV Remote，连接并授权电视，再选择安装 GenTV；还能清理、遥控和管理电视 |
| **AI 助手协助安装** | 把仓库地址和目标设备告诉能使用本地终端的 AI 助手，按[安装指南](docs/getting-started.md#install-with-an-ai-assistant)检查环境、构建或选用 APK，并通过 ADB 安装；配对提示由你确认 |
| **U 盘安装** | 将电视版 APK 复制到 U 盘，在电视文件管理器中打开，按系统提示允许安装并确认；详见[U 盘步骤](docs/getting-started.md#install-the-tv-app-from-a-usb-drive) |

**用手机安装，只需四步：**

1. 从 [gentv.me](https://www.gentv.me/) 在 Android 手机上安装 **GenTV Remote**。
2. 在电视上启用开发者选项和网络调试或无线调试。
3. 打开 GenTV Remote，选择电视，并按电视提示完成配对或调试授权。
4. 开始使用手机遥控，或选择安装 GenTV，体验新桌面。如需更换默认桌面，再到电视系统设置中选择。

**适用设备：**手机和电视均需 Android 8/API 26 或以上。电视管理要求 ADB 可连接且已授权。三星 Tizen 和 LG webOS 无法运行 Android 应用；Android 电视也可能因厂商限制而无法安装、更换默认桌面或使用部分信号源控制。

只安装电视版、使用 CLI 命令安装、从源码构建，或让 AI 助手协助配置，请看[环境与安装指南](docs/getting-started.md)。

## 界面预览

### 清爽桌面，打开电视就看到喜欢的内容

![GenTV 电视桌面效果图](docs/media/readme/overview/05-tv-showcase.png)

![四种壁纸桌面](docs/media/readme/overview/06-readme-wallpapers.png)

### 电视清理和应用管理，手机上操作更方便

从首页进入存储清理、应用管理、恢复与广告过滤；先查看，再选择要处理的项目。

- [首页与清理入口](docs/media/readme/screenshots/phone/01-home.png)
- [应用管理](docs/media/readme/screenshots/phone/04-app-management.png)

### 全部 8 套壁纸桌面原图

![八种壁纸桌面总览](docs/media/readme/overview/02-wallpaper-gallery.png)

- [雪山湖泊](docs/media/readme/screenshots/tv/01-alpine-lake.png)
- [蓝色丝绸](docs/media/readme/screenshots/tv/02-abstract-silk.png)
- [沙漠落日](docs/media/readme/screenshots/tv/03-desert.png)
- [森林晨雾](docs/media/readme/screenshots/tv/04-forest.png)
- [银河夜空](docs/media/readme/screenshots/tv/05-milky-way.png)
- [秋日湖畔](docs/media/readme/screenshots/tv/06-autumn.png)
- [冬日雪景](docs/media/readme/screenshots/tv/07-winter.png)
- [极光](docs/media/readme/screenshots/tv/08-aurora.png)

### 全部 5 张手机界面原图

- [首页与清理入口](docs/media/readme/screenshots/phone/01-home.png)
- [手机遥控器](docs/media/readme/screenshots/phone/02-remote.png)
- [桌面管理](docs/media/readme/screenshots/phone/03-desktop-management.png)
- [应用管理](docs/media/readme/screenshots/phone/04-app-management.png)
- [连接与开始](docs/media/readme/screenshots/phone/05-welcome.png)

### 仓库宣传与分享图

![GenTV GitHub 宣传与分享图](docs/media/readme/overview/04-github-social-preview.png)

此图也可用于 GitHub 仓库 Social preview 设置。

电视画面为 Android TV 模拟器实际截图；手机画面由界面测试渲染，使用演示数据。宣传图为这些界面的排版展示。[完整素材与来源说明](docs/media/readme/README.md)。

## 还能做什么

| 功能 | 能带来什么 |
|---|---|
| 桌面个性化 | 应用排序、改名、分组、自定义磁贴图片，保存或导出桌面配置 |
| 壁纸 | 使用本地图片或可选的在线壁纸；下载后的图片可离线显示 |
| 应用维护 | 252 款机型的社区清理参考、系统组件保护、备份与操作记录；清单不代表逐机型兼容认证 |
| 恢复 | 重新启用已停用的应用，恢复符合条件的、为当前用户移除的预装包；彻底卸载的用户应用需要重新安装 |
| 广告过滤 | 在支持的 Android 9+ 电视上配置私人 DNS；过滤效果取决于应用和 DNS 服务商 |
| 更新与隐私 | 更新包签名和 SHA-256 校验，无需 GenTV 账号，匿名统计默认关闭 |

源码构建默认不配置在线服务。官方构建或自行托管时可配置启用，详见[在线服务](README.md#online-services)与[隐私说明](PRIVACY.md)。

## 开发与更多信息

- [构建命令](README.md#build) · [环境与安装指南](docs/getting-started.md) · [发布签名](docs/release.md)
- [参与贡献](CONTRIBUTING.md) · [验证结果与待验收项](docs/pre-release-validation.md)
- [问题反馈](https://github.com/Elaine-Lynn/GenTV/issues) · [安全问题报告](SECURITY.md) · [隐私说明](PRIVACY.md)

GenTV 以 [MIT 许可证](LICENSE)发布，包含经过修改的第三方开源组件；来源、原作者和许可见[第三方声明](THIRD_PARTY_NOTICES.md)。GenTV 与电视厂商及第三方应用均无关联。

源码构建和模拟器测试不代表每台真实电视都已通过验收。请保留备份，维护完成后关闭 ADB 调试；安装桌面不等于授权停用原厂桌面。
