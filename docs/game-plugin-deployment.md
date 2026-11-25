# 游戏插件独立更新部署

主包和插件现在分别管理版本。当前主包为 3.7.15（37），第一版独立插件为 1.0.0（1）。后续只提高插件的版本，即可让相同主包下载新版插件，无需系统安装。

## 给后端的文件

从本次构建的 `work/game-runtime-release/` 目录取：

| 文件 | 用途 |
| --- | --- |
| `1/armeabi-v7a/game-armeabi_v7a.apk` | ARMv7 组件 |
| `1/arm64-v8a/game-arm64_v8a.apk` | ARM64 组件 |
| `server/update-armeabi-v7a.json` | ARMv7 主接口清单 |
| `server/update-arm64-v8a.json` | ARM64 主接口清单 |
| `latest/<abi>/manifest.json` | GitHub 备用接口清单 |

APK 只是代码和资源容器，不要让用户安装。两个 ABI 的文件大小分别约为 75.4 MB 和 78.9 MB；设备只下载匹配的一个文件。清单由 `tools/package_game_runtime.py` 生成，已经包含实际大小、SHA-256 和签名证书摘要，不要手工猜测这些值。

## 接口部署

1. 更新现有服务的 `server/wallpaper/app.py`，保留服务配置的 `GENTV_STATE_DIR`、`PUBLIC_BASE_URL` 和其他环境变量。
2. 将两个 `server/update-<abi>.json` 放到 `$GENTV_STATE_DIR/plugins/game/`。默认状态目录为 `/var/lib/gentv-tv`。使用临时文件写完后 rename 替换清单，避免请求读到不完整文件。
3. 将 `server/wallpaper/deploy/nginx-wallpaper.locations.conf` 中 `/tv/plugins/game/update` 的 location 添加到现有 HTTPS 虚拟主机。执行 `nginx -t` 成功后再 reload Nginx，并重启现有 wallpaper API 服务。
4. 按以下规则发布组件文件，然后发布指向它们的清单。

主接口：`GET /tv/plugins/game/update?abi=armeabi-v7a` 或 `abi=arm64-v8a`，JSON 和主应用更新一样是只读接口。缺失清单返回 503，不支持的 ABI 返回 400。

默认清单指向本仓库 `game-runtime-v1.0.0` GitHub Release 的附件。上传两个 APK，附件名必须与清单一致。如果后端直接托管文件，则放在 `$GENTV_STATE_DIR/releases/`，并把 JSON 的 `plugin.url` 改为构建时 API_BASE_URL 下的 `releases/game-armeabi_v7a.apk` 或 `releases/game-arm64_v8a.apk`；其余大小和摘要不变。

GitHub 备用接口：把 `latest/<abi>/manifest.json` 放到仓库 `update-manifest` 分支的 `game-runtime/latest/<abi>/manifest.json`。主接口请求失败时客户端读取这个备用地址。不要再发布到按主包版本分目录的旧 `game-runtime/37/` 地址。

## 下次发布插件

使用 `-PgameVersionCode=2 -PgameVersionName=1.0.1` 构建 `:game:assembleRelease`，再次运行打包脚本生成清单。插件必须使用与主包相同的正式签名。兼容关系由 `hostApiVersion`、`minHostVersionCode`、`maxHostVersionCode` 决定；不再要求插件版本与主包一致。协议不变且确认兼容时主包无需升级。

先上传新附件并验证可下载，再替换主接口和备用接口清单。同一版本的附件保持不变，修复内容时提高插件版本号。客户端每次进入游戏比较版本；更新失败则继续使用已有的兼容组件。

## 验收

分别检查两个 ABI 的接口返回 200、正确 JSON 和 `Cache-Control: no-cache`。从清单下载文件后核对 SHA-256。保持同一主包，先加载插件 1，再发布插件 2，确认再次进入游戏下载插件 2；断网、错误哈希及不兼容协议不能替换已保存的插件。

本次已完成本地接口测试、版本兼容测试及构建检查。服务器部署、公开下载以及真机从版本 1 更新到版本 2 的完整链路，需部署后验收。旧设备上已经安装的上一版主包没有这个独立版本协议，需先升级一次主包。
