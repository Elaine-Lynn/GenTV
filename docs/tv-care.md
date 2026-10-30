# GenTV 电脑端电视维护

这是仓库内的开发维护适配器，供能访问本地终端的 AI 助手使用。它通过已授权的原生或独立 ADB 客户端 调用 GenTV 共用的 Kotlin core，不需要其他项目的执行脚本或 APK。它不是独立桌面 App，不会扫描或配对电视，仅可连接用户明确给出的传输地址。

## 本地环境和构建

需要 JDK 21、Python 3，以及本地 Android SDK 的 `adb`。APK 包名及最低系统版本由内置解析器读取，不需要 aapt。入口会检查 PATH、SDK 环境变量、本地 SDK 配置及常见安装位置，首次构建后记录源码及产物 SHA-256；未变化时直接启动，变化后重新构建。

```sh
python3 tools/tv_care.py --help
```

也可显式构建并运行：

```sh
./phone/gradlew -p phone --no-daemon :tv-care:test :tv-care:installDist
```

所有下列命令在仓库根目录执行。`TV_SERIAL` 必须替换为 `adb devices -l` 中用户指定、状态为 `device` 的准确传输地址。`SESSION` 使用新的 `work/tv-care/时间` 路径；不可覆盖已有恢复记录。设备提示由用户确认。

## 只读识别与备份

```sh
python3 tools/tv_care.py inspect --serial TV_SERIAL
python3 tools/tv_care.py match --serial TV_SERIAL
python3 tools/tv_care.py backup --serial TV_SERIAL --session SESSION
```

`match` 调用本地 `ListParser`、`ListCatalog`、`ModelMatcher`，结合型号、设备代号、系统版本和真实安装包覆盖率排序，不是简单的字符串搜索。机型资料来自本地 252 份原有记录和 108 份补充记录；补充记录保留来源提交及许可证，不下载或执行外部工具。解析器拒绝的记录不会参与匹配；匹配仍需核对固件与保留项。

备份保存包列表、原禁用项、设置、系统属性和原 Home。私有目录、日志和原配置不提交；写操作验证设备身份及固件签名；老系统的降级识别见下文。断线后仍使用原会话，先检查日志和实际状态；更换固件后重新备份，旧会话只允许只读 `check`。

## 清理、缓存和 DNS

先生成用户审核过的 `SELECTED.txt`，一行一个准确包名，支持 `#` 注释。不能把机型清单不经审核直接执行。`KEEP.txt` 可列出用户要保留的包。

```sh
python3 tools/tv_care.py clean --serial TV_SERIAL --session SESSION --packages SELECTED.txt --keep KEEP.txt --model-list MODEL_ID
```

默认只输出计划。用户请求已覆盖这些具体清理项时，使用同一命令加 `--apply` 执行。本地 `Debloater`、静态/动态保护和持久日志负责逐包禁用及核验；电脑适配器另保护所有 Home 候选和关键 TV 中间件。原本已禁用、PERSISTENT、未知状态或不安全机型不会被修改。

```sh
python3 tools/tv_care.py caches --serial TV_SERIAL --session SESSION
python3 tools/tv_care.py dns --serial TV_SERIAL --session SESSION --provider ads
```

同样以 `--apply` 执行。缓存清理复用 `TvFacts.freeCaches` 并读实时可用空间，不清应用数据；缓存不能恢复。DNS 执行还需 `--router-dns-reviewed`，表示用户已确认现有路由器/VPN 域名过滤的取舍；支持 `ads`、`family`、`restore`。不能去除 YouTube 视频广告。DNS 设置后必须实测解析和流媒体播放。

## 安装和 Home 交接

APK 先按官方校验文件或本地构建记录确认 SHA-256。入口还用内置二进制 Manifest 解析器核对应用身份；不自动卸载、降级或绕过厂商侧载限制。

```sh
python3 tools/tv_care.py install --serial TV_SERIAL --session SESSION --apk APK_PATH --sha256 APK_SHA256
```

计划核对无误后加 `--apply`，安装并启动。电视画面与方向键由用户确认，再交接 Home：

