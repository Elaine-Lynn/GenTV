# 两层游戏插件的独立更新部署

代码提供以下接口，服务端部署及附件上传另行执行。本地构建完成不代表线上已经提供下载。

## 主接口与 GitHub 备用接口

主接口基于主包构建配置的 API_BASE_URL，不在插件中写死 API 域名。

| 用途 | 主接口（默认路径） | update-manifest 分支中的备用文件 |
| --- | --- | --- |
| 基础插件 | `/tv/plugins/game/update?abi=armeabi-v7a` 或 `arm64-v8a` | `game-runtime/latest/<abi>/manifest.json` |
| 游戏目录 | `/tv/plugins/games/catalog` | `game-runtime/catalog.json` |
| 单款游戏 | `/tv/plugins/games/kart/update` | `game-runtime/games/kart/latest/manifest.json` |

备用根路径为本仓库 GitHub raw 的 `update-manifest/game-runtime/`。请求失败立即尝试备用源；游戏目录和游戏清单格式校验失败也触发备用。单个游戏更新失败时可继续运行已有兼容版本。

## 文件生成与部署

先按 [构建说明](game-module.md) 运行两个打包脚本，输出默认为 `work/game-runtime-release/`。

| 生成文件 | 部署位置 |
| --- | --- |
| `100/<abi>/game-<abi下划线>.apk` | GitHub Release `game-runtime-v2.0.0` 附件 |
| `games/kart/1/game_kart.apk` | GitHub Release `game-kart-v1.0.0` 附件 |
| `server/update-<abi>.json` | `$GENTV_STATE_DIR/plugins/game/update-<abi>.json` |
| `server/games/kart/update.json` | `$GENTV_STATE_DIR/plugins/games/kart/update.json` |
| `latest/<abi>/manifest.json` | 上表中的基础插件备用位置 |
| `games/kart/latest/manifest.json` | 上表中的单款游戏备用位置 |
| 仓库 `server/wallpaper/game-catalog.json` | `$GENTV_STATE_DIR/plugins/games/catalog.json` 与备用 `game-runtime/catalog.json` |

上传组件前请先确认签名、摘要和大小与生成清单一致。先上传附件并验证公开可下载，再原子替换接口 JSON。不要将主包版本填为游戏版本，也不要在相同版本号下覆盖不同内容。

后端同步更新 `app.py`、`game-catalog.json` 和 `deploy/nginx-wallpaper.locations.conf` 中的 `/tv/plugins/game/update`、`/tv/plugins/games/` 路由，执行 `nginx -t` 后 reload 并重启 API 服务。目录尚未配置时读取随服务部署的真实目录；单款游戏缺清单返回 503，不返回假的成功数据。请求只允许 GET / HEAD，带 ETag 和 no-cache。

也可由自己的 HTTPS 服务托管 APK：放到 `$GENTV_STATE_DIR/releases/`，分别使用构建 API_BASE_URL 下的 `releases/game-<abi下划线>.apk`、`releases/game_kart.apk`，清单摘要不变。不要填 HTTP 或任意第三方下载域名。

## 清单协议

基础清单由 `package_game_runtime.py` 生成，`hostApiVersion=2`，主包兼容范围默认为 `minHostVersionCode=40`，发布前按实际验证调整。独立游戏清单由 `package_game_content.py` 生成：

```json
{
  "id": "kart", "versionCode": 1, "versionName": "1.0.0",
  "baseApiVersion": 1, "minBaseVersionCode": 100, "maxBaseVersionCode": 2147483647,
  "packageName": "com.gentv.game.kart.plugin",
  "entryClass": "com.gentv.game.kart.KartGamePlugin",
  "signingCertificateSha256": "由打包工具填写实际证书摘要",
  "plugin": {
    "name": "game_kart.apk",
    "url": "https://github.com/Elaine-Lynn/GenTV/releases/download/game-kart-v1.0.0/game_kart.apk",
    "sha256": "由打包工具填写实际文件摘要", "sizeBytes": 12345
  }
}
```

此段仅为字段说明，不能直接部署。使用构建生成的 JSON，避免签名、大小填错。新增游戏时增加独立 Gradle 模块、目录条目、组件附件及独立清单，基础组件和主包无需因目录新增而升级。

## 验收清单

确认从无组件开始下载基础插件，再选择赛车下载独立游戏；`pm list packages` 中不应新增插件应用。验证手机遥控、游戏退出返回游戏中心、再次进入不重复下载、离线保留兼容缓存。保持主包和基础组件不变，发布下一版赛车，确认只下载赛车插件。错误摘要、签名、不兼容接口以及降级不得破坏原版本。ARMv7 与 ARM64 构建和设备验证应分别记录。
