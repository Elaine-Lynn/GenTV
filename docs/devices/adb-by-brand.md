# Turn on ADB debugging, by TV brand

English · [简体中文](adb-by-brand.zh-CN.md)

GenTV Remote and the command-line install both talk to the TV over ADB on your local network. This page collects how ADB was turned on for each brand in GenTV's [community device references](README.md). Every step links to the model note it comes from. Menus change between models, regions and firmware updates, so treat a step as "what worked on that model", not a guarantee for yours.

Always:

- accept the **Allow USB debugging?** prompt on the TV and tick **Always allow** for your phone or computer;
- use a factory or service menu only to switch ADB on, and change nothing else there;
- turn debugging off again when you have finished.

## Three ways TVs open network ADB

| What you see | What to do | Example models |
|---|---|---|
| **USB debugging opens port 5555 by itself** | Turn on USB debugging, then connect to `<TV IP>:5555` | [Xiaomi Mi Box S](../../phone/framework/data/devices/xiaomi-mi-box-s/debloat.txt), [Hisense A4](../../phone/framework/data/devices/hisense-hismarttv-a4/debloat.txt), [Philips 50PUD7406](../../phone/framework/data/devices/philips-50pud7406/debloat.txt), [Sony BRAVIA VU31](../../phone/framework/data/devices/sony-bravia-vu31/debloat.txt) |
| **A separate Network debugging switch** | Turn on USB debugging **and** Network debugging; some TVs also need a restart | [Nvidia Shield TV Pro](../../phone/framework/data/devices/nvidia-shield-tv-pro-2019/debloat.txt), [Xgimi MoGo 2](../../phone/framework/data/devices/xgimi-mogo-2/debloat.txt), [Panasonic 4KTV](../../phone/framework/data/devices/panasonic-4ktv-0su/debloat.txt), [Hisense HG9](../../phone/framework/data/devices/hisense-hg9/debloat.txt) |
| **Only Wireless debugging (Android 11+)** | Developer options → Wireless debugging → **Pair device with pairing code**, and enter the IP, port and code in GenTV Remote | [Chromecast with Google TV 4K](../../phone/framework/data/devices/google-chromecast-gtv-4k/debloat.txt), [Sony BRAVIA 9 II](../../phone/framework/data/devices/sony-bravia-9-ii/debloat.txt), [Philips Smart 4K Google TV](../../phone/framework/data/devices/philips-smart-4k-google-tv/debloat.txt), [Skyworth SWTV 24AE](../../phone/framework/data/devices/skyworth-swtv-24ae-4k-nf/debloat.txt) |

Wireless debugging often switches itself off after a reboot or when Wi-Fi drops; the pairing is kept, so turn it back on and reconnect. On several sets it only stays on over **Wi-Fi**, not Ethernet ([TCL 65C7K](../../phone/framework/data/devices/tcl-65c7k-google-tv/debloat.txt), [Chromecast with Google TV 4K](../../phone/framework/data/devices/google-chromecast-gtv-4k/debloat.txt)).

## Unlock Developer options (standard Android TV / Google TV)

- **Google TV and recent Android TV:** Settings → System → About → press OK 7 times on **Android TV OS build** (not "Android TV OS version", which only shows a logo). [Sony BRAVIA 9 II](../../phone/framework/data/devices/sony-bravia-9-ii/debloat.txt)
- **Older Android TV:** Settings → Device Preferences → About → press OK 7 times on **Build**, then Developer options → USB debugging. [Freebox Player Pop](../../phone/framework/data/devices/freebox-player-pop/debloat.txt)

## By brand

### Xiaomi / Redmi

- **China-market Xiaomi and Redmi TVs (PatchWall):** Settings → About → press OK 7 times on **产品型号** (product model) to unlock developer mode, then Settings → **账号与安全** (Account & Security) → **ADB 调试** → **允许**. The first connection needs the on-screen "允许USB调试" confirmation. [Redmi MFTP0](../../phone/framework/data/devices/redmi-mitv-mftp0/debloat.txt), [Xiaomi MFTR0](../../phone/framework/data/devices/xiaomi-mitv-mftr0/debloat.txt)
- Network ADB **closes on every reboot** on these sets (standby is not a reboot); switch ADB 调试 on again after each power cycle. [Xiaomi MFFU0](../../phone/framework/data/devices/xiaomi-mitv-mffu0/debloat.txt), [Xiaomi ASTP0](../../phone/framework/data/devices/xiaomi-mitv-astp0/debloat.txt), [Xiaomi MTEQ0](../../phone/framework/data/devices/xiaomi-mitv-mteq0/debloat.txt)
- **Xiaomi Mi Box and international Google TV models:** standard Developer options; USB debugging alone opens port 5555. [Mi Box 3](../../phone/framework/data/devices/xiaomi-mi-box-3/debloat.txt), [Xiaomi AXSO2](../../phone/framework/data/devices/xiaomi-mitv-axso2/debloat.txt). On the Mi Box S, USB debugging cuts power to the USB port, so a USB speaker or drive stops working while it is on. [Mi Box S](../../phone/framework/data/devices/xiaomi-mi-box-s/debloat.txt)