```sh
python3 tools/tv_care.py home --serial TV_SERIAL --session SESSION
python3 tools/tv_care.py home --serial TV_SERIAL --session SESSION --ui-confirmed --apply
```

默认保留原桌面和设置向导启用。验证包含 Home 解析、从设置发送 Home 和前台 Activity；失败恢复原 Home。注入按键不能代替用户实体遥控器和重启验收。

完整安装的目标是让 GenTV 实际接管默认桌面，包含必要的可恢复交接，不再另行索要兼容接管授权。建议从一开始就提供已核验的 APK：

```sh
python3 tools/tv_care.py home --serial TV_SERIAL --session SESSION --apk APK_PATH --sha256 APK_SHA256 --ui-confirmed --apply
```

适配器先安装一次 GenTV，保留原桌面尝试 Home 角色及默认选择。成功即停止处理原桌面；失败后继续兼容分支，不按厂商或白名单判定不支持：

- **未知 Home**：核对当前解析结果、实际 HOME 候选及 `dumpsys package` 的 HOME 声明，识别具体组件后纳入接管。选择器、多候选或误指系统服务时先完成识别；不猜原桌面，不启动工厂应用。
- **CVTE**：可以保留原桌面直接切换。若固件仍抢 Home，先在已安装 GenTV 的情况下单独重启，确认电视和 ADB 恢复，再用相同命令加 `--boot-confirmed` 续办兼容接管。这个参数记录用户实际观察到的重启结果，不能自动假设已完成；不同时开启遥控器辅助服务。
- **Wi-Fi 归属**：若 setupwraith 拥有 Wi-Fi 或归属不可读，先让用户在电视设置忘记并重新加入网络，再重连续办。真实有线默认网络可作为另一条路径，必须同时核对当前默认 NetworkAgent 的 ETHERNET 传输和实际 Ethernet 默认路由，不仅凭口述“插了网线”。
- **恢复**：禁用仅作用于已识别的 Home 阻碍，原 APK 和恢复记录始终保留；核对真实包状态，失败回滚。Android/TV 核心服务不作为接管手段。不可解除的持久服务、侧载签名锁等具体限制应明确报告。

旧 `--allow-stock-disable` 参数仅保留兼容，不再作为完整安装额外授权的门槛。`--ui-confirmed`、`--boot-confirmed` 记录实际设备画面和重启观察，不能用参数替代观察。无障碍权限、影响用户常用功能的取舍仍由用户选择。

## 桌面布局、主题、开机和按键配置

```sh
python3 tools/tv_care.py desktop-export --serial TV_SERIAL --session SESSION --output SNAPSHOT.json
python3 tools/tv_care.py desktop-save --serial TV_SERIAL --session SESSION --profile PROFILE.json --revision REVISION
```

导出文件含 revision 和 schema2 config；编辑其中 config 对象，另存为 `PROFILE.json`。按本地 `docs/desktop-protocol.md` 修改应用排列、名称、文件夹或信号源映射。加 `--apply` 保存；电视做完整协议校验，旧 revision 会拒绝。保存前留原配置，写入不确定时先读，不自动重放。

```sh
python3 tools/tv_care.py look-export --serial TV_SERIAL --session SESSION --output LOOK.json
python3 tools/tv_care.py look-save --serial TV_SERIAL --session SESSION --config LOOK_EDITED.json --before-hash EXPORTED_SHA256
```

`look-export` 输出当前 GenTV 主题配置及其 hash。根据本地 `Config.java` 和支持的主题配置修改导出文件，保留无关字段；`look-save --apply` 通过本地 ThemeProvider 命令保存，保留现有壁纸文件，不清布局。涉及按键服务或开机落点还需要 `--allow-helpers` 和用户明确选择；配置不能代替无障碍授权，必须手测快捷键及音量长按。主题原配置纳入本会话恢复。新增壁纸素材仍通过手机管理端的既有流程导入。

## 验收和恢复

