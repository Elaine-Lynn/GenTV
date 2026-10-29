# GenTV 壁纸接口

官方接口入口：https://api.gentv.app/tv/wallpaper。独立 Gunicorn 服务监听127.0.0.1:18765，Nginx通过专用 locations 配置暴露壁纸、更新与受限写入接口。

```json
{"wallpapers":[{"id":"content-version","url":"https://api.gentv.app/tv/wallpapers/coast.jpg?v=content-version"}],"intervalSeconds":120}
```

- `GET /tv/wallpaper`：按文件名顺序返回最多100张图片；目录为空时返回空数组。
- `GET /tv/wallpapers/<filename>`：只读图片。支持jpg/jpeg/png/webp；列表忽略隐藏文件、软链接、空文件和超过8 MiB的文件。
- 支持HEAD、ETag；不提供上传或管理写接口。
- 生产图片目录：`/srv/gentv/wallpaper/media`。运行时图片不提交代码仓库。
- 建议1920×1080横图。添加、删除、覆盖图片后，下次客户端清单刷新自动生效（3.3起最多每6小时检查一次）；修改时间与大小生成版本URL，避免旧图缓存。
- 生产Nginx直接服务该专用目录，目录必须只放可公开图片；其余源码保存在Web根目录外。

## 本地运行与测试

```sh
python3 -m unittest discover -s server/wallpaper -v
mkdir -p work/wallpaper-state
GENTV_STATE_DIR="$PWD/work/wallpaper-state" WALLPAPER_DIR=/path/to/images PUBLIC_BASE_URL=http://127.0.0.1:18765/tv python3 server/wallpaper/app.py
```

命令行WSGI服务用于本地联调；生产使用requirements.txt锁定的Gunicorn。本地写入状态放在忽略目录 work/，避免普通用户无法写入默认 /var/lib/gentv-tv。HTTP 回环地址仅用于服务端本地测试，不能直接作为正式客户端要求的 HTTPS API 基址。

## 部署与回滚

服务器先安装python3-venv。源码包包含app.py、requirements.txt、deploy/中的service和Nginx locations；可选seed/coast.jpg仅在目标目录尚无同名图片时安装。上传到磁盘目录/var/tmp，计算SHA256后执行：

```sh
sh install-on-server.sh /var/tmp/wallpaper-source.tar.gz <sha256>
```

部署脚本创建版本目录`/srv/gentv/wallpaper/releases/<UTC timestamp>`与current软链接。服务配置位于/etc/gentv-wallpaper.env、/etc/systemd/system/gentv-wallpaper.service；Nginx新增snippet，并仅在已配置证书的 api.gentv.app HTTPS host中引入。修改前备份vhost，nginx -t失败立即恢复，不reload错误配置。

需要撤销服务时，只停止gentv-wallpaper，不停止Nginx或网站。每次部署的previous-current.txt记录上一个源码目录；切回该软链接并重启服务即可回滚代码，图片目录保持独立。

更新图片应先上传到/var/tmp，再安装到媒体目录的临时文件，最后原子改名；勿把私钥、源码或设备资料放入公开媒体目录。

3.3客户端按完整版本URL持久缓存图片，磁盘上限64 MiB。已下载图片轮播与重启不再下载；清单失败时使用已保存列表离线轮播。调试夹具使用独立wallpapers-test目录与更短刷新周期，正式包不包含此入口。

## 3.4 统计与更新

