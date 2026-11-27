# GenTV 壁纸接口

官方接口入口：https://api.gentv.app/tv/wallpaper。独立 Gunicorn 服务监听127.0.0.1:18765，Nginx通过专用 locations 配置暴露壁纸、更新与受限写入接口。

```json
{"wallpapers":[{"id":"content-version","url":"https://api.gentv.app/tv/wallpapers/coast.jpg?v=content-version"}],"intervalSeconds":120}
```

- `GET /tv/wallpaper`：按文件名顺序返回最多100张图片；目录为空时返回空数组。
- `GET /tv/wallpapers/<filename>`：只读图片。支持jpg/jpeg/png/webp；列表忽略隐藏文件、软链接、空文件和超过8 MiB的文件。
- 支持HEAD、ETag；不提供上传或管理写接口。
- 生产图片目录：`/srv/gentv/wallpaper/media`。运行时图片不提交代码仓库。
- 建议1920×1080横图。添加、删除、覆盖图片后，下次客户端清单刷新自动生效（3.3起最多每6小时检查一次）；版本 URL 使用图片内容的 SHA-256；仅修改文件时间或重新部署相同内容不会让客户端重新下载。
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

客户端按完整版本 URL 持久缓存图片，磁盘图片上限 64 MiB、最多 100 张。普通轮播只读取完整本地缓存；清单移除图片或返回空列表不会立即删除仍可用的图片。

- 清单成功检查（含空列表、304）后最多每 6 小时检查一次，持久保存 ETag 并发送 If-None-Match。失败按 1、2、4、6 小时退避；时间回拨不触发密集请求。
- 每个 24 小时预算窗口最多读取 16 MiB 图片响应体，失败和中断也计入。每次请求前持久预留 8 MiB，结束后退还未读取部分；进程异常结束保守保留预留额度，重启不重置预算。HTTP/TLS 头部与清单流量不计入图片预算。
- 每个轮播周期最多尝试一张新图。缓存满后，每 24 小时最多一次补入新图，按需淘汰最久未访问的图片，并保护当前显示图片。因最大图片预留或预算不足，可能暂时不再补入较小图片。
- 对仍在清单中的已成功下载版本保留记录，被淘汰后不再循环下载；URL 版本变化才重新作为新图。坏图和下载失败按 1、2、4、8、16、24 小时退避，并跨重启保留。
- 调试夹具使用独立 wallpapers-test 目录与更短清单刷新周期，正式包不包含此入口。

以上客户端规则需要新版 TV APK。内容哈希版本需要同时部署本目录的服务端代码；首次从旧的时间戳版本迁移到内容哈希版本会发生一次 URL 变化，下载仍受预算限制。

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

## 使用统计报表

`/tv/report/` 为独立、带 HTTP Basic 登录的内部报表；`/tv/report/export.csv` 导出所选日期的每日汇总。默认起始日期为 2026-10-28，支持最多 366 天范围。日期按 UTC；当日为未结束的部分数据。无上报填 0，数据库缺失单独标注，损坏或读取失败返回 503。

报表展示电视会话、前台时长、手机启动、连接成功、清理完成、版本与事件分布。只用 SQLite `mode=ro` 和 `query_only` 读取 `stats.sqlite3.daily`、`mobile.sqlite3.totals`；不读反馈、原始事件或设备资料。现有数据没有安装 ID，不能计算安装台数、去重用户、新增设备，也不能区分测试流量。此报表不改变客户端采集行为或统计授权。

独立 Gunicorn 服务 `gentv-report` 仅监听 `127.0.0.1:18766`；Nginx 在 API HTTPS vhost 引入 `deploy/nginx-report.locations.conf`。页面与 CSV 均在应用侧登录校验，无配置时拒绝所有访问；响应禁缓存、禁止嵌入、禁止搜索收录，不使用外部脚本或图表 CDN。用户名及强随机密码的 SHA-256 在 `/etc/gentv-report.env`（600），不提交仓库。密码本身只保存在操作者指定的本地凭据文件（600），勿放到公开网站或分享链接内。

```sh
python3 tools/deploy_stats_report.py --host admin@YOUR_SERVER \
  --credentials-file work/stats-report/access.json
```

支持重复 `--ssh-option`，复用已有 SSH 验证，不修改 API 服务、发布清单或数据库。部署创建 `/srv/gentv/report/releases/<UTC>`、独立 venv 和 current 链接，配置变更前备份到 `/var/backups/gentv-report/<UTC>`（700）；Nginx 校验或本机服务启动检查失败时自动还原配置和原服务状态。重复部署保留本地凭据文件以复用密码。

人工回退：使用备份 `previous.json` 的 previous 恢复 current 链接，将快照中的配置放回原路径；`systemctl daemon-reload` 后按 previous 中的 active/enabled 恢复报表服务，`nginx -t` 成功后 reload。首次部署可移除 vhost 的报表 include 并停用该独立服务，其他 API 保持运行。


## Schema 2：新增、活跃、留存与路径

