# Gen 插件 App 开发规范（GHCP 1）

版本：GHCP 主版本 1 · 文档日期：2026-11-24 · 状态：开发预览。

面向为 GenTV 提供桌面内容的 Android App 开发者。本文以当前 Launcher 的实际实现为依据，规定接入、通信、内容、图片、交互和验收要求。**必须**表示兼容条件，**应当**表示开发建议，**可以**表示可选能力。

基础模板为 `summary`、`media_row` 和 `feature`。新版宿主还可声明支持可选的 `channel_preview`。协议已接入本地源码和测试盒子，不表示现有公开发行的每个 GenTV 版本均支持。开发前须使用包含 GHCP 1 的宿主构建验证。历史效果图中的其他模板、在线插件商店、自动安装、通用 SDK 和兼容认证工具不属于当前已交付能力。

相关入口：[英文线级契约](home-content.md) · [完整独立示例 App](../../launcher/home-content-example/README.md) · [默认呈现设计契约](home-content-presentation.zh-CN.md)。本文描述当前可接入的能力；呈现设计契约中的扩展目标不自动成为已实现接口。

## 1. 工作方式与边界

Gen 插件是一个普通、独立安装的 Android App，通过 **GHCP（GenTV Home Content Protocol）**向 GenTV 返回结构化内容。它不是加载到 Launcher 内执行的插件代码。

1. 用户安装 App；App 的启动 Activity 声明协议元数据。
2. 用户在 GenTV「桌面内容」（部分入口称「Gen扩展」）中明确允许展示。
3. 用户将该 Activity 对应图标放到第一行。
4. 图标焦点稳定约 200 ms 后，GenTV 显式绑定 App 的内容 Service。
5. Service 通过 Messenger 返回一份 JSON 完整快照。
6. GenTV 校验后使用原生模板显示内容，独立异步读取图片。
7. 用户按上进入可操作内容，点击后打开 App 自己的 Activity。
8. 离开焦点、退出桌面或授权失效后，宿主取消会话并回收展示资源。

App 可以使用 Java 或 Kotlin，无须依赖 GenTV SDK、使用 GenTV 签名、申请 ADB 权限或访问官方服务器。最低宿主系统要求为 Android 8 / API 26；App 自身的 minSdk 可按业务选择，但必须能安装到目标电视。

用户展示许可与 App 自身账号/数据许可是两套独立授权。安装、目录收录、加入第一行都不自动授予展示许可；展示许可也不代表 App 可以向宿主提供私人数据。当前养鱼模式、儿童保护启用期间及内容空间不足时不展示 GHCP 内容。不要要求用户关闭儿童保护来完成基本接入。

## 2. 最小接入步骤

- 声明一个真实、启用且可公开启动的入口 Activity，并在其上放置协议元数据。
- 声明同包、同 UID 的 exported 内容 Service，返回 Messenger Binder。
- 在工作线程处理消息 `1`，返回消息 `2` 和合法 JSON。
- 若需要可点击内容，为条目声明同包 exported Activity，并处理 `ACTION_ID`。
- 在测试宿主中完成用户授权、加入第一行以及第 12 节联调验收。

推荐先完成无图片、无背景的 `summary`，再接入封面和背景。正式业务数据获取、联网、登录、缓存由 App 自己负责；请求到来时应当快速返回已准备好的有效内容。

## 3. AndroidManifest 声明

以下示例包名为 `com.example.genplugin`，开发者必须替换为自己的包名与类名。TV 图标和横幅资源由 App 提供。

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <!-- 用于在受包可见性限制的系统上查询宿主身份。 -->
    <queries><package android:name="com.gentv.tv" /></queries>

    <application android:label="我的 Gen 插件"
        android:icon="@drawable/app_icon" android:banner="@drawable/app_banner">
        <activity android:name="com.example.genplugin.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
            <meta-data android:name="com.gentv.tv.home.PROTOCOL_MAJOR"
                android:value="1" />
            <meta-data android:name="com.gentv.tv.home.CONTENT_SERVICE"
                android:value="com.example.genplugin.HomeContentService" />
        </activity>
        <service android:name="com.example.genplugin.HomeContentService"
            android:exported="true">
            <intent-filter>
                <action android:name="com.gentv.tv.home.BIND_CONTENT" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

