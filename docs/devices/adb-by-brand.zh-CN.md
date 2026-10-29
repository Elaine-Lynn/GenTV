# 各品牌电视怎么打开 ADB 调试

[English](adb-by-brand.md) · 简体中文

GenTV Remote 和命令行安装都要通过局域网里的 ADB 连接电视。本页汇总了 GenTV [社区机型参考](README.md)里各品牌打开 ADB 的记录，每一条都附上出处机型。同一品牌不同型号、地区和固件版本的菜单可能不一样，所以这些步骤是"在那台机型上可行"，不保证你的电视完全一样。

无论哪个品牌：

- 电视弹出"允许 USB 调试吗？"时选允许，并勾选**始终允许**这台手机或电脑；
- 用工厂菜单或服务菜单时，只打开 ADB，其他选项一概不动；
- 用完把调试关掉。

## 电视打开网络 ADB 的三种情况

| 你看到的 | 怎么做 | 例子 |
|---|---|---|
| **打开 USB 调试就自动开放 5555 端口** | 打开 USB 调试，然后连接 `<电视IP>:5555` | [小米盒子 S](../../phone/framework/data/devices/xiaomi-mi-box-s/debloat.txt)、[海信 A4](../../phone/framework/data/devices/hisense-hismarttv-a4/debloat.txt)、[飞利浦 50PUD7406](../../phone/framework/data/devices/philips-50pud7406/debloat.txt)、[索尼 BRAVIA VU31](../../phone/framework/data/devices/sony-bravia-vu31/debloat.txt) |
| **另有一个"网络调试"开关** | USB 调试和网络调试**都要**打开；有些电视还要重启一次 | [Nvidia Shield TV Pro](../../phone/framework/data/devices/nvidia-shield-tv-pro-2019/debloat.txt)、[极米 MoGo 2](../../phone/framework/data/devices/xgimi-mogo-2/debloat.txt)、[松下 4KTV](../../phone/framework/data/devices/panasonic-4ktv-0su/debloat.txt)、[海信 HG9](../../phone/framework/data/devices/hisense-hg9/debloat.txt) |
| **只有"无线调试"（Android 11 及以上）** | 开发者选项 → 无线调试 → **使用配对码配对设备**，把 IP、端口和配对码输入 GenTV Remote | [Chromecast with Google TV 4K](../../phone/framework/data/devices/google-chromecast-gtv-4k/debloat.txt)、[索尼 BRAVIA 9 II](../../phone/framework/data/devices/sony-bravia-9-ii/debloat.txt)、[飞利浦 Smart 4K Google TV](../../phone/framework/data/devices/philips-smart-4k-google-tv/debloat.txt)、[创维 SWTV 24AE](../../phone/framework/data/devices/skyworth-swtv-24ae-4k-nf/debloat.txt) |

无线调试经常在重启或 Wi-Fi 断开后自己关掉，但配对记录会保留，重新打开再连接就行。有几台电视只有连 **Wi-Fi** 时才能保持无线调试，插网线时会自动关掉（[TCL 65C7K](../../phone/framework/data/devices/tcl-65c7k-google-tv/debloat.txt)、[Chromecast with Google TV 4K](../../phone/framework/data/devices/google-chromecast-gtv-4k/debloat.txt)）。

## 打开开发者选项（标准 Android TV / Google TV）

- **Google TV 和较新的 Android TV：** 设置 → 系统 → 关于 → 在 **Android TV OS 版本号**（build）上连按 7 次确定。注意不是"Android TV OS 版本"，那一项只会显示一个图标。[索尼 BRAVIA 9 II](../../phone/framework/data/devices/sony-bravia-9-ii/debloat.txt)
- **较老的 Android TV：** 设置 → 设备偏好设置 → 关于 → 在**版本号**（Build）上连按 7 次，再到开发者选项里打开 USB 调试。[Freebox Player Pop](../../phone/framework/data/devices/freebox-player-pop/debloat.txt)

## 分品牌

### 小米 / Redmi

- **国行小米、Redmi 电视（PatchWall 桌面）：** 设置 → 关于 → 在**产品型号**上连按 7 次确定，打开开发者模式；再到 设置 → **账号与安全** → **ADB 调试** → **允许**。第一次连接时电视会弹出"允许USB调试"，要确认。[Redmi MFTP0](../../phone/framework/data/devices/redmi-mitv-mftp0/debloat.txt)、[小米 MFTR0](../../phone/framework/data/devices/xiaomi-mitv-mftr0/debloat.txt)
- 这些电视**每次重启都会关掉网络 ADB**（待机不算重启），每次开机后要到"ADB 调试"里重新打开。[小米 MFFU0](../../phone/framework/data/devices/xiaomi-mitv-mffu0/debloat.txt)、[小米 ASTP0](../../phone/framework/data/devices/xiaomi-mitv-astp0/debloat.txt)、[小米 MTEQ0](../../phone/framework/data/devices/xiaomi-mitv-mteq0/debloat.txt)
- **小米盒子和海外版 Google TV 机型：** 走标准开发者选项，打开 USB 调试就会开放 5555 端口。[小米盒子 3](../../phone/framework/data/devices/xiaomi-mi-box-3/debloat.txt)、[小米 AXSO2](../../phone/framework/data/devices/xiaomi-mitv-axso2/debloat.txt)。小米盒子 S 打开 USB 调试后 USB 口会断电，接在上面的音箱或 U 盘会用不了。[小米盒子 S](../../phone/framework/data/devices/xiaomi-mi-box-s/debloat.txt)

