# GHCP 1 本地源码验证记录（2026-11-24）

状态：开发预览，未发布，未覆盖真实电视。基于 HEAD `b9477b1` 及当前已有未提交改动的工作区；没有重置或提交原有改动。协议见 [GHCP 1](reference/home-content.md)，实现计划见 [产品计划](home-content-plan.zh-CN.md)。

## 本次交付

- Launcher：活动元数据发现、逐应用/全局许可、签名与服务绑定、异步 Messenger、三种原生模板、内容焦点与同包直达。
- 默认效果：现有壁纸上悬浮内容、轻移淡化、可选背景异步渐显/渐隐；不改用户壁纸设置。
- 失效与保护：请求代次、来源 UID/身份校验、一次有界冷启动重试、TTL、撤权、服务断开、包变化、屏保和内存压力清理。
- 资源：控制/图片/取消独立的进程级有界工作池；受限 JSON、资源来源、图片大小/采样及内存持有预算。
- 独立离线参考 App：summary、media_row（6 项）、feature，以及空内容、坏数据、超时、坏背景等测试模式。它不是实际天气或媒体业务服务。
- 公开接入文档：Manifest、消息编号、字段类型/上限、图片 URI、actionId、授权边界和构建方式，无须宿主与提供方同签名。

## 构建与通用检查

环境：JDK 17、Android SDK 34；Debug，不使用正式发布签名。

```sh
./launcher/gradlew -p launcher --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :home-content-example:assembleDebug :home-content-example:lintDebug
```

结果：BUILD SUCCESSFUL。Launcher 单元测试 **365 项，0 失败/错误/跳过**，其中新增模型测试 21 项。两模块 lintDebug、Debug APK 与测试 APK 构建通过。

通用检查：品牌检查、仓库检查、git diff --check 通过；Python 测试运行 72 项，70 项通过、2 项环境相关跳过。仓库检查显示的 Release APK 大小来自已有 Release 产物，不是本次新构建的 Release；本次不据此声称正式 APK 通过发布体积验收。手机版未修改或重建。

本地 APK、测试原始输出、截图与构建哈希放在忽略目录 `output/ghcp-validation-20261124/`。APK 在各模块 build/outputs/，不提交构建输出。

## 模拟器集成

只使用本次创建的隔离 AVD，不修改默认 Home。最初已有模拟器的 Launcher 因签名不同拒绝 Debug 覆盖，原安装与数据保留；没有卸载或清除该 GenTV，也没有改真实电视。临时装入该旧模拟器的参考 App 已移除。新的隔离模拟器可使用下列测试命令；不要把它用于拥有用户数据的设备：

```sh
adb -s <isolated-emulator> install -r launcher/app/build/outputs/apk/debug/app-debug.apk
adb -s <isolated-emulator> install -r launcher/home-content-example/build/outputs/apk/debug/home-content-example-debug.apk
adb -s <isolated-emulator> install -r launcher/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <isolated-emulator> shell am instrument -w -e home_content_only true com.gentv.tv.test/com.gentv.tv.DesktopIntegration
```

测试类首先拒绝非模拟器硬件。它会在隔离模拟器中设置示例的第一行顺序和许可，不能用于设备验收或保留布局的用户安装。

| 环境 | 最终结果 |
|---|---|
| Google TV / Android 16，API 36，1920×1080 | PASS 37 checks |
| AOSP Android 9，API 28，1920×1080（非厂商电视固件） | PASS 37 checks |

37 项检查包含：发现与默认拒绝、签名绑定许可、真实服务数据与来源显示、进入内容/返回图标、撤权清除、六卡片及第六项可达、feature 背景加载及图片实际可见性、刷新释放旧快照图片、坏背景保留摘要并恢复壁纸、空/畸形数据拒绝、外国包动作拒绝、外国 UID 回调拒绝、超时含重试回退、TTL 到期、前台刷新保留焦点、内存清理保留文本、实际打开提供方页面收到 `weather` actionId，以及返回 Home 恢复内容。

原始安装输出为 `Success`，明确启动输出为 `Status: ok`。集成成功的原始终止标记应为：

```text
INSTRUMENTATION_RESULT: result=PASS 37 checks; Messenger, templates, images, focus, revocation, expiry, malformed and foreign input
INSTRUMENTATION_CODE: 0
```

开发中修复了首次布局仍沿用旧 dock 坐标导致操作项裁切、刷新时先移除旧焦点引发会话取消、刷新请求期间旧但仍有效快照的操作被错误拦截，以及 ImageView 整数透明度重载导致背景加载后仍不可见的问题。背景现在只对视图透明度做动画，最终截图已目视检查。跨应用返回测试通过 UiAutomation 注入，避免旧 Android 对普通 Instrumentation 跨 UID 注入的限制；AOSP 触屏镜像的夹具先退出触摸模式，再等实际状态就绪后断言，不把冷启动时间误当协议结果。

## 尚未验证的边界

- 真实电视、实体遥控器、Home 默认交接、待机/重启、厂商固件和真实 1 GB 设备性能。
- API 26 实机/镜像；当前最低 API 仍是 26，但本次运行覆盖仅 API 28/36。
- 多应用快速切换与恶意慢图片提供方的完整压力矩阵；有界/来源防护已有代码，不把结构审查当运行验证。
- 完整 RTL、大字体、短屏/多行主题和 TalkBack 可用性矩阵。
- 真实私人数据提供方的独立授权、退出账号与 URI 撤权案例；公开虚构数据的样例不能替代。
- 全部历史视觉探索模板、在线扩展目录、手机协助安装、SDK/兼容工具发布与正式 Release 验收。

这些项目不标为 PASS。主计划的阶段清单继续保留，不能用本次局部集成结果替代所有 P0/P1/P2 退出条件。