### 3.1 发现与身份要求

| 项目 | 必须满足 |
|---|---|
| 元数据位置 | 在实际启动 Activity 上，不能只放在 application 或 Service 上 |
| `PROTOCOL_MAJOR` | Android integer `1`，不能写字符串 `"1"` 型的资源替代值 |
| 启动入口 | `MAIN` + `LEANBACK_LAUNCHER` 或 `LAUNCHER`；推荐电视入口 |
| `CONTENT_SERVICE` | 全限定类名、`.相对类名` 或 flattened component；推荐全限定名 |
| 组件状态 | Activity、Service、Application 都启用；Activity 和 Service 均 exported |
| 组件归属 | 启动 Activity 与 Service 在同一包且 UID 相同 |
| 服务权限 | 若 Service 要求权限，GenTV 必须已持有；不要用同签名权限阻断普通接入 |
| APK 签名 | 当前只支持单一当前签名者；多当前签名者会被拒绝 |

授权绑定到启动 Activity、Service 组件及当前签名证书。更换证书或 Service 后须重新获得展示许可；不能假设签名轮换历史会自动沿用许可。普通同身份覆盖升级保留许可，卸载后重新安装不应依赖旧许可。

服务 action 是协议名称；绑定使用显式 Service 组件，不靠隐式服务匹配。App 不应在无人绑定时启动常驻前台服务来维持桌面内容。

## 4. Messenger 消息协议

只使用 Android 框架的 `Message`、`Bundle`、基础值和 JSON 字符串。**不得**把自定义 Parcelable、RemoteViews、PendingIntent、Bitmap、序列化 Intent、HTML 或可执行脚本作为协议内容交给宿主。

### 4.1 请求：REQUEST（what = 1）

| 位置 | 类型 | 语义 |
|---|---|---|
| `Message.what` | int | 固定 `1` |
| `Message.arg1` | int | 不透明请求 token，响应必须原样回传 |
| `Message.replyTo` | Messenger | 当前请求的宿主回调 |
| `Bundle.protocolMajor` | int | 固定 `1` |
| `Bundle.component` | String | 当前来源启动 Activity 的 flattened short component |
| `Bundle.locale` | String | 宿主界面 BCP-47 语言标签 |
| `Bundle.widthDp` | int | 当前内容区域宽度提示，单位 dp |
| `Bundle.heightDp` | int | 当前内容区域高度提示，单位 dp |

尺寸只是提示，不是 App 自绘尺寸，也不保证全部文字完整展示。必须用 Android 提供的 `Message.sendingUid` 识别请求方，不能把 `component` 或自报包名当作身份凭证。不要信任调用者自行设置的 `Message.arg2` 等未定义字段。

### 4.2 响应：SNAPSHOT（what = 2）

| 位置 | 类型 | 语义 |
|---|---|---|
| `Message.what` | int | 固定 `2` |
| `Message.arg1` | int | 原请求 token |
| `Bundle.snapshot` | String | 一份 UTF-8 编码长度不超过 65,536 字节的 JSON 对象 |

每次请求只接受第一份响应。旧 token、非当前来源 UID 的消息、迟到响应和重复响应均不能更新桌面。每份响应必须是完整快照，不是增量 patch。`snapshotId` 是内容标识，不替代请求 token，也不是授权凭证。

绑定/响应/校验的宿主等待预算约 1 秒。当前来源从未收到快照时，宿主允许在 500 ms 后进行一次冷启动重试；不得依赖反复重试才能正常显示。网络请求不应阻塞这条链路。无有效数据时返回 `items: []`；不要编造业务数据或只返回半份 JSON。

### 4.3 失效通知：INVALIDATE（what = 3）

提供方在当前绑定会话内，通过保存的 `replyTo` 发送 `what=3`，`arg1` 使用当前请求 token，不需要 JSON。

```java
Message invalidation = Message.obtain(null, 3);
invalidation.arg1 = currentToken;
currentCallback.send(invalidation);
```

