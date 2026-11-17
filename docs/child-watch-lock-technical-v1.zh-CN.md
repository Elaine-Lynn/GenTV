# GenTV 儿童防沉迷技术方案 V1

> 历史预研记录，保留当时结论；当前正式功能和限制见[防沉迷系统说明](child-mode.md)。

状态为预研，记录日期为 2026-11-05。方案面向电视端研发、机型适配和真机验收。

GenTV 默认桌面已在 DBB3 Pro 上验证两种开机恢复场景。答题和休息状态可以跨重启恢复，且经过 GenTV 的应用入口受限。普通安装下，系统直接启动腾讯视频可以绕过桌面门禁。Device Owner 配合 Lock Task 和应用挂起在同一台盒子上挡住了该启动路径，但固件未能移除测试管理员。这验证了系统托管路线在该设备上的锁定机制。DBB3 Pro 的撤销问题暂缓处理，管理员撤销仍是独立的部署验收项。

## 1. 规则与实现边界

家长模式拟提供年级、观看间隔和受管应用设置，默认间隔 10 分钟。首版用亮屏期间受管应用在前台的累计时长近似观看时长。换到另一个受管应用继续累计，待机、屏保和离开受管应用时暂停。第三方播放器内的暂停可能仍计时，界面应注明口径。

| 状态 | 触发与行为 |
| --- | --- |
| 观看 | 到点先持久化待答状态，再显示全屏题目并执行对应的播放拦截。 |
| 待答 | 按年级选择离线题库中的四选一题目。前两次答错换题，第三次答错进入 5 分钟休息。 |
| 休息 | 维持原有观看限制。倒计时结束只允许再答题。再次答错的等待档位建议为 10、15、20 分钟，20 分钟封顶。 |
| 解锁 | 答对或家长验证后清除本轮状态并释放本轮施加的策略。播放器是否能恢复原进度取决于自身行为。 |

当前桌面原型已实现答题和休息状态的恢复、桌面入口拦截、简单算术题以及 5 分钟休息。年级题库、家长设置与 PIN、逐档延长休息、可靠的第三方应用自动计时和完整家长恢复流程属于后续实现。桌面验证使用预置状态，托管验证使用隔离 APK，正式发行包尚未提供托管配置入口。 [ChildLock.java](../launcher/app/src/main/java/com/gentv/tv/ChildLock.java) · [ChildLockPolicy.java](../launcher/app/src/main/java/com/gentv/tv/ChildLockPolicy.java)

## 2. 技术分层

### 桌面门禁和重启恢复

在 `MainActivity` 恢复前台及接收新的 Home 意图时检查锁定状态。锁定优先于开机自动播放、初始设置或输入请求。桌面应用启动和 Android TV Remote 的 `market://launch` 入口检查同一状态。正式版还需核对 GenTV 自有的快捷键、信号源、设置快捷面板及其他可以直接启动活动的入口。 [MainActivity.java](../launcher/app/src/main/java/com/gentv/tv/MainActivity.java) · [AppLaunchActivity.java](../launcher/app/src/main/java/com/gentv/tv/AppLaunchActivity.java)

锁定状态存入设备保护存储，包含阶段、答错次数和休息时间。单次开机内用单调时钟计算倒计时。跨重启时，墙钟可信才用持久化的截止时间扣除经过时长，否则恢复最近的剩余时长检查点。DBB3 Pro 曾在开机初期报告 2009 年，不能直接依靠墙钟。当前实现每约 10 秒记录一次检查点，时钟不可用时，重启后的剩余时间可能比实际多一个检查周期或更长，例如界面离开前台时。倒计时归零依然保持待答。恢复依赖固件真正启动 GenTV Home。 [ChildLock.java](../launcher/app/src/main/java/com/gentv/tv/ChildLock.java)

### 普通安装的跨应用干预

