# 游戏基础插件与独立游戏插件

游戏功能使用两层插件。两个层级均为下载后动态加载，不使用 split APK、PackageInstaller 或系统安装界面。

```text
GenTV 主应用（launcher/app）
  └─ 游戏基础插件（launcher/game）
       ├─ 游戏首页、目录缓存、游戏插件下载和独立更新
       ├─ 共用 Gecko 浏览器、原生库、输入桥、局域网遥控服务
       └─ 独立游戏插件（当前 launcher/game-kart，后续按游戏增加）
            ├─ 游戏入口、适配代码
            └─ 本游戏的手机操控页面及资源
```

主应用只管理基础插件的下载、校验、更新及宿主生命周期。进入工具箱“游戏”，首次下载对应 ABI 的基础组件后显示游戏首页。选中赛车后才下载赛车插件；之后每次打开游戏检查独立更新。已有兼容游戏在断网时仍可加载（网页游戏本身仍需要联网）。

当前赛车插件包含远程网页入口和控制适配，不包含或重新分发第三方游戏网站的全部内容。新增游戏可携带自己的代码和资源，通过 `GameContentEntry` 提供入口及遥控页。浏览器和局域网服务由基础插件复用。

## 边界与版本

| 模块 | 内容 | 版本 / 协议 |
| --- | --- | --- |
| `game-api` | 主包与基础插件之间的生命周期接口 | host API 2 |
| `game` | 游戏首页、共享内核、子插件管理 | 2.0.0 / code 100 |
| `game-content-api` | 基础插件与各游戏间的接口，仅编入基础插件 | content API 1 |
| `game-kart` | 赛车入口和专用手机操控页，不含 Gecko 和 `.so` | 1.0.0 / code 1 |

基础插件 code 100 为迁移起点，避免与旧试验组件的版本号冲突；此后使用独立递增序列。旧 host API 1 主包需要先升级一次，之后普通游戏更新不需要主包升级，基础插件升级也不清除游戏插件。

主包仅编译 `game-api`，不依赖 `game`、`game-content-api` 或 `game-kart`。基础插件通过 `DexClassLoader` 加载；子插件使用基础插件的类加载器作为父加载器，复用共享接口与内核。Android 9 / API 28 起支持，ABI 为 ARMv7 / ARM64。其他 Android 版本保留桌面功能并提示游戏组件不支持。

## 校验、缓存与失败恢复

两层组件均校验 HTTPS 下载地址、大小、SHA-256、包名、APK 版本、签名证书和兼容范围；签名必须与主包一致。基础插件保存在主包私有 `game-runtime/`，游戏插件独立保存在 `game-content/<id>/versions/<code>/`。激活指针以 AtomicFile 写入。下载和校验失败不会替换原有的兼容游戏，不允许版本降级。相同版本号的已发布附件不可替换，修复时提高版本号。

游戏目录只缓存成功解析的数据。缺网时使用缓存目录，首次离线使用内置的真实游戏目录。目录只含名称及入口元数据，不夹带游戏插件。主接口失败立即尝试 GitHub 备用清单。

## 资源与构建

自有资源使用模块前缀：基础资源 `game_base_`，赛车资源 `game_kart_`。Gecko 的 `omni.ja` / `.so`、WebExtension 的 `manifest.json` 保留第三方固定名称。子插件不得携带另一套内核。发布工具会检查资源前缀、引擎重复与签名。

```sh
./launcher/gradlew -p launcher --no-daemon :app:assembleRelease :game:assembleRelease :game-kart:assembleRelease
python3 tools/package_game_runtime.py --sdk "$ANDROID_HOME" \
  --base-apk launcher/app/build/outputs/apk/release/app-release.apk \
  --game-dir launcher/game/build/outputs/apk/release
python3 tools/package_game_content.py --sdk "$ANDROID_HOME" \
  --base-apk launcher/app/build/outputs/apk/release/app-release.apk \
  --apk launcher/game-kart/build/outputs/apk/release/game-kart-release.apk \
  --id kart --entry-class com.gentv.game.kart.KartGamePlugin
```

基础组件更新使用 `-PgameVersionCode=101 -PgameVersionName=2.0.1`；赛车更新使用 `-PkartVersionCode=2 -PkartVersionName=1.0.1`。两者互不绑定。Debug QA 可加 `--allow-debug`，不得把 Debug 构建作为正式发布。后端部署和 GitHub 文件布局见 [独立更新部署](game-plugin-deployment.md)。

## 验证记录

Android 9 / ARMv7 真机通过本机 HTTPS 测试源完成两层实际下载与校验，未系统安装任何插件。验证了基础插件独立升级仍保留赛车、赛车 1→2 独立升级、手机操控页与认证指令、连接后隐藏二维码、返回游戏中心并恢复焦点、重建宿主后继续复用内核。错误版本 3 的摘要不会替换已生效的赛车 2。

主包、ARMv7 / ARM64 基础组件及赛车均完成正式签名构建。ARM64 已做构建和包结构校验，尚未真机验证。公开服务器和 GitHub 附件尚未部署，本机 HTTPS 验收不等于正式在线下载已上线。
