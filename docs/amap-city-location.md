# 高德自动城市配置

没有保存城市时，首页请求构建配置 `API_BASE_URL` 下的 `location` 接口；后端根据请求的公网 IPv4 调用高德 IP 定位，返回省市。电视使用内置城市数据中的坐标请求现有 Open-Meteo 天气接口。没有新增高德 Android SDK，APK 中不包含高德 Key。

成功后保存城市，不重复定位；升级保留已有城市。手动城市选择页面及工具箱入口已移除，内置坐标表只保留省、市，删除区县数据。识别失败时显示天气暂不可用，沿用首页的定位重试策略；天气仍按原有三十分钟周期刷新。没有配置 GenTV API 时不发起自动定位。

## 后端配置

1. 在高德控制台申请 **Web 服务** 类型的 Key，启用基础 IP 定位服务。当前实现使用普通 Key 认证，不使用高德 Android SDK Key。若配置服务端 IP 白名单，应填写后端公网出口 IP。
2. 更新服务目录中的 `app.py`、`location.py`、`rate_limit.py`。没有新增 Python 三方依赖。
3. 在现有 `/etc/gentv-wallpaper.env` 中添加如下配置，替换示例值；限制该文件读取权限，不提交到 Git：

   ```properties
   AMAP_WEB_SERVICE_KEY=填写高德Web服务Key
   ```

4. 将 `server/wallpaper/deploy/nginx-wallpaper.locations.conf` 中新增的 `/tv/location` location 合并到现有 HTTPS 虚拟主机。运行 `nginx -t` 成功后再 reload Nginx，并重启 `gentv-wallpaper` 服务。不要覆盖现有其他站点配置。
5. 客户端沿用 `-PgentvApiBaseUrl` 或 `GENTV_API_BASE_URL` 指定 API 根地址，必须是以 `/` 结尾的 HTTPS 地址；正式构建也可沿用本地私有签名配置中的 `apiBaseUrl`。

## 接口

`GET /tv/location`，无客户端 IP 查询参数。后端只采用连接来源地址；仅允许本机反向代理通过 `X-GenTV-Peer` 提供原始来源，Nginx 必须覆盖该请求头。若前方有 CDN 或负载均衡，先为 Nginx 配置经过验证的可信代理与真实来源地址，避免把 CDN 节点地址当作用户位置。

成功示例：

```json
{"province":"上海市","city":"上海市","adcode":"310000","source":"amap-ip"}
```

| HTTP 状态 | 含义 |
| --- | --- |
| 200 | 已识别城市 |
| 422 | 来源不是可定位的公网 IPv4，含 IPv6-only 情况 |
| 429 | 每个来源每分钟最多 12 次请求 |
| 503 | 尚未配置 Key、上游失败、超配额或无法识别城市 |

高德基础 IP 定位仅支持国内 IPv4，不保证区县准确度。未知城市不会猜测匹配，客户端不会显示无法定位的城市天气。缓存仅保留在服务进程内，按临时加盐哈希索引最多缓存 4096 个来源、每项六小时，不保存原始 IP。服务重启清空缓存；接口无访问日志，错误响应不包含 Key、来源 IP 或上游 URL。仍需在高德控制台设置适当配额和用量告警。

## 验收

从国内公网访问配置的 HTTPS API 根地址下的 `location`，检查 200 响应和正确城市。直接从服务器本机回环请求且没有可信代理来源时返回 422 是预期行为。首次进入未保存城市的首页应显示“正在识别城市…”，随后显示天气；已有城市重新进入不能被自动结果覆盖。移除 Key、断网和不支持的 IP 应显示不可用并按策略重试，不应出现手动选城入口。

没有实际 Key 时可以完成本地模拟接口测试、客户端城市匹配测试及构建；无法完成真实高德响应和正式设备上的端到端验收。部署完成后仍需实测旧 Android 设备的 HTTPS 证书兼容性。

高德文档：https://developer.amap.com/api/webservice/guide/api/ipconfig