`POST /tv/analytics` 使用严格的 schema 2 字段：`schema_version=2`、`app`（gentv_tv/gentv_mobile）、`app_version`、`android_sdk`、`install_id`（应用私有随机 UUID）、`session_id`、`traffic`（production/test）和最多 50 条 `events`。每条事件包含 event_id、occurred_at（毫秒）、sequence、name、value、foreground_seconds。请求最大 64 KiB，事件限过去 48 小时至未来 5 分钟，固定页面/操作白名单，禁止自由文本或硬件标识。累计事件 ID 与会话顺序去重；前台增量仅允许 heartbeat/session_duration 的 0–120 秒，并按 UTC 零点拆分。无后台采集服务。

新数据存入独立 `analytics.sqlite3`，旧 `stats.sqlite3` 与 `mobile.sqlite3` 保持原口径，不混合安装指标。install_id 在服务端以 app 命名空间 SHA-256 存储；电视和手机不关联身份。测试/模拟器与正式流量分别聚合，报表默认 production。明细路径与会话保留 30 天，按安装实例每日活动保留 400 天，安装首末观测信息在连续 400 天不活跃后删除。每日 retention 定时器清理；数据库自动纳入已有私有备份与健康检查。

口径：新增为首次观测，非实际安装日；DAU/WAU/MAU 为结束日 / 滚动 7 / 滚动 30 天去重安装实例；区间活跃在整个所选区间去重。平均每日活跃时长为区间总秒数除以每天 DAU 之和。会话在首次进入前台或离开超过 30 分钟后开始；后台、电视屏保不计时。每分钟心跳用于持续前台时长和跨日活跃；离开时补计余量。事件失败在进程内短暂重试，队列最多 100 条、24 小时失效，进程终止可能丢失。

D1/D7/D30 为新增队列在准确第 N 天再次活跃的比例，只对已结束的观察日给出结果；未来/进行中日期显示“未到观察日”。路径为同一应用、流量、安装、会话内按 sequence 排序的相邻固定页面/操作，跨上传批次连接，仅展示最近 30 天与所选范围交集内的 Top 30 转移。新协议没有数据时显示等待，不从旧数据推算。更换版本不改变安装 ID；关闭统计、清除数据或重装会产生新的实例。首次观测与跨流量切换均受采样覆盖影响。

新客户端需重新明确授权，不能继承旧版 `statistics`/`enabled` 授权：电视控制中心提供独立“隐私与使用统计”，不恢复软件更新页已移除的统计区域；手机设置更新说明及测试标记开关。模拟器自动分类，手机 Debug 保持不发送。正式实体测试机需在新统计开启前标记测试。电视 3.7.3/code25 与手机 3.7.2/code18 已发布；用户升级并重新开启统计后才会收到这些新指标，旧版历史不能补算。

## Schema 3：可选 MAC 辅助身份去重

兼容 schema 2；schema 3 仅额外要求 mac_address 字符串。空值、不可读取、错误格式、全零、组播和本地管理 MAC 回退安装 ID。有效 MAC 用 analytics.sqlite3 内随机且持久化的服务器密钥计算 app 命名空间 HMAC；数据库不存原始 MAC。device_aliases 关联安装哈希和设备 HMAC，报表在只读快照内统一解析身份，新增队列取最早观测日，活跃去重而时长、事件、会话仍累加。相同 MAC 的不同应用与测试分组保持独立。映射随安装记录在 400 天不活跃后删除，密钥不随部署更换。备份需保留整个数据库；不要单独重置密钥。缺少映射表的旧数据库仍可只读查询。

两端升级后默认关闭新版统计，用户阅读新说明并重新开启才会发送 MAC。原始 MAC 不进入日志、报表或 CSV。旧版从未上传 MAC 的重装实例无法追溯关联；接口切换也可能影响 MAC，不能宣称精确物理设备数。


### Verified analytics traffic

Anonymous production claims are retained in the unverified view. The first ingest
after upgrading migrates older client-declared production counts to that same
view in one transaction. Test traffic and historical event counts are retained.
The report also applies this distinction before the first ingest on an old database.

A trusted server can sign a production batch with HMAC-SHA256 over its exact
request body and send the lowercase hex digest in `X-GenTV-Analytics-Signature`.
Configure a private, service-owned 0600 key file using
`GENTV_ANALYTICS_GENTV_AGENT_KEY_FILE`, `GENTV_ANALYTICS_GENTV_TV_KEY_FILE` or
`GENTV_ANALYTICS_GENTV_MOBILE_KEY_FILE`. Use different random keys for each app
and keep them on trusted servers. The Agent account platform is the supported
Agent forwarder. Existing Launcher and mobile clients continue to submit to
the unverified or test view while a trusted forwarder is unavailable.

The TV wallpaper picker reads `GET /tv/wallpaper?view=catalog`. It returns
ordered `categories` with localized titles and `items` of kind `image`,
`video` or `shuffle`; it never lists "coming soon" placeholders. Media paths
are relative to the configured API base. A still wallpaper's `preview` is its
pre-rendered `<name>-preview.jpg` (640x360) when that file is in the media
folder, otherwise the wallpaper itself; previews are served but never listed as
wallpapers. Render them with `python3 tools/make_wallpaper_previews.py
<media folder>` and upload them with the wallpapers. The unqualified endpoint
still serves the legacy image rotation manifest. The deployment archive
includes the default image and its preview, and the kitten loop and thumbnail. Deploy the catalog API before distributing clients that
use it.