```sh
python3 tools/tv_care.py screenshot --serial TV_SERIAL --output SHOT.png
python3 tools/tv_care.py key --serial TV_SERIAL --key HOME --apply
python3 tools/tv_care.py verify-home --serial TV_SERIAL --session SESSION
python3 tools/tv_care.py check --serial TV_SERIAL --session SESSION --model-list MODEL_ID
python3 tools/tv_care.py restore --serial TV_SERIAL --session SESSION
```

恢复默认只输出计划，加 `--apply` 撤销本会话包状态、DNS、原 Home 和已记录的布局/主题；保留 GenTV 安装及其他会话的记录。中断时的 intent 也会被核对。配置若已被其他设备修改则停止该配置的回滚，不覆盖用户新改动。

仍需用户确认实体 Home、输入/应用快捷键、HDMI 图声、流媒体、投屏/语音保留功能、待机恢复和重启。包修改后至少等 15 秒落盘再重启。输出 `partial`/`rolled-back`/`needs-device-action` 的命令返回码为 2，停止结果为 1；成功或只读计划为 0，不能只看 ADB 命令返回码。

## 异常连接与私有 VPN

优先使用已配对的原生 adb，包括设备显示端口的加密无线调试。若 macOS 本地网络权限导致助手进程不可达，而普通终端可达，可让用户在普通终端运行本仓库中继：

```sh
python3 tools/tv_relay.py --host TV_PRIVATE_IP --port KNOWN_ADB_PORT
```

工具仅监听 `127.0.0.1`，输出分配的端口；使用该已知本机地址连接 ADB，再把准确传输地址用于维护命令。中继不建立授权，也不自动改为明文 ADB；TLS 字节原样转发。结束时停止中继，并关闭电视端调试。

用户明确要求维护另一家庭的电视时，可使用用户已经建立的 Tailscale/其他私有 VPN，地址、ACL 和已知端口由用户提供。不得开路由器公网端口、扫描远端网络或自动安装 VPN；无人接受电视授权提示时不能继续。私有 VPN 不代表厂商侧载或 Home 限制已经解除。

## 关闭调试

先记下已知的无线连接端口，用户关闭调试并撤销授权后执行：

```sh
python3 tools/tv_care.py ports --host TV_PRIVATE_IP --ports KNOWN_PORTS
```

`KNOWN_PORTS` 是逗号分隔的实际已知端口，不猜端口。只将明确连接被拒绝记为 closed；超时、不可达记为 unknown。重启后再次核对调试开关与已知端口；检查应直接指向电视，不能把已停止的中继端口当作电视调试关闭的证据。

## 备用客户端与断点传输

默认 `--transport auto` 优先原生 ADB；仅在操作前的连接检查失败时选备用客户端，不跨客户端重放写命令。已明确提供的 TCP 地址可先尝试原生 `adb connect`。未授权、仍离线或 TLS 配对问题须先处理电视提示。

```sh
python3 tools/tv_care.py --setup-transport
python3 tools/tv_care.py --transport python inspect --serial TV_PRIVATE_IP:KNOWN_PORT
```

独立客户端使用隔离 Python 环境中的固定版本 adb-shell，只支持用户已开启的传统 TCP ADB，不支持加密无线调试、USB 或绕过授权。不会改变电视调试模式。原生支持 TLS 的连接继续用原生客户端或字节透明中继。

APK 推送先走同步协议；传输失败时可使用限定于 GenTV 临时目录的分块通道。续传先核对已传部分长度和 SHA-256，每块核对长度，最后核对全文件 SHA-256 后移入目标。断线后重新执行同一安装计划会检查暂存进度；安装结果不确定时先查包状态。设备缺少可靠哈希工具时停止安装。

## 源码方式的预构建维护运行包

```sh
python3 tools/tv_care.py --prepare-runtime
python3 tools/package_tv_care.py
```

打包者需要 JDK 21 和 jlink。产物位于 `phone/tv-care/build/bundles/`，包含当前操作系统及架构的 Java 运行时、维护程序、skill、本地机型资料和许可证。用户解压后运行包内 `python3 tools/tv_care.py --help`，不需 JDK 或 Gradle。每次启动校验清单中的文件哈希；下载时另核对可信渠道给出的压缩包 SHA-256。仍需 Python 3；原生连接需 adb，APK 身份核对不再需要 aapt，独立连接首次准备需安装 Python 依赖。该包不包含 APK、密钥、会话或 SDK，不是独立桌面 App，也不是 GenTV CLI 产品。

