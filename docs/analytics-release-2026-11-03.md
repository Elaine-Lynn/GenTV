# 统计升级正式发布检查 · 2026-11-03

## 发布结果

电视 `com.gentv.tv`：3.7.3/code25；手机 `com.gentv.mobile`：3.7.2/code18。官网中英文首页、下载文件、release.json、升级接口及中英文隐私说明均已更新。电视更新保持非强制，旧 debug 签名兼容通道仍为 3.6.1/code21。

新统计需要用户重新主动开启；默认关闭。模拟器自动归入测试，实体测试机需先标记测试。旧版历史不能补算安装、活跃、留存。两端不上传品牌、型号或硬件标识，只使用独立的应用内随机安装 ID。

## 产物

- tv: `gentv-tv-3.7.3.apk`，844,328 bytes，SHA-256 `31ec27b1f300e4bd946f5bf4ef6ee2cdb92436b20417698029db7c4537945eef`。
- mobile: `gentv-mobile-3.7.2.apk`，25,868,959 bytes，SHA-256 `5256bef81484a41499980466700432494ea770cd97074a18af57b2791993fae8`。

两端正式证书 SHA-256：`8d5b5378f9056880f3036d44bc2f4a48322141fbb8d8bc91951d61a4652d997f`。手机内置电视包、独立电视 APK 和 TV 许可分发 ZIP 中的 APK 字节一致；TV 小于 1,000,000 bytes。私钥与签名配置未上传。

## 上线前修正

- TV 页面统计改用 `instanceof` 判断，避免正式包 R8 混淆类名后把首页、设置页误记为 other；屏保按具体类型排除。
- 手机补全中文隐私弹窗及标题。
- Linux 隔离测试复现同长度壁纸覆盖后 inode/size/mtime/ctime 全部相同的情况：移除仅依赖文件元信息的哈希缓存，始终按受大小限制的真实内容计算版本；相同图片保持原版本，不触发全量刷新。
- API 和报表显式关闭 SQLite 连接，事务提交/回滚语义保持。

## 构建与测试

- TV Release：312 项单元测试通过，lintRelease、assembleRelease 成功。
- 手机：core 277、ADB 14、tv-care 46 项全部通过；app 207 项中 206 项通过、1 项既有环境用例跳过。testDebugUnitTest、lintRelease、assembleRelease 成功。该手机项目未提供 testReleaseUnitTest 任务，未将其声称为通过。
- 服务端：macOS 34 项中 32 项通过、2 项 Linux 专属测试跳过；在接口服务器的隔离临时目录补跑 Linux 全部 34 项，通过，包括部署失败后的配置与服务回滚。测试不操作生产数据。
- tools 16 项、skill scripts 3 项通过；branding、repository、git diff --check 通过。Lint 无错误，既有告警保留。

## 正式 APK 运行验证

使用新启动的 `GenTV_371_FocusQA` / emulator-5556（Android 36、arm64），不操作原有 emulator-5554，也未操作真实电视。

1. TV 从正式 3.7.2 覆盖到 3.7.3：install 返回 Success、启动 Status: ok，升级时配置逐字一致、默认 Google TV Home 保留。
2. 手机先安装旧正式 3.7.1，保存中文语言，再覆盖正式 3.7.2；install Success、启动 Status: ok，中文语言保留。
3. 两端新统计均默认关闭，模拟器通过应用内界面明确开启。服务器收到真实 APK 发出的 schema 2 请求，只有 test 流量；每端一个稳定安装 ID、进程重启后新会话，前台时长至少 60 秒。TV 正式包正确上报 home/privacy 页面，证明 R8 后仍可识别。
4. 两端统计开关均恢复关闭。手机 APK 首次安装在 TV 模拟器上会自然新增一条应用排序/隐藏记录；排除此新包条目后所有原配置保留。默认 Home 始终未改。
5. 模拟器实际安装的 base.apk 与对应公网发布 APK SHA-256 相同。

这些是 Android 模拟器证据，不是实体手机或品牌电视上的 Home、HDMI、遥控器、待机及 ADB 连接验收。此轮没有更改输入源适配或已有实体设备。

## 公网复核

官网与 API 两个下载地址的两端 APK 均验 SHA-256 和正式签名；官网 release.json 与真实文件大小、版本、哈希一致。GenTV/3.7.1、3.7.2、3.7.3 的 TV 更新均返回 code25；旧 GenTV/3.6.1 保持 code21；手机版返回 code18。中英文首页链接、JSON-LD 和中英文隐私页通过复核；壁纸 API、登录保护报表均正常，未登录报表拒绝访问。

## 部署与恢复

官网当前目录：`/var/www/gentv/releases/20261103T081541Z-analytics-tv373-mobile372`；上一目录：`/var/www/gentv/releases/20261030T051916Z-tv372`。

发布前清单及网站指针备份：`/var/backups/gentv-tv/20261103T075942Z-analytics-release`。

API 修正目录：`/srv/gentv/wallpaper/releases/20261103T081338-54004`，配置回退快照 `/var/backups/gentv-wallpaper/20261103T081338-54004`。

报表修正目录：`/srv/gentv/report/releases/20261103T081431Z`，回退快照 `/var/backups/gentv-report/20261103T081431Z`。

发布先验证并存放不可变 APK，再构造网站版本目录，原子替换两份升级清单和网站 current 链接；异常恢复旧清单和网站指针。需要回退时，按备份原子恢复 update.json/update-mobile.json 及网站 current；旧 APK 和 legacy 清单保留，不删除统计数据库。API/报表代码可按各自快照恢复。