- `GET /tv/update?platform=tv` 读取 `/var/lib/gentv-tv/update.json`，字段为 `versionCode`、`versionName`、`packageName`、`url`、`sha256`。手机使用 `platform=mobile`（或无参数），对应独立的 `update-mobile.json`；缺少清单返回503，避免把电视包推给手机。
- APK放在 `/var/lib/gentv-tv/releases/`，通过 `/tv/releases/` 只读访问。客户端只接受同API前缀HTTPS地址、最大8 MiB、匹配SHA256、同包名、同签名且版本严格递增的APK；用户确认下载，再由Android确认安装。
- `POST /tv/stats` 接收 `{ "event":"session", "versionCode":13, "foregroundSeconds":42 }`；仅按UTC日期和版本累加会话数与秒数，SQLite位于 `/var/lib/gentv-tv/stats.sqlite3`。不保存请求原文、设备ID、安装ID或应用列表；该路由关闭Nginx访问日志。不提供公开统计读取接口。
- 统计默认关闭，可在控制中心“更新与使用统计”开启。仅在前台会话结束时发送，失败丢弃；不是唯一用户数或新增设备数。桌面进入前台时每天最多检查一次更新；无后台服务。
- `api.gentv.app.conf` 要求先配置域名DNS和TLS证书。`StateDirectory=gentv-tv` 由systemd创建可写数据目录；代码和媒体目录仍保持独立。

## 强制更新策略（电视）

电视清单额外支持 `forceUpdate`（布尔值）和 `minSupportedVersionCode`（非负整数，不能超过最新versionCode）。仅在最新版本比本机新时生效：forceUpdate=true 强制所有旧版本升级；本机versionCode低于minimum时强制对应旧版本。命中后隐藏“稍后”、禁用弹窗返回和外部关闭，并缓存要求，返回桌面会重新提示；系统Home、系统设置和Android安装确认仍由系统控制。无效策略被拒绝；升级成功的新版本不再阻挡。两项默认均为关闭（false/0）。

## 手机端服务

`POST /tv/events`：64KiB/50条，严格字段与枚举白名单，事件时间限过去48小时至未来5分钟；event_id为UUID，整批校验后单事务写入、去重和按天聚合。session_id仅验证不保存。每个服务进程上限120请求/分钟。原始事件在mobile.sqlite3保存30天；汇总独立保存。

`POST /tv/feedback`：2MiB请求、1MiB解码截图，仅接受JPEG/PNG；兼容手机现有字段和bug/praise/feature/question枚举。写入成功才返回201、success:true；每个服务进程上限12请求/分钟。反馈保存在feedback.sqlite3，保留30天。

两个写接口都不公开数据查询，关闭Nginx访问日志。`gentv-retention.timer` 每日运行清理，避免长期无新请求时旧明细滞留。速率是单worker的全局上限，并非独立用户配额；扩大部署需共享限速存储。

## 3.7 发布运维

`tools/deploy_tv_api.py` 使用版本化源码包部署。首次切换前保存环境、全部 service/timer、Nginx snippet 与 vhost；失败恢复配置、current 和定时器启用/运行状态。部署锁避免并发切换。隔离 Linux 故障注入测试：`python3 -m unittest discover -s server/wallpaper -v`，包含 nginx 失败及切换后健康失败回滚；macOS 跳过 GNU 工具专属测试。

`gentv-backup.timer` 每日执行 `backup.py`：通过 SQLite backup API 捕获在线数据库，复制到临时库执行 integrity_check 后才将快照提交，`/var/backups/gentv-tv` 权限 700，保留最近 14 次快照。快照含反馈私密内容，禁止公开。`gentv-health.timer` 每五分钟检查 API、SQLite quick_check 与 48 小时内有效备份；异常反映在 systemd/journal 和私有 `/var/lib/gentv-tv/health.json`。没有配置邮件或即时消息通知，应在有运维通知目的地后另接外部告警。

写入端点有 Nginx 入口限流与每来源内存配额；Cloudflare 可信代理网段仅从官方 API 获取并验证 CIDR，Nginx 覆盖客户端传入的来源头。来源仅以随机密钥哈希短暂存在内存，不存入统计表。共享网络仍共享配额。

正式 TV 更新清单增加 `signingCertificateSha256`、`minSdk`、`sizeBytes`。旧 debug TV 客户端使用独立 legacy 清单，参见 `docs/release.md`。强制策略默认关闭，客户端只有通过完整清单验证后才接受，24 小时后需重新验证策略。