### TCL / 雷鸟

- **国行 TCL 和雷鸟：** 设置 → 系统 → **本机信息**，然后在遥控器上按**上、下、左、右**，重复 2–3 遍，直到页面底部出现 ADB 开关。打开后重启也不会关。[雷鸟 R34A0](../../phone/framework/data/devices/ffalcon-r34a0-cn/debloat.txt)、[TCL Q10G Pro](../../phone/framework/data/devices/tcl-q10g-pro/debloat.txt)
- 其中有几款固件即使 ADB 能连，也**禁止通过 ADB 安装应用**，这些电视装不了 GenTV。[雷鸟 R34A0](../../phone/framework/data/devices/ffalcon-r34a0-cn/debloat.txt)、[雷鸟 65S585C Pro](../../phone/framework/data/devices/ffalcon-65s585c-pro/debloat.txt)、[TCL Q10G Pro](../../phone/framework/data/devices/tcl-q10g-pro/debloat.txt)
- 有一款国行 Android 14 固件完全没有开发者选项和无线调试界面，记录里是用 USB 线连电脑才打开的。[TCL MT9655 国行](../../phone/framework/data/devices/tcl-mt9655-cn-android14/debloat.txt)
- **海外版 TCL Google TV：** 走标准开发者选项。看机型，有的打开 USB 调试就开放 5555 端口（[TCL C89B](../../phone/framework/data/devices/tcl-c89b-google-tv/debloat.txt)），有的需要无线调试（[TCL 65C7L](../../phone/framework/data/devices/tcl-65c7l-google-tv/debloat.txt)、[TCL 65C7K](../../phone/framework/data/devices/tcl-65c7k-google-tv/debloat.txt)）。
- **TCL 50DP660：** 用遥控器进工厂菜单：设置 → 图像 → 高级设置 → 亮度，把焦点停在"对比度"上，输入 **1 9 5 0**，然后 其他 → ADB。开发者选项里的 USB 调试也要打开。[TCL 50DP660](../../phone/framework/data/devices/tcl-50dp660/debloat.txt)

### 索尼 BRAVIA

- 开发者选项：设置 → 系统 → 关于 → **Android TV OS 版本号**（build）。[索尼 BRAVIA 9 II](../../phone/framework/data/devices/sony-bravia-9-ii/debloat.txt)
- 有些固件里的开关叫 **ADB 调试**，不叫 USB 调试。[BRAVIA 4K GB](../../phone/framework/data/devices/sony-bravia-4k-gb-us/debloat.txt)、[KD-65X82L](../../phone/framework/data/devices/sony-kd-65x82l/debloat.txt)
- Android 9 的机型打开 USB 调试就开放 5555 端口（[XBR-65X900F](../../phone/framework/data/devices/sony-xbr-65x900f/debloat.txt)）；较新的机型需要无线调试配对，而且重启后 5555 端口会关闭（[BRAVIA 9 II](../../phone/framework/data/devices/sony-bravia-9-ii/debloat.txt)、[BRAVIA AE2](../../phone/framework/data/devices/sony-bravia-ae2/debloat.txt)）。

### 海信 / Vidda

- 记录中的大多数机型：打开 USB 调试就直接开放 5555 端口，不用配对。[海信 A4](../../phone/framework/data/devices/hisense-hismarttv-a4/debloat.txt)、[海信 4K FFM](../../phone/framework/data/devices/hisense-smarttv-4k-ffm/debloat.txt)、[Vidda MT9653](../../phone/framework/data/devices/vidda-mt9653/debloat.txt)
- 海信 HG9 还要打开网络/无线调试开关并重启一次。[海信 HG9](../../phone/framework/data/devices/hisense-hg9/debloat.txt)
- **安全提醒：** 部分海信和 Vidda 机型从不弹出"允许 USB 调试吗？"，也就是说 ADB 开着的时候，局域网里任何设备都能连上。用完请马上关掉。[海信 65D62QH](../../phone/framework/data/devices/hisense-65d62qh/debloat.txt)、[Vidda MT9653](../../phone/framework/data/devices/vidda-mt9653/debloat.txt)

### 创维 / 酷开