适用于注销、数据访问撤销或内容失效。宿主清除旧内容及背景，并把内容焦点退回来源图标；来源仍活跃时可以重新请求。自动重取限制为最多每秒一次，更快的连续通知会清除展示而不继续自动重取。不要用 INVALIDATE 实现逐帧更新、倒计时刷新或实时推流。

提供方必须同步停止提供已经撤销的数据，并处理旧的在途请求；只发一条通知、随后又返回私人旧快照是不合格实现。解除绑定/服务销毁后应释放回调引用和对应授权资源。

## 5. JSON 数据规范

### 5.1 顶层字段

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `protocolMajor` | integer | 是 | 只能为 `1` |
| `snapshotId` | string | 是 | 非空白，最长 80 |
| `template` | string | 是 | `summary` / `media_row` / `feature` |
| `title` | string | 是 | 非空白，最长 80 |
| `subtitle` | string | 否 | 最长 160；省略表示无副标题 |
| `privacy` | string | 是 | `public` / `personal` |
| `ttlSeconds` | integer | 是 | 1–300 秒 |
| `background` | string / null | 否 | 受限图片 URI，最长 1,024；省略/null/空字符串均无背景 |
| `items` | array | 是 | 数量上限见模板表；空数组表示当前无内容 |

### 5.2 条目字段

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `id` | string | 是 | 同快照内唯一、非空白，最长 80；同业务项应保持稳定 |
| `title` | string | 是 | 非空白，最长 80 |
| `subtitle` | string | 否 | 最长 160 |
| `image` | string / null | 否 | 受限图片 URI，最长 1,024 |
| `progress` | number | 否 | 有限数值 0–1；例如 0.45 表示 45% |
| `activity` | string | 否 | 同包 flattened Activity component，最长 240 |
| `actionId` | string | 否 | 最长 80；非空时必须同时有 `activity` |

长度按 Java `String.length()` 的 UTF-16 码元数检查；整个 JSON 另外按 UTF-8 字节数限制。emoji 可能占用两个码元。除了图片字段外，已出现的可选字符串也必须是字符串，不能用 `null` 替代；省略不需要的字段。整数必须写整数形式，不要把 `ttlSeconds` 写成 `"30"` 或 `30.0`。`progress` 若不展示应省略，不能传 `null` 或 `-1`。

已定义字符串禁止 ISO 控制字符及双向覆盖/隔离控制字符，因此不能用换行排版；自然语言阿拉伯文等不等于被禁止，宿主仍应按其布局能力处理。必须输出标准 JSON，不写注释、尾随内容或重复键。宿主拒绝重复条目 ID、超过 12 层嵌套和超限字段。未知附加字段会被忽略，不能据此要求宿主支持新模板或执行新能力。

### 5.3 模板及建议用途

| 模板 | 最大条目数 | 当前宿主行为 | 建议用途 |
|---|---:|---|---|
| `summary` | 3 | 文本/数值摘要；不显示条目 `image`；可显示进度和操作 | 天气、设备状态、今日概览 |
| `media_row` | 6 | 横向封面卡片，带文字、可选进度和操作；按焦点横向滚动 | 继续观看、歌单、节目推荐 |
| `feature` | 1 | 单张内容图及文字/操作；内容图与背景独立 | 相册、单项推荐、主题内容 |

上限是整份快照的条目数，不是超过后自动截断。不要依赖卡片固定像素尺寸、固定同时可见数量或长文字永远不截断。建议重要标题保持简短。无 `activity` 的条目只供阅读，不产生操作焦点；全是信息项时，按上不会强行进入一个不存在的内容按钮。

### 5.4 可直接修改的快照示例

天气摘要，默认不带背景：

```json
{
  "protocolMajor": 1,
  "snapshotId": "weather-20261124-01",
  "template": "summary",
  "title": "今日天气",
  "privacy": "public",
  "ttlSeconds": 60,
  "items": [
    {"id": "temperature", "title": "18°", "subtitle": "多云",
     "activity": "com.example.genplugin/.MainActivity", "actionId": "weather"},
    {"id": "air", "title": "优良", "subtitle": "空气质量"}
  ]
}
```

媒体卡片，在顶层使用 `template: "media_row"`，`items` 可按以下结构扩展至 6 项：

