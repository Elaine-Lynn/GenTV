# GenTV 本地能力映射

下列路径均相对用户的 GenTV 仓库。优先读实际文件，不以技能内的说明替代当前实现。正常操作不需要外部项目的脚本、环境变量、venv 或缓存。

| 能力 | 本地依据 |
|---|---|
| 安装、JDK/SDK、签名与下载校验 | `docs/getting-started.md`、`docs/release.md` |
| 开启 ADB、品牌限制 | `docs/devices/adb-by-brand.md` |
| 机型参考、解析、匹配 | `phone/framework/data/devices/`、core `lists/ListCatalog.kt`、`ListParser.kt`、`ModelMatcher.kt` |
| 连接、认证、传输 | `phone/framework/adb/src/main/kotlin/com/gentv/adb/TvConnector.kt`、`KadbTvShell.kt` |
| 设备快照、备份 | core `ops/Backup.kt` 中 `TvReader` |
| 动态/静态保护、清理 | core `lists/Protected.kt`、`ops/Debloater.kt` |
| 命令、日志、恢复 | core `ops/Commands.kt`、`ActionLog.kt`、`Restorer.kt` |
| 桌面安装、交接、回滚 | core `ops/LauncherHandover.kt`、`SetupWraithGuard.kt` |
| 主题、程序坞、信号源 | core `ops/LauncherLook.kt`、`lists/SourceProfiles.kt`、`docs/desktop-protocol.md` |
| DNS 过滤与撤销 | core `ops/PrivateDns.kt` |
| 遥控器、截图 | core `ops/Remote.kt`、`phone/app/src/main/java/com/gentv/mobile/data/TvScreenCapture.kt` |
| 内存、存储、缓存清理 | core `ops/TvFacts.kt`、`Finisher.kt` |
| 固件更新后复查 | core `ops/TvCheck.kt` |

表中 core 是 `phone/framework/core/src/main/kotlin/com/gentv/core/`。

## 电脑端执行入口

`tools/tv_care.py` 发现本机 SDK/JDK，运行 `phone/tv-care` 的 JVM 适配器，直接依赖 `:core`，复用匹配、清理、日志、DNS、缓存和 Home 交接逻辑。完整命令与条件读仓库 `docs/tv-care.md`。

- `inspect`、`match`、`backup`：设备事实、实际匹配和私有备份。
- `clean`：默认计划；`--apply` 调用本地 Debloater，仅禁用本次审核包。
- `install`：校验 APK hash 和包名，安装启动；不卸载冲突包。
- `home --apk ... --sha256 ...`：完整安装默认走分阶段接管，不需额外请求兼容接管授权。先保留原桌面尝试，再处理已识别的 Home 阻碍；CVTE 在安装后重启核验，再以 `--boot-confirmed` 续办。Wi-Fi 归属不清先修复，已核实的实际有线默认网络可继续。
- `restore`：复用 Restorer，隔离本会话撤销项，保留 GenTV，恢复启用/default 状态及已保存配置。主题/布局冲突不会阻止独立的包、DNS 和 Home 恢复。
- `dns`、`caches`、`check`：本地 DNS、缓存清理、固件更新后只读复查。
- `desktop-export/save`：本地 schema2 桌面协议，有 revision 冲突保护。
- `look-export/save`：配置型主题、开机及按键设置；保留壁纸并留恢复基线，辅助服务须单独选择和授权。
- `screenshot`、`key`、`verify-home`：PNG、按键与 Android 前台核对；不能代替实体遥控器验收。
- `ports`：只有明确 Connection refused 算 closed，不把超时/不可达算关闭。

`tools/tv_relay.py` 是仅监听本机的中继。私有 VPN 必须由用户提供明确目标与现有授权，不能开公网 ADB 或自动扫描。

机型匹配默认读本地 252 份原有清单和 `references/device-supplements.json` 的 108 份补充记录。通过相同 ListParser 校验，拒绝记录不参与匹配；本地原有记录同 ID 优先。资料来源与 MIT 许可保留在仓库 `licenses/tv-care-device-references-LICENSE.txt`。

手机端还支持卸载路径，但电脑适配器只禁用，恢复时保留 GenTV；不能用手机卸载分支代替此适配器。辅助服务权限不自动授予，实机画面、实体按键和重启仍由用户验收。

新增能力：`tv_transport.py` 独立 TCP 客户端；`ChunkTransfer.kt` 哈希核对续传；`package_tv_care.py` 自带 Java 运行时；`PrivacyAudit.kt` 只读证据报告；`Performance.kt` 实机采样与同设备比较；`LegacyShell.kt` / `DeviceIdentity.kt` 老系统查询及身份绑定；`Configuration.kt` 常用配置参数。以 `docs/tv-care.md` 的实际依赖和限制为准。
