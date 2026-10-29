# 手机端匿名统计接口 v1

状态：客户端与仓库内服务端均已实现。历史实机事件接收记录见 [发布验收记录](release-acceptance.md)，不代表当前线上服务已重新验证。源码构建默认不配置 API 基址，因此不发送统计请求。仅用于 GenTV Remote 手机 App，不接入电视 Launcher。设置 → 匿名使用统计，默认关闭，用户主动打开才发送。debug 构建不自动采集产品事件。

## 接口

官方发布接口：`POST https://api.gentv.app/tv/events`。源码构建通过 `-PgentvApiBaseUrl` 或 `GENTV_API_BASE_URL` 配置 API 基址。

请求头：`Content-Type: application/json; charset=utf-8`。不带账号、广告 ID、设备 ID 或鉴权密钥，不跟随重定向。服务端应为公开的受限写入接口，执行请求体大小、字段白名单、事件枚举、字段长度、速率和时间范围校验；不得在 APK 放置服务端密钥。

```json
{
  "schema_version": 1,
  "app": "gentv_mobile",
  "app_version": "0.9.0",
  "android_sdk": 35,
  "session_id": "随机 UUID，每个客户端实例及每次重新开启统计时更换",
  "events": [
    {"event_id": "随机 UUID", "occurred_at": 1793145600000, "name": "connection_result", "value": "connected"}
  ]
}
```

`occurred_at` 为客户端 UTC Unix 毫秒，服务端另外保存接收时间，不信任客户端时钟。客户端只将 HTTP 200、202、204 作为整批接收成功；响应体可为空。其他状态、网络失败与重定向均视为失败。服务端必须整批原子接收，按 `event_id` 幂等去重后再聚合，重复事件仍返回成功；不能返回部分成功。建议请求体上限 64 KiB、每批最多 50 条，未知字段及枚举返回 400。

| name | value | 含义 |
| --- | --- | --- |
| app_open | 空字符串 | 新 Activity 会话启动，不等于 DAU |
| screen_view | Step 小写枚举、settings、desktop | 页面进入，重复状态刷新不触发；Activity 重建可能重新计数 |
| connection_result | connected / denied / unreachable / failed | 观察到连接终态变化；不是严格的逐次连接尝试计数 |
| tidy_complete | 空字符串 | 清理流程完成，不表示全部操作成功 |
| file_send_result | sent / failed / no_space / cancelled | 单个文件状态进入终态，不含文件名、路径、类型或内容 |
| update_check | current / available / failed / unconfigured | 手动检查更新的状态变化 |
| feedback_submit | success / failed / unconfigured | 反馈提交结果，不包含反馈正文或附件 |

可统计页面使用次数、连接终态分布、清理完成次数、文件发送结果分布、版本分布和会话数。没有跨会话稳定 ID，因此不能计算真实独立用户、DAU、留存或跨设备漏斗；不得把页面/事件次数当成用户数。未加入遥控每次按键、应用包名或设备型号统计。

## 客户端行为与隐私

- 仅内存暂存，最多 100 条，超出丢最旧数据；每批最多 50 条，24 小时过期。
- 事件产生后延迟约 2 秒合批，只允许一批在途。没有定时轮询、后台服务或独立网络重试循环；成功后排空剩余批次，失败保留内存队列，后续事件可触发再次发送。进程退出或 Activity 销毁可能丢失数据，统计为尽力而为。
- 关闭后取消待发送任务、清空队列并换会话 ID；已经发出的网络请求无法撤回，服务器已经接收的记录不会因此自动删除。
- 只保存统计开关偏好；事件内容、会话 ID 不落盘，不复用反馈的 install-id。
- 不上报 IP、MAC、序列号、电视名称、SSID、应用列表、账号、用户文本、文件、日志、截图、包管理命令或异常详情。服务端仍可见请求源 IP，应避免写入分析表，配置访问日志脱敏及短期保留。
- 仓库内服务端在接收时按天聚合并清理超过 30 天的原始事件；另提供每日清理脚本及部署定时器。实际部署仍需启用定时清理，并核对日志保留配置。

## 验证与上线

`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline --continue --console=plain`

AnalyticsTest 验证默认关闭、关闭清队列、字段白名单、失败重试 ID 不变、成功清除、队列/批次上限、24 小时过期及会话轮换。仓库内服务端已实现字段校验、事务内去重和聚合、限流及保留清理，配有本地测试。历史验收记录包含真实手机事件与服务端确认；每次部署仍需使用发布构建或直接调用测试验证真实请求和清理配置。debug UI 开关可测试保存，但不发送真实统计流量。