```json
[
  {"id": "episode-42", "title": "山与海", "subtitle": "第 3 集",
   "image": "content://com.example.genplugin.images/covers/episode-42/v3",
   "progress": 0.45, "activity": "com.example.genplugin/.MainActivity",
   "actionId": "episode-42"}
]
```

单图内容，在顶层使用 `template: "feature"`，`items` 只能有 0 或 1 项：

```json
[
  {"id": "album-cover", "title": "山川之间",
   "image": "content://com.example.genplugin.images/albums/mountains/v2",
   "activity": "com.example.genplugin/.MainActivity", "actionId": "album-mountains"}
]
```

以上两段数组是 `items` 字段的片段，不可直接作为 `Bundle.snapshot`。若希望单图同时有大图背景，在完整快照顶层另加 `background` URI；只有 `image` 不会自动变成背景。

### 5.5 可选频道预览

当 REQUEST Bundle 的 `channelPreview` 布尔值为 `true` 时，App 可以返回 `channel_preview`。旧宿主未发送该能力时应返回既有模板。此模板最多三项，只接收 `public` 内容，省略 `background`、`activity` 与 `actionId`。每项提供稳定的 `id`、`title`、可选节目名 `subtitle`，以及必填的 `video`。

`video` 必须是 `android.resource://<本包名>/<十进制 raw 资源 ID>`，单个资源不超过 4 MiB。建议使用短的 H.264 MP4 预览片段。宿主在左侧同一窗口静音循环播放，右侧频道可用遥控器上下移动并按 OK 切换。离开来源释放播放器。宿主按来源和签名保存上次选中的频道 ID，未匹配时播放第一项。

## 6. 图片提供规范

只支持以下来源：

- `android.resource://<App 包名>/<十进制资源 ID>`，例如 Java 表达式 `"android.resource://" + getPackageName() + "/" + R.raw.landscape`。不得复制另一个构建中的资源 ID，APK 更新后 ID 可能变化。
- `content://<App 自有 Provider authority>/<版本化路径>`，Provider 必须属于同包且 UID 相同。

URI 不得包含 query、fragment 或 user-info。不支持 `http://`、`https://`、`file://`、磁盘绝对路径和其他 App 的 Provider。联网图片必须由 App 先取得，再通过自己的受控 Provider 提供；GenTV 不替 App 下载外网 URL。

有读取权限要求时，App 必须向经过验证的宿主明确授予只读 URI 权限；在 Messenger Bundle 中写 URI 不会自动产生授权。Provider 可使用非全局导出的实现配合精确 URI 授权，应避免把私人图片通过任意调用方可读的 exported Provider 暴露。私有 Provider 的每次读取也必须执行身份和数据许可检查。

| 资源项目 | 当前宿主边界 |
|---|---|
| 单图编码大小 | 不超过 2 MiB（2,097,152 字节） |
| 输入宽、高 | 每边不超过 8,192 像素，必须可解码为位图 |
| 条目图解码目标 | 至多约 480 × 300，可能进一步采样 |
| 背景图解码目标 | 至多约 1280 × 720，最终由宿主铺屏裁切 |
| 单次图片准备 | 约 2 秒，超时结果不显示 |
| 会话图片持有预算 | 12 MiB；不包含全部瞬时解码和渲染开销，不是 App 可自由占满的配额 |

推荐提供与最终用途相近的 JPEG/PNG 位图；协议不保证动画图片播放、透明背景、HDR、矢量资源或任意图片格式支持。大图主体应留裁切余量，不要将关键文字烘焙到图片边缘。

相同会话内相同背景 URI 被视为同一资源；内容变更应使用新路径，如 `/background/scene-42/v2`，不能用 `?v=2`。不可读、跨包或超限的可选图片不应妨碍合法文字显示；但字段类型错误或非法 URI scheme 会使快照校验失败。所有图片均为尽力展示，不保证每项一定显示。

## 7. 最小公开数据 Service 示例

以下 Java 类可以与第 3 节 Manifest 配合使用，仅返回固定、公开的演示数据。**包名白名单不是私人数据授权方案**；涉及个人数据必须替换为第 10 节要求的用户授权和签名校验。