可选的无障碍服务提供前台应用事件和覆盖层，用于计时和弹题。验证需分别观察前台窗口识别、覆盖层能否取得遥控器焦点、播放器是否确实暂停或停止音频，以及服务被固件终止后的表现。对不支持暂停或焦点不稳定的组合，仅开放已经验证的桌面门禁，或将弹题标成提醒。系统直达、语音、投屏、品牌键、原生信号源均需单独验收。 [Android 无障碍覆盖层](https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo#TYPE_ACCESSIBILITY_OVERLAY) · [后台 Activity 启动限制](https://developer.android.com/guide/components/activities/secure-bal)

### 系统托管锁定

实验构建由 Device Owner 在锁定时挂起明确指定的播放器，并让 GenTV 进入系统 Lock Task。此时以系统报告的 `LOCKED` 和播放器的实际挂起状态为准。重启后，GenTV Home 读取持久状态并核对策略，答错或休息结束都不释放策略。答对后释放本轮施加的挂起并退出锁定。发布实现需另外验证前台播放中的切换、进程中断后的策略对账、已有管理策略的归属、多应用允许名单、家长恢复和管理员撤销。 [ChildLockPolicy.java](../launcher/app/src/main/java/com/gentv/tv/ChildLockPolicy.java) · [Android Lock Task](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode) · [Android 应用挂起](https://developer.android.com/work/dpc/security#disable-access-to-apps)

## 3. 已有证据

| 环境 | 实测结果 | 仍待验证 |
| --- | --- | --- |
| Google TV 模拟器 | 约 30.2 秒弹题，连错三次进入休息，重启恢复休息。强行重开测试播放器时，播放状态曾短暂恢复，随后被踢回桌面。 | 测试播放器的日志时间差不代表实际音频输出。实体按键、厂商流媒体和信号源需真机验证。 |
| DBB3 Pro，Android 9，普通安装 | 首次自动识别当前播放窗口失败。手动起表后约 30.1 秒显示答题，腾讯视频活跃音轨停止。系统随后曾强制停止独立测试进程。系统跳过独立 APK 的开机广播。 | 外部直接启动腾讯视频可进入前台，本次绕过用例未确认实际出画出声。实体 Home 未使当时的视频继续播放，品牌直达键未实测。 |
| DBB3 Pro，GenTV Home | 隔离构建作为默认桌面，分别重启验证待答和休息页自动恢复。休息结束后仍拦截 GenTV 入口，答对后可以重新打开腾讯视频。 | 待答和缩短的休息时长用预置状态测试，需在正式规则引擎接入后跑完整时序。 |
| DBB3 Pro，Device Owner 实验 | 待答时重启，系统恢复 `LOCKED`，腾讯视频保持挂起。ADB 直接启动失败，前台仍为 GenTV，未观察到目标应用活跃音轨。答错继续锁定，答对解除挂起，腾讯视频重新进入前台。 | 系统直接启动返回固件异常，不能据此推断实体品牌键和所有系统路径。未独立验证托管模式下休息状态的重启，也未验证答对后的实际流媒体续播。 |

**DBB3 Pro 的托管撤销是部署阻塞。** 测试管理员移除时，固件报 `Unknown URI content://telephony/carriers/enforce_managed`，Device Owner 记录仍在。已退出 Lock Task、解除腾讯视频挂起、停用隔离测试包的桌面入口和辅助服务，并恢复测试前的 Home 选择状态。后续只读探测返回 `Could not find provider: telephony`。Android 9 上游实现先清理 APN，再写入清除后的 Owner 状态，与此次失败顺序一致。该机型不得列为可部署的托管机型。其他机型应先在可恢复的专用测试设备上验收安装、撤销和原状态恢复，再进入播放测试。 [Android 9 设备策略源码](https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r61/services/devicepolicy/java/com/android/server/devicepolicy/DevicePolicyManagerService.java)

原始证据保存在本地忽略的测试目录，主要记录为 `emulator.log`、`dbb3-gate.txt`、`dbb3-boot-restore.txt`、`pending-boot.txt`、`rest-boot.txt`、`expiry-unlock.txt`、`managed-unlock.txt`、`managed-cleanup.txt`。

## 4. 从现有机型资料推导适配方案

仓库的机型索引和 72 条信号源路由可以确定候选机型、播放入口和固件差异。沿用现有匹配约束，先核对品牌或制造商与具体型号或代号，再核对 Android 主版本和固件指纹。共享主板的资料发生冲突时，保留标准 Android 路径并进入实测。机型资料提供入口和测试线索，不能证明按键可拦截、覆盖层可用、播放器可挂起或 Device Owner 可撤销。 [机型索引](devices/README.md) · [信号源适配](input-compatibility.md) · [共享路由目录](../phone/framework/data/source-profiles.json)

以下各行是**待验证的适配路线**，DBB3 Pro 的实测范围以第 3 节为准。

| 机型或固件族 | 现有线索 | 建议适配与验收重点 |
| --- | --- | --- |
| Google TV 与较接近标准 Android TV 的固件 | TCL 65C8L、65QM8L 的 Android 14 记录给出 `com.tcl.tv`，Sony BRAVIA VU3 使用 `com.android.tv`。 | 先做默认 Home 重启恢复和 Android 播放器拦截。直播按机型单测。固件若有 setupwraith 网络归属问题，沿用现有 Home 交接流程并确认重启后联网。 |
| 当贝 DBB3 Pro、Android 盒子 | DBB3 Pro 没有 `tv_input` 服务。小米盒子 S 的 Android 9 资料也未配置直播播放器。 | 先验收 Android 应用、系统直达和实体遥控器。DBB3 Pro 只认可已实测的桌面门禁范围，托管部署被撤销失败阻塞。其他盒子的服务保活和托管撤销须独立实测。 |
| 小米与 Redmi 电视 | ASTP0、MTEQ0 有不同的厂商信号源映射，MOOQ3 的直播入口为 MediaTek 电视应用。 | 按型号拆分应用键、直播和输入源。用实际输入事件确认是否走 GenTV 已接管的按键路径，厂商 HDMI 切换和直播需单测。 |
| TCL 定制 Android 固件 | TC8000 AY30A2 的输入面板走厂商服务，GenTV 另有该类固件强停后的 Home 恢复记录。 | 加测熄屏、杀进程、休息页重启和品牌直达键。验证无障碍开关前后的音量长按和实体键。既有 Home 恢复记录可用作测试线索。 |
| 海信 Android、Vidda、创维与酷开 | 海信 E5Q Pro 和 Vidda MT9653 走海信输入服务，创维 7S60 A5 使用酷开控制中心，7T861 A23 有独立直播包。 | 将 Android 视频、直播和厂商输入面板分开计入验收。通过面板切到 HDMI 或直播的路径若不受控，明确列出未覆盖的来源。 |
| CVTE 与深度定制投影仪 | 本地有 Sankey CVTE 样本。BYINTEK U4 走厂商输入 Activity，极米 Horizon S Pro 走 HDMI 专用 Action。 | 按机型确认默认 Home 能跨重启保持。CVTE 复用单独的重启检查点，输入面板及 HDMI 分别验证。Home 被固件抢回时，降级为仅 GenTV 入口门禁。 |
| Fire OS 和非 Android 平台 | 仓库 Fire OS 资料注明原 Home 受保护。Tizen、webOS、VIDAA 使用独立平台。 | 受保护 Home 的机型无法依赖 GenTV 开机恢复。非 Android 平台仅可评估另行接入兼容 Android 盒子，控制范围止于盒子内的播放来源。 |

机型差异的原始依据见 [TV Care 的 Home 交接流程](tv-care.md) 和 [机型与固件例外](../skills/gentv-tv-care/references/compatibility.md)。

## 5. 主流机型适配实现

### 共用实现

1. **统一 GenTV 门禁。** 将应用卡片、应用库、开机自动打开、`market://launch`、GenTV 已接管的遥控键和信号源请求接到同一状态检查。待答和休息阶段先恢复锁定页，再处理启动请求。家长恢复入口单独处理。
2. **分离路由与验证结果。** TV 端沿用 `InputProfiles`、`InputProfileMatch` 和 `InputSources` 识别标准或厂商信号源。另记每个固件和入口的 Home 恢复、前台识别、覆盖层焦点、音频中断、系统直达拦截和管理员撤销结果。路由命中只提供候选入口。
3. **按播放来源选控制方式。** 普通 Android 应用以包和前台窗口识别，无法稳定识别时暂停累计并提示。存在可验证的暂停接口时，先暂停再弹题。原生直播、HDMI、投屏和 CEC 单独验证。覆盖层显示但音轨仍在时，产品只标记为画面遮挡。
4. **在运行时降级。** 每次开机核对实际 Home 与权限。厂商抢回 Home 时，GenTV 内仍可拦截应用启动，开机恢复显示为不可用。服务被关闭、固件或目标应用版本变化时，重新核对受影响能力。系统应用直达无法拦截时，继续保留桌面门禁。
5. **托管策略单独交付。** 用可撤销的专用 DPC 测试构建验证。先保存目标包和本轮策略，再挂起已确认的播放器并进入 Lock Task。重启后核对实际挂起和系统锁定状态，解锁只释放本轮施加的策略。采用设备托管前，先在授权的专用样机上验证管理员移除、原桌面和网络恢复。

[InputProfiles.java](../launcher/app/src/main/java/com/gentv/tv/InputProfiles.java) · [InputProfileMatch.java](../launcher/app/src/main/java/com/gentv/tv/InputProfileMatch.java) · [InputSources.java](../launcher/app/src/main/java/com/gentv/tv/InputSources.java)

### 候选样机和实现切入点

优先级按技术覆盖面和验证成本排序。以下样机的儿童模式能力待实测。组件名用于识别来源，不能直接用作挂起清单。

| 优先级与候选样机 | 适配实现 | 验收重点 |
| --- | --- | --- |
| P0 · TCL 65C8L／65QM8L、Sony BRAVIA VU3、Philips Google TV TA7 | 共用 Android 14 Home 恢复和应用门禁。直播分别按 `com.tcl.tv`、`com.android.tv`、`com.mediatek.wwtv.tvcenter` 识别。首轮保留 setupwraith。 | 冷启动进 GenTV、两种锁定页恢复、Android 视频出画出声与阻断、实体品牌键及语音直达。每个品牌的直播独立验收。 |
| P0 · NVIDIA SHIELD TV 2019、小米盒子 S | 用 Android 11 和 Android 9 样本覆盖盒子形态。SHIELD 有 `com.google.android.tv` 路由，小米盒子 S 未配置本地直播。优先接入盒子内 Android 应用和投屏。 | 分别验收盒子内播放、投屏和系统直达。电视自带应用和其他 HDMI 输入按电视侧单列。任选一台具备可验证撤销路径的专用盒子做托管候选。 |
| P1 · 小米 ASTP0／MTEQ0、Redmi MOOQ3 | 接入已有的 `EXTSRC_PLAY` 路由和各型号信号源编号。MOOQ3 的 MediaTek 直播单测。GenTV 已接管的遥控键走统一门禁。 | 比对实体输入键、GenTV 输入菜单和品牌直达键。原生输入键能绕过时，将该来源标为未覆盖。 |
| P1 · 海信 E5Q Pro／Vidda MT9653、创维 7S60 A5／7T861 A23 | 海信样本按厂商输入服务识别。创维样本分别按酷开控制中心和独立直播包识别。在 GenTV 请求厂商面板前做门禁。 | 测试系统能否独立打开厂商面板并切换来源。Android 视频、内置直播和 HDMI 各自记录画面和声音结果。 |
| P1 · TCL TC8000 AY30A2、CVTE 样本 | TCL 加测厂商输入面板和第三方进程被强停后的表现。CVTE 沿用独立重启检查点，分开验收 Home 交接与遥控辅助后再组合。 | 服务未恢复时退回已验证的 Home 门禁。Home 被固件抢回时停止开机恢复承诺。核对音量长按、输入切换和网络恢复。 |
| P2 · 极米 Horizon S Pro、BYINTEK U4、Fire TV | 极米和 BYINTEK 分别用已有 HDMI 专用 Action 和厂商输入 Activity 验证。Fire TV 先确认固件是否允许持久替换 Home。 | 单测原生 HDMI、CEC 切换和厂商面板。Home 受保护的 Fire TV 不纳入依赖 GenTV 开机恢复的方案。 |

Samsung Tizen、LG webOS、Hisense VIDAA 可评估外接兼容 Android 盒子。GenTV 在这种架构中仅控制盒子内经验证的应用或投屏入口，电视侧仍独立管理。

### 验证记录与交付顺序

每条结果对应 **具体固件 × 播放来源 × 启动入口**。例如同一台 TCL 上，腾讯视频从 GenTV 应用库启动与从品牌键启动要记成两条。每条保留型号、固件指纹、SDK、GenTV 和目标应用版本、日期、画面与音频结果、重启恢复、撤销结果及证据编号。统一使用通过、失败、未测、不适用。进入前台、实际播放、系统挂起和用户观察分别记录。

先选一台 Android 14 Google TV 和一台主流 Android 盒子，验证 Home 门禁与逐应用的到点弹题、停止播放。第二批增加小米厂商路由、海信或酷开输入面板。托管验收单独排在确认可撤销的样机上。只有对应固件、来源和入口通过验收，才将结果用于产品界面的覆盖说明。

## 6. 机型验收与发布判定

每条验收结果按 **型号、固件指纹、Android 版本、播放来源、目标应用版本、启动入口** 记录。桌面、系统设置里的应用入口、实体 Home 和返回、品牌直达键、语音、投屏、HDMI 和内置直播分别记录。对没有相应入口的设备记录不适用，对缺少现场观察的项目记录未测。

1. **播放基线**：先在目标应用确认连续出画和出声。记录前台 Activity、目标应用音轨和电视前的观察。受保护内容可能无法截图，使用现场画面与系统证据交叉验证。
2. **到点和锁定**：用短间隔检查实际弹出时间、焦点、画面和声音。连续三次答错后，锁定期间尝试所有适用入口，记录是否有短暂恢复播放。
3. **重启恢复**：分别在待答和休息时重启。先看系统自动恢复的页面，再发送任何输入。检查倒计时结束后仍待答、答错保持限制、答对或家长验证后可恢复。
4. **策略验收**：托管场景同时检查系统 Lock Task 状态、挂起结果、实际前台和音轨。设备管理员配置须有单独授权，专用设备先验证退出、移除管理员、恢复原 Home 与网络。任何无法恢复的固件都不得进入部署名单。

发布时按播放来源标注能力。**桌面门禁**只承诺经 GenTV 的已验收入口。**播放中干预**还要求实测到点弹出以及对目标应用的画面和声音控制。**系统托管锁定**另外要求系统直达拦截、跨重启恢复和可撤销性全部通过。当前已有 DBB3 Pro 的桌面恢复证据，其他机型以候选矩阵安排验证，不新增兼容名单。