### TCL / FFalcon (雷鸟)

- **China-market TCL and FFalcon:** Settings → System → **本机信息** (System Info / Device info), then press **Up Down Left Right** on the remote, repeating 2–3 times, until an ADB switch appears at the bottom. It stays on across reboots. [FFalcon R34A0](../../phone/framework/data/devices/ffalcon-r34a0-cn/debloat.txt), [TCL Q10G Pro](../../phone/framework/data/devices/tcl-q10g-pro/debloat.txt)
- Several of these firmwares **block installing apps over ADB** even after ADB works, so GenTV can't be installed there. [FFalcon R34A0](../../phone/framework/data/devices/ffalcon-r34a0-cn/debloat.txt), [FFalcon 65S585C Pro](../../phone/framework/data/devices/ffalcon-65s585c-pro/debloat.txt), [TCL Q10G Pro](../../phone/framework/data/devices/tcl-q10g-pro/debloat.txt)
- One China-market Android 14 build has no Developer options or Wireless debugging screen at all; the recorded way in was a USB cable to a computer. [TCL MT9655 CN](../../phone/framework/data/devices/tcl-mt9655-cn-android14/debloat.txt)
- **TCL Google TV (international):** standard Developer options. Depending on the model, USB debugging opens port 5555 ([TCL C89B](../../phone/framework/data/devices/tcl-c89b-google-tv/debloat.txt)) or Wireless debugging is needed ([TCL 65C7L](../../phone/framework/data/devices/tcl-65c7l-google-tv/debloat.txt), [TCL 65C7K](../../phone/framework/data/devices/tcl-65c7k-google-tv/debloat.txt)).
- **TCL 50DP660:** factory menu from the remote: Settings → Picture → Advanced Settings → Brightness, highlight Contrast, type **1 9 5 0**, then Other → ADB. USB debugging in Developer options must be on as well. [TCL 50DP660](../../phone/framework/data/devices/tcl-50dp660/debloat.txt)

### Sony BRAVIA

- Developer options: Settings → System → About → **Android TV OS build**. [Sony BRAVIA 9 II](../../phone/framework/data/devices/sony-bravia-9-ii/debloat.txt)
- Some firmware labels the switch **ADB debugging** instead of USB debugging. [BRAVIA 4K GB](../../phone/framework/data/devices/sony-bravia-4k-gb-us/debloat.txt), [KD-65X82L](../../phone/framework/data/devices/sony-kd-65x82l/debloat.txt)
- Android 9 sets open port 5555 with USB debugging ([XBR-65X900F](../../phone/framework/data/devices/sony-xbr-65x900f/debloat.txt)); newer sets need Wireless debugging pairing, and port 5555 closes after a reboot ([BRAVIA 9 II](../../phone/framework/data/devices/sony-bravia-9-ii/debloat.txt), [BRAVIA AE2](../../phone/framework/data/devices/sony-bravia-ae2/debloat.txt)).

### Hisense / Vidda

- Most recorded sets: USB debugging opens port 5555 directly, no pairing. [Hisense A4](../../phone/framework/data/devices/hisense-hismarttv-a4/debloat.txt), [Hisense 4K FFM](../../phone/framework/data/devices/hisense-smarttv-4k-ffm/debloat.txt), [Vidda MT9653](../../phone/framework/data/devices/vidda-mt9653/debloat.txt)
- Hisense HG9 also needs its Network/Wireless debugging switch and a restart. [Hisense HG9](../../phone/framework/data/devices/hisense-hg9/debloat.txt)
- **Security:** on some Hisense and Vidda sets the TV never asks "Allow USB debugging?", so any device on your network can connect while ADB is on. Turn it off as soon as you're done. [Hisense 65D62QH](../../phone/framework/data/devices/hisense-65d62qh/debloat.txt), [Vidda MT9653](../../phone/framework/data/devices/vidda-mt9653/debloat.txt)

### Skyworth / Coocaa (创维 / 酷开)