```java
package com.example.genplugin;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import org.json.*;
import java.util.Arrays;

public final class HomeContentService extends Service {
    private HandlerThread worker;
    private Messenger endpoint;

    @Override public void onCreate() {
        super.onCreate();
        worker = new HandlerThread("home-content");
        worker.start();
        endpoint = new Messenger(new Handler(worker.getLooper(), request -> {
            if (request.what != 1 || request.replyTo == null) return true;
            String[] callers = getPackageManager().getPackagesForUid(request.sendingUid);
            if (callers == null || !Arrays.asList(callers).contains("com.gentv.tv")) {
                return true;
            }
            try {
                if (request.getData().getInt("protocolMajor", 0) != 1) return true;
                JSONObject item = new JSONObject()
                    .put("id", "weather").put("title", "18°")
                    .put("subtitle", "离线演示数据")
                    .put("activity", getPackageName() + "/.MainActivity")
                    .put("actionId", "weather");
                JSONObject snapshot = new JSONObject()
                    .put("protocolMajor", 1).put("snapshotId", "demo-1")
                    .put("template", "summary").put("privacy", "public")
                    .put("ttlSeconds", 30).put("title", "今日天气")
                    .put("items", new JSONArray().put(item));
                Message response = Message.obtain(null, 2);
                response.arg1 = request.arg1;
                Bundle data = new Bundle();
                data.putString("snapshot", snapshot.toString());
                response.setData(data);
                request.replyTo.send(response);
            } catch (JSONException | RemoteException | RuntimeException ignored) {
                // 不记录快照/账号数据；宿主安全超时。实际项目可记录脱敏错误类型。
            }
            return true;
        }));
    }

    @Override public IBinder onBind(Intent intent) { return endpoint.getBinder(); }

    @Override public void onDestroy() {
        worker.quitSafely();
        super.onDestroy();
    }
}
```

该片段不实现账号、网络、Provider 或 INVALIDATE。完整的三模板、失效通知及异常演示见 [ContentService.java](../../launcher/home-content-example/src/main/java/com/gentv/example/ContentService.java)；不要把示例的测试模式搬成正式业务接口。后台任务应有超时/取消机制，不要保存 `Message` 供无限期使用，也不要在主线程执行联网或图片处理。

## 8. 内容点击与 Activity 跳转

`activity` 必须解析为同包、同 UID、启用且 exported 的 Activity；若它要求权限，宿主须已持有。目标不满足条件时，宿主会拒绝快照，不能通过空白/非法动作保留一份看似正常但不可用的内容。

点击后宿主使用显式 `Intent.ACTION_VIEW`，只附带一个协议业务字符串：

```java
String actionId = getIntent().getStringExtra("com.gentv.tv.home.ACTION_ID");
if ("weather".equals(actionId)) {
    // 打开天气详情页；正常图标启动时 actionId 可以为 null。
}
```

若入口使用 `singleTop`、`singleTask` 等复用方式，必须同时在 `onNewIntent()` 中调用 `setIntent(intent)` 并处理新 actionId，避免只在 `onCreate()` 读取一次。显式跳转不需要额外 ACTION_VIEW intent-filter，但目标必须可以被宿主访问。

不得要求宿主传递自定义 extras、Intent flags、URI grant flags、任意深链 URL 或跨包组件。`actionId` 不是可信认证令牌：exported Activity 可以被其他调用方调用，必须做白名单校验、账号检查与必要确认。扣费、删除、开锁等有副作用动作不得仅因收到 actionId 就执行；协议动作应进入 App 自己的页面完成交互。图标上的 OK 始终保留普通启动语义。

## 9. 默认样式、动效与焦点

App 只提供数据和图片，布局、文字样式、裁切、遮罩和动效由 GenTV 控制；GHCP 1 不提供颜色、字体、自定义布局、动画曲线、时长或脚本字段。

| 情况 | 默认表现 |
|---|---|
| 未提供背景 | 内容悬浮在用户当前桌面壁纸上，不替换壁纸，不自动把封面放大成背景 |
| 初次显示/来源切换 | 焦点稳定约 200 ms 后请求；内容约 20 dp 轻移并淡入，入场约 280 ms，退出约 180 ms |
| 可选背景 | 内容先显示，背景独立异步准备，约 700 ms 柔和淡入/淡出；不随内容平移 |
| 背景失败/不提供 | 保留有效文字和用户原背景，不因等图卡住遥控器 |
| 同源刷新 | 当前实现不重播整组入场，尽量按稳定条目 ID 保留操作焦点 |
| 快速切换 | 旧会话作废，迟到数据/图片不允许覆盖新来源；退场内容不可操作 |
| 减少动态效果 | 去掉位移、使用短淡化；系统动画关闭时直接到最终状态 |