- **酷开 K3 Pro：** 开发者选项被隐藏，ADB 要从酷开工厂菜单里打开，每次重启都会复位。[酷开 K3 Pro](../../phone/framework/data/devices/coocaa-k3-pro-90p3d-max/debloat.txt)
- **创维 8R40（K5C）：** 唯一的 ADB 开关在 设置 → **基本设置** → **设备信息** → **更多信息**。不要停用 `com.tianci.de` 和 `com.skyworthdigital.stb.dataprovider`，否则这个页面会崩溃，ADB 就只能靠恢复出厂设置才能重新打开。[创维 8R40 K5C](../../phone/framework/data/devices/skyworth-8r40-k5c/debloat.txt)
- **创维 65A3F：** 5555 端口开放时不需要任何授权，用完请关掉 ADB。[创维 65A3F](../../phone/framework/data/devices/skyworth-65a3f/debloat.txt)
- **海外版创维 Google TV（SWTV 24AE / 24NA）：** 只有无线调试，重启或 Wi-Fi 断开后会自己关掉。[SWTV 24AE](../../phone/framework/data/devices/skyworth-swtv-24ae-4k-nf/debloat.txt)、[SWTV 24NA](../../phone/framework/data/devices/skyworth-swtv-24na-4k-nf/debloat.txt)

### 飞利浦

- 打开 USB 调试就开放 5555 端口。[飞利浦 50PUD7406](../../phone/framework/data/devices/philips-50pud7406/debloat.txt)、[飞利浦 55PUS7503](../../phone/framework/data/devices/philips-55pus7503/debloat.txt)
- 如果不弹授权提示：撤销 USB 调试授权，关掉再打开调试，再把电视断电重启。[飞利浦 65PUS8102](../../phone/framework/data/devices/philips-65pus8102/debloat.txt)
- 飞利浦 Google TV 机型可能需要无线调试配对。[飞利浦 Smart 4K Google TV](../../phone/framework/data/devices/philips-smart-4k-google-tv/debloat.txt)

### Google（Chromecast with Google TV、Google TV Streamer）

只能用无线调试配对，配对前 5555 端口一直关着。配对时用 Wi-Fi，不要插网线；重启后要重新打开无线调试。[Chromecast with Google TV 4K](../../phone/framework/data/devices/google-chromecast-gtv-4k/debloat.txt)、[Chromecast HD](../../phone/framework/data/devices/google-chromecast-gtv-hd/debloat.txt)、[Google TV Streamer](../../phone/framework/data/devices/google-tv-streamer-4k-kirkwood/debloat.txt)

### Nvidia Shield

开发者选项 → **网络调试**，打开后开放 5555 端口。[Shield TV 2019](../../phone/framework/data/devices/nvidia-shield-tv-2019/debloat.txt)、[Shield TV Pro 2019](../../phone/framework/data/devices/nvidia-shield-tv-pro-2019/debloat.txt)

### 极米投影仪

- MoGo 2：USB 调试和网络调试**都要**打开。[极米 MoGo 2](../../phone/framework/data/devices/xgimi-mogo-2/debloat.txt)
- MoGo 3 Pro：用无线调试配对，投影仪重启后会自己关掉。[极米 MoGo 3 Pro](../../phone/framework/data/devices/xgimi-mogo-3-pro/debloat.txt)

### 亚马逊 Fire TV

设置 → My Fire TV → 关于 → 在设备名称上连按 7 次确定，再到开发者选项 → **ADB 调试**。端口 5555，不用配对。Fire OS 上的机型参考只用于清理：亚马逊自带桌面受保护，始终占着主屏幕，所以 GenTV 替换不了它。[Fire TV Stick 4K（2023）](../../phone/framework/data/devices/amazon-fire-tv-stick-4k-2023/debloat.txt)、[Fire TV Stick 4K Max（2021）](../../phone/framework/data/devices/amazon-fire-tv-stick-4k-max-2021/debloat.txt)

### 其他品牌

- **海尔：** 打开 USB 调试就开放 5555 端口，会弹授权提示。[海尔 H32S80EFX](../../phone/framework/data/devices/haier-h32s80efx/debloat.txt)
- **一加电视：** 打开 USB 调试就开放 5555 端口，重启后仍然开着。[一加电视 Cebu](../../phone/framework/data/devices/oneplus-tv-cebu/debloat.txt)
- **松下：** 打开单独的网络调试开关，再重启一次。[松下 4KTV](../../phone/framework/data/devices/panasonic-4ktv-0su/debloat.txt)
- **其他品牌：** 到[机型索引](README.md)里找你的型号。

## 找不到 ADB 开关？

有些固件隐藏或去掉了 ADB，或者允许 ADB 连接却禁止安装应用，这些电视装不了 GenTV。如果你在自己的机型上找到了可行的办法，欢迎提交 [Issue](https://github.com/Elaine-Lynn/GenTV/issues)，写明型号、固件版本和准确的菜单路径。不要贴出电视的 IP 地址、配对码或 ADB 密钥。