- **Coocaa K3 Pro:** Developer options are hidden; ADB is switched on from the Coocaa factory menu and resets on every reboot. [Coocaa K3 Pro](../../phone/framework/data/devices/coocaa-k3-pro-90p3d-max/debloat.txt)
- **Skyworth 8R40 (K5C):** the only ADB switch is under Settings → **基本设置** → **设备信息** → **更多信息**. Don't disable `com.tianci.de` or `com.skyworthdigital.stb.dataprovider`: that page then crashes and ADB can only come back with a factory reset. [Skyworth 8R40 K5C](../../phone/framework/data/devices/skyworth-8r40-k5c/debloat.txt)
- **Skyworth 65A3F:** port 5555 opens without any authorisation prompt; turn ADB off when done. [Skyworth 65A3F](../../phone/framework/data/devices/skyworth-65a3f/debloat.txt)
- **International Skyworth Google TV (SWTV 24AE / 24NA):** only Wireless debugging; it switches off after reboots or Wi-Fi drops. [SWTV 24AE](../../phone/framework/data/devices/skyworth-swtv-24ae-4k-nf/debloat.txt), [SWTV 24NA](../../phone/framework/data/devices/skyworth-swtv-24na-4k-nf/debloat.txt)

### Philips

- USB debugging alone opens port 5555. [Philips 50PUD7406](../../phone/framework/data/devices/philips-50pud7406/debloat.txt), [Philips 55PUS7503](../../phone/framework/data/devices/philips-55pus7503/debloat.txt)
- If the authorisation prompt doesn't appear: revoke USB debugging authorisations, switch debugging off and on, and power-cycle the TV. [Philips 65PUS8102](../../phone/framework/data/devices/philips-65pus8102/debloat.txt)
- Philips Google TV sets may need Wireless debugging pairing. [Philips Smart 4K Google TV](../../phone/framework/data/devices/philips-smart-4k-google-tv/debloat.txt)

### Google (Chromecast with Google TV, Google TV Streamer)

Wireless debugging pairing only; port 5555 stays closed until paired. Use Wi-Fi, not Ethernet, for pairing, and switch Wireless debugging back on after a reboot. [Chromecast with Google TV 4K](../../phone/framework/data/devices/google-chromecast-gtv-4k/debloat.txt), [Chromecast HD](../../phone/framework/data/devices/google-chromecast-gtv-hd/debloat.txt), [Google TV Streamer](../../phone/framework/data/devices/google-tv-streamer-4k-kirkwood/debloat.txt)

### Nvidia Shield

Developer options → **Network debugging** opens port 5555. [Shield TV 2019](../../phone/framework/data/devices/nvidia-shield-tv-2019/debloat.txt), [Shield TV Pro 2019](../../phone/framework/data/devices/nvidia-shield-tv-pro-2019/debloat.txt)

### Xgimi projectors

- MoGo 2: turn on **both** USB debugging and Network debugging. [Xgimi MoGo 2](../../phone/framework/data/devices/xgimi-mogo-2/debloat.txt)
- MoGo 3 Pro: Wireless debugging pairing; it switches off when the projector restarts. [Xgimi MoGo 3 Pro](../../phone/framework/data/devices/xgimi-mogo-3-pro/debloat.txt)

### Amazon Fire TV

Settings → My Fire TV → About → press select 7 times on the device name, then Developer options → **ADB debugging**. Port 5555, no pairing. On Fire OS the device references are for cleanup only: the Amazon home screen is protected and keeps Home, so GenTV can't replace it. [Fire TV Stick 4K (2023)](../../phone/framework/data/devices/amazon-fire-tv-stick-4k-2023/debloat.txt), [Fire TV Stick 4K Max (2021)](../../phone/framework/data/devices/amazon-fire-tv-stick-4k-max-2021/debloat.txt)

### Others

- **Haier:** USB debugging opens port 5555 with an authorisation prompt. [Haier H32S80EFX](../../phone/framework/data/devices/haier-h32s80efx/debloat.txt)
- **OnePlus TV:** USB debugging opens port 5555 and stays on across reboots. [OnePlus TV Cebu](../../phone/framework/data/devices/oneplus-tv-cebu/debloat.txt)
- **Panasonic:** the separate network-debugging switch plus a restart. [Panasonic 4KTV](../../phone/framework/data/devices/panasonic-4ktv-0su/debloat.txt)
- **Any other brand:** look up your model in the [device index](README.md).

## Can't find ADB on your TV?

Some firmware hides or removes it, or allows ADB but blocks installing apps. GenTV can't be installed on those TVs. If you found a way that works on your model, open an [issue](https://github.com/Elaine-Lynn/GenTV/issues) with the model, firmware version and exact menu path, but never post your TV's IP address, pairing code or ADB keys.