这些时长是宿主当前初始参数，可随性能调校，不构成 App 可依赖的计时 API。App 不应自行延迟响应来“配合动效”，也不应预合成带工具栏或第一行图标的大图。

| 遥控操作 | 当前行为 |
|---|---|
| 第一行左右移动 | 切换来源图标和内容；不改变图标普通启动语义 |
| 图标上按上 | 有有效操作时进入首个内容按钮，否则保留正常顶部导航 |
| 内容中左右 | 在有 action 的条目间移动，无 action 的信息项不产生假焦点 |
| 内容中按下/返回 | 回到来源图标，返回键抬起不会再触发一次屏保 |
| 内容中按上 | 到顶部工具栏 |
| 图标上按 OK | 正常打开 App，App 可提供样式/账号/展示设置 |

## 10. 生命周期、隐私与资源责任

- 每份快照在宿主接受展示时建立基于单调时钟的 TTL；不是提供方传入的绝对时间，也不能保证已缓存业务数据仍然新鲜。App 必须自己控制数据新鲜度。
- `ttlSeconds > 2` 时当前宿主会在有效期前尝试前台刷新；App 必须能够重复响应。`ttlSeconds <= 2` 不依赖自动前台刷新。刷新可能失败或被取消，不保证定时到达。
- 有效期结束、撤权、包身份变化、退出桌面、窗口失焦、屏保或菜单打断都会使当前会话失效/隐藏。每次返回桌面都必须能重新获取内容，不依赖宿主保存上次快照。
- `privacy: "personal"` 是内容分类，不是加密、鉴权或防旁观能力。电视是共享屏幕，App 应明确让用户选择哪些个人内容可上桌面，并可随时退出。
- 私人数据提供方必须验证 Android 分配的调用 UID、对应包以及用户信任的签名身份；还要验证账号/数据权限。不能只校验包名、component、token 或 actionId。不要假设所有开源构建都使用官方证书。
- 快照、图片、点击入口执行同一套数据访问策略；注销/撤权时停止新读取、处理在途结果、发送失效通知并清理对应 URI 授权。图片读取应响应取消信号并限制耗时。
- 宿主的协议快照/图片只保留于运行内存，不写入桌面导出或协议内容磁盘缓存。App 自己的缓存仍须遵循自己的隐私策略；不要在日志、崩溃上报、统计事件中记录原始私人快照或可访问私人图片的 URI。
- 宿主采用有界控制/图片执行器和资源预算，容量不足时可以回退为文字或原桌面。App 不得据此申请无限图片、常驻连接或后台高频推送。

## 11. 版本与兼容规则

`PROTOCOL_MAJOR` 元数据、请求 `protocolMajor` 和 JSON `protocolMajor` 均为 `1`。不支持的主版本不得作为 GHCP 1 强行解释。未来不兼容变更必须使用新主版本；附加未知字段被忽略不意味着它们已经被支持。

GHCP 1 不包含通用能力协商、App 获取展示授权状态的查询 API、统一错误码回调、分页、差量 patch、自定义模板、通用网络视频播放或直接设备控制。Service 被绑定不代表快照已经展示，发送成功也不代表渲染成功。App 不应根据是否收到下一次请求判断用户是否阅读了内容。

GHCP 与 [ADB 桌面配置协议](../desktop-protocol.md)、[旧 Module Card 规范](modules.md)相互独立，不得复用后两者的权限或数据字段作为 GHCP 接入前提。

## 12. 联调、验收与问题定位

### 12.1 使用仓库参考项目

环境：JDK 17、Android SDK 34。以下命令在 GenTV 仓库根目录运行：

```sh
./launcher/gradlew -p launcher --no-daemon :home-content-example:assembleDebug :home-content-example:lintDebug
```