## 隐私审计和实机性能

```sh
python3 tools/tv_care.py privacy --serial TV_SERIAL --output work/privacy.json
python3 tools/tv_care.py benchmark --serial TV_SERIAL --samples 5 --idle-seconds 3 --output work/performance.json --apply
python3 tools/tv_care.py benchmark --serial TV_SERIAL --output work/after.json --compare work/performance.json --apply
```

隐私审计只读汇总语音服务配置、本地资料中的 ACR/遥测候选、权限和 AppOps 原始证据，并生成私有 JSON 与 Markdown 报告。包名提示、已授予权限与实际访问记录分别说明；不能据此断言上传、录音或恶意行为，不自动禁用。

性能流程对已安装 GenTV 进行冷启动、注入方向键/回 Home 后首帧代理测量、帧时长/掉帧、PSS、闲置窗口整机 CPU 采样。会强制停止并重新启动 GenTV，不清数据或修改默认桌面；仅原本默认 GenTV 时测回 Home。缺失指标写 null，注入按键指标不能宣传为实体遥控延迟。前后对比要求相同有效硬件身份及固件。报告保留采样证据，模拟器结果须另列。

## 老系统识别与会话绑定

Home 查询按 `cmd`、旧版 `pm`、系统解析表逐级读取；只接受实际 HOME 声明，不猜组件。清理可在 Android 8 以下评估，GenTV 安装仍要求 API 26。

硬件序列号不可用时，用户确认准确目标设备后执行 `backup ... --device-confirmed`，在设备 GenTV 临时目录建立随机身份标记，之后每次核对标记与稳定型号属性。fingerprint 缺失时从至少两项系统构建属性生成替代签名；这不是完整固件证明。无可辨识构建属性、标记丢失或身份变化时停止写入并重新识别，不按 IP/型号复用恢复。此电脑同一账户的维护操作统一互斥，避免身份建立前后或不同地址造成并行写入。

## 常用配置直接用参数

```sh
python3 tools/tv_care.py configure --serial TV_SERIAL --session SESSION --columns 5 --effects smooth --greeting 客厅
python3 tools/tv_care.py configure --serial TV_SERIAL --session SESSION --boot home --apply
```

支持 `--dock` 逗号包名列表、`--hide-app` / `--show-app`、`--rename-app APP=NAME`、`--settings-key` / `--input-key` 数字键码列表及 `--app-key KEYCODE_NAME:PACKAGE`。AI 助手将用户自然语言转换为参数，先读取当前真实应用，缺失或多组件歧义时选择准确组件；常用操作无需用户编辑 JSON。高级文件夹、素材导入继续使用已有协议或手机端。配置保留无关字段，按布局和主题各自的冲突检测保存；部分成功会明确报告并保留恢复基线，不宣称跨协议原子提交。按键和开机能力仍受电视辅助服务权限约束，用户在电视上完成权限操作后实测。

## 冻结运行包和发布

构建机安装 `tools/requirements-tv-build.txt`，运行 `python3 tools/package_tv_skill.py --frozen`。用户入口为包内 `bin/gentv-care`（Windows 为 .exe），自带 Python、Java、传统 TCP 客户端；不需要手动安装 Python/JDK/Gradle/aapt。USB/TLS 调试仍需原生 adb。源码入口的构建依赖不变。Python、依赖和 Java 法律文件随包保留。

`.github/workflows/tv-care-release.yml` 分别在 macOS ARM64/x64、Linux x64、Windows x64 构建及启动检查，再合并统一 skill；结果以实际 CI 为准。固定 tag 生成 GitHub 草稿，普通 PR 仅上传 artifacts，不公开发布。原生准备脚本从固定版本 URL 下载并校验，缓存工作区可复用。

公开发布前另核对实机记录、附件和校验清单，CocoLoop 收录单独确认。发布者提交草稿位于忽略的 work/ 目录，不包含在公开源码中。
