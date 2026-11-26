# 手机端匿名统计接口 v2

手机 3.7.3/code19 与电视 3.7.4/code26 使用同一套 schema 2 协议，应用身份和安装 ID 独立。源码不配置 API 时不采集；手机 Debug 不发送。新版默认开启统计，设置中可随时关闭，升级保留用户明确关闭的选择。设置中可将之后的上报标为测试，模拟器自动归类；实体测试设备应先标记测试，再开启统计。

`POST <configured API base>/analytics`；正式地址为 `https://api.gentv.app/tv/analytics`。请求包含 schema_version=2、app=gentv_mobile、app_version、android_sdk、install_id、session_id、traffic，以及最多 50 条 events。事件字段为 event_id、occurred_at、sequence、name、value、foreground_seconds。详见[服务端协议](../../server/wallpaper/README.md#schema-2新增活跃留存与路径)。旧 `/tv/events` 继续兼容旧客户端，不与新版安装指标混合。

安装 ID 是统计单独生成的应用私有随机 UUID，不复用反馈 ID 或硬件 ID。升级和重启保留，关闭统计、清除数据或重装后重置；服务端只存其哈希。首次收到上报为“新增安装实例”，不代表物理设备数或实际安装时间。离开 30 分钟后开始新会话，短暂后台及 Activity 重建保留当前会话。前台每分钟记录时长增量，进入后台补记余量，不计算后台时间。

页面只允许 Step 枚举及 settings、desktop，功能事件包含连接结果、清理完成、文件发送结果、更新检查和反馈提交结果。不发送输入内容、型号、应用名称或列表、电视地址、文件名、日志及反馈正文。事件记录序号可连接跨上传批次的路径；报表仅展示相邻固定步骤的汇总。

待发队列只存在内存，最多 100 条、每批 50 条、24 小时失效，产生事件后延迟约 2 秒合批；失败由后续事件或前台心跳触发重试，事件 ID 不变。关闭后清空队列、停止心跳并换安装 ID 和会话 ID。已在途请求无法撤回。无后台采集服务，强制结束或网络失败可能少计。

事件和会话明细保留 30 天，按安装的日活明细保留 400 天，首末观测信息在连续 400 天不活跃后删除。备份最多保留 14 份。完整披露见[隐私说明](../../PRIVACY.zh-CN.md)。

验证：AnalyticsTest 覆盖授权、队列、重试去重、稳定安装 ID、事件顺序、前后台时长、短后台保留会话、长后台换会话和调度。SettingsScreenTest 覆盖开关；MetricsViewModel 使会话在 Activity 重建时保留。Release 运行与接口验收见本次[发布检查](../../docs/analytics-release-2026-11-03.md)。

## Schema 3：MAC 优先去重

新版需重新主动开启，默认关闭。请求版本为 3，增加 mac_address 字符串；只尝试系统公开接口读取本机 wlan0、eth0 的出厂 MAC，不读取所连电视或路由器地址，不使用 ADB、root 或 shell 绕过限制。过滤空值、格式错误、全零、组播及本地管理/随机 MAC（含 02:00:00:00:00:00），不可用时回退安装 ID。服务端仅保存按应用隔离的 MAC HMAC 及安装 ID 哈希映射，不保存原始 MAC。对同一安装后续观测到有效 MAC，会关联其保留历史；跨重装且无 MAC 的旧记录无法补推。不同网络接口/系统限制仍可能影响去重，结果不保证等于物理设备台数。原有 schema 2 客户端继续兼容。
