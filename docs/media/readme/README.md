# GenTV README 素材包

生成日期：2026-10-29。共 21 张 PNG，按用途组织；可随仓库提交。

## 桌面对比图

两张图取自 [gentv.me](https://www.gentv.me/) 首页的桌面对比模块，由官网的 WebP 原图无损转换为 PNG，没有裁切或改动像素。README 首屏把它们并排展示。

| 文件 | 内容 |
|---|---|
| [01-traditional-tv.png](comparison/01-traditional-tv.png) | 传统电视桌面（生成图，片名、海报和人物均为虚构，不对应任何厂商） |
| [02-gentv-home.png](comparison/02-gentv-home.png) | GenTV 桌面（森林晨雾壁纸，Play 商店获得焦点） |

## 项目介绍图

| 文件 | 用途 |
|---|---|
| [01-project-cover.png](overview/01-project-cover.png) | README 首屏：电视桌面 + 手机遥控器 |
| [02-wallpaper-gallery.png](overview/02-wallpaper-gallery.png) | 八种壁纸桌面总览 |
| [03-phone-companion.png](overview/03-phone-companion.png) | 手机首页、遥控器、桌面管理三场景拼图 |
| [04-github-social-preview.png](overview/04-github-social-preview.png) | GitHub 仓库 Social preview，1280 × 640 |
| [05-tv-showcase.png](overview/05-tv-showcase.png) | TV 桌面大图展示 |
| [06-readme-wallpapers.png](overview/06-readme-wallpapers.png) | README 正文精选四种壁纸，较八图总览更便于阅读 |

## TV 桌面原始截图

全部为 1920 × 1080 的 Android TV 模拟器实际截图，运行本项目源码构建的 Debug 应用。通过应用现有主题配置接口切换本地壁纸后，直接 screencap 捕获；没有把壁纸后期贴到桌面上。

| 文件 | 壁纸 |
|---|---|
| [01-alpine-lake.png](screenshots/tv/01-alpine-lake.png) | 雪山湖泊 |
| [02-abstract-silk.png](screenshots/tv/02-abstract-silk.png) | 蓝色抽象丝绸 |
| [03-desert.png](screenshots/tv/03-desert.png) | 沙漠落日 |
| [04-forest.png](screenshots/tv/04-forest.png) | 森林晨雾 |
| [05-milky-way.png](screenshots/tv/05-milky-way.png) | 银河夜空 |
| [06-autumn.png](screenshots/tv/06-autumn.png) | 秋日湖畔 |
| [07-winter.png](screenshots/tv/07-winter.png) | 冬日雪景 |
| [08-aurora.png](screenshots/tv/08-aurora.png) | 极光 |

## 手机界面预览

[screenshots/phone/](screenshots/phone/) 下 5 张图由当前源码的 Robolectric/Roborazzi 测试重新渲染。它们是真实界面代码的测试状态图，不是实机连接成功的证据。设备名称、应用列表和状态为测试夹具；没有使用连接中的真实手机或真实电视资料。

## 来源与使用边界

- TV 源码版本：3.7.0/code22；手机源码版本：3.7.1/code17。手机英文“应用精简”文案修正后重新渲染首页。
- TV 截图使用专用只读模拟器实例，拍摄完成后已关闭。其他设备没有被安装或修改。
- 前 7 套壁纸是 GenTV 自有的 AI 生成壁纸，由在线壁纸服务提供，源文件不随仓库发布。极光来自仓库中的 `phone/framework/data/themes/scenes/wallpapers/04-aurora.jpg`，来源与许可见 [第三方声明](../../../THIRD_PARTY_NOTICES.md)。
- 桌面使用应用支持的配置：每行 5 个应用、程序坞背景透明度选项 30%（`columns=5`、`dock_opacity=30`）、本地壁纸、10% 壁纸遮暗、关闭应用背景画；图中布局属于一种可配置展示，不代表每次首次启动的默认布局。
- 第三方应用名称和图标用于展示其在 GenTV 中的识别效果，不表示合作或背书。图片不代表所有电视机型均通过验收。
- overview/ 是截图外部排版形成的介绍效果图，截图只做等比缩放；screenshots/ 是原始界面画面。PNG 已做无损压缩。
- [DESIGN.md](DESIGN.md) 记录视觉方向；[MANIFEST.md](MANIFEST.md) 记录尺寸、字节数与 SHA-256。

## README 引用示例

从仓库根 README 使用：

```markdown
![GenTV project overview](docs/media/readme/overview/01-project-cover.png)
![GenTV wallpaper gallery](docs/media/readme/overview/06-readme-wallpapers.png)
![GenTV Remote interface previews](docs/media/readme/overview/03-phone-companion.png)
```

社交预览图需要在 GitHub 仓库设置中手动选择；本次没有修改远端设置。