示例产物：`launcher/home-content-example/build/outputs/apk/debug/home-content-example-debug.apk`。安装到明确授权的测试设备后，打开示例选择样式，在 GenTV 中允许展示并加入第一行。不要卸载现有 GenTV、清数据或强降级来绕过签名冲突。普通插件 App 可以由开发者自己的 Android 项目独立构建。

本仓库的 `home_content_only` instrumentation 是会写入隔离模拟器测试布局/许可的夹具，**不得在用户真实电视上运行**。真实设备使用正常授权页面和原生布局操作进行验收。既有运行记录见 [2026-11-24 验证](../home-content-validation-2026-11-24.zh-CN.md)，不能把模拟器测试替代目标机型验收。

### 12.2 开发者提交前检查表

- [ ] 元数据在正确启动 Activity 上；主版本类型为整数；服务同包/同 UID 且可绑定。
- [ ] 未授权时不展示；用户可允许/撤销；拒绝授权不妨碍 App 普通启动。
- [ ] 无大图、无封面时可以完整显示有用内容；背景失败时正文不消失。
- [ ] JSON 字节、长度、类型、条目数、TTL、进度和 URI 均符合限制。
- [ ] 六项媒体数据均能通过遥控器到达对应操作，最后一项不会被丢弃。
- [ ] 图标 OK 普通启动；按上进入内容；左右移动；按下/返回回到来源图标。
- [ ] 点击正确业务项；Activity 复用时 onNewIntent 能处理；未知 actionId 安全处理。
- [ ] 快速 A→B→A、菜单打断、退出/返回、空内容、超时、服务重启均安全回退。
- [ ] 私人内容获得独立用户许可；注销/撤权后不再通过快照、图片或点击泄露旧数据。
- [ ] 更换 Service/签名后重新授权；普通更新、包移除和重新安装行为已核对。
- [ ] 实际电视的短屏、大字体、RTL、减少动态效果和内存压力经过检查，未测项目明确记录。
- [ ] 分发说明包含支持的 GHCP 主版本、测试过的宿主/设备、数据用途和问题反馈方式，不把未来能力标成已支持。

### 12.3 常见问题

| 现象 | 优先检查 |
|---|---|
| 展示设置里找不到 App | 元数据是否放在真实启动 Activity；版本是否为 integer；组件是否启用/exported；Service 是否同包及宿主可绑定 |
| 找得到但聚焦不显示 | 用户许可、第一行位置、宿主模式/儿童保护/可用空间、1 秒超时、JSON 类型和动作组件 |
| 升级后需要重新允许 | Service 或当前签名身份是否变化，不要通过清除宿主数据解决 |
| 有文字但无图片 | 自有 URI、读取授权、编码大小/边长、准备超时；summary 本来不显示条目封面 |
| 背景更新后仍是旧图 | 是否复用了同一个 URI；换版本化路径，不能加 query 参数 |
| 点击没有进入正确页面 | activity 是否同包且 exported；actionId 是否白名单匹配；onNewIntent 是否处理 |
| 快照重复发送不更新 | 每请求只接受一份；响应当前请求，必要时使用受限 INVALIDATE |
| 截图没有内容但电视看得到 | 区分厂商截图限制、当前焦点和会话状态；使用实体遥控器核对，不据截图异常自动清数据 |

## 13. 实现核对入口

- [HomeContentRegistry.java](../../launcher/app/src/main/java/com/gentv/tv/HomeContentRegistry.java)：发现、签名及展示许可。
- [HomeContentModel.java](../../launcher/app/src/main/java/com/gentv/tv/HomeContentModel.java)：JSON 类型、长度与数量校验。
- [HomeContentController.java](../../launcher/app/src/main/java/com/gentv/tv/HomeContentController.java)：IPC、生命周期、图片、动作及默认渲染。
- [示例 Manifest](../../launcher/home-content-example/src/main/AndroidManifest.xml)与[完整示例 Service](../../launcher/home-content-example/src/main/java/com/gentv/example/ContentService.java)：独立接入参考。

公开发布协议前，应把本文、参考 App 与对应宿主版本一同标记版本并运行兼容验收；本地预览文档不构成商店收录或兼容认证承诺。
