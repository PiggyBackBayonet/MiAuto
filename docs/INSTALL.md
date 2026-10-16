# 安装 MiAuto 0.1.1

把 APK 装到**车机的 Android 系统上**，不是装在手机上。
已验证设备：Newland NLS-MT9055-GL（Android 11 / arm64）。比亚迪及其他车机**尚未验证**。
手机需要支持 ICCOA CarLink —— 小米 CarWith、OPPO Car+ 车联、vivo Jovi InCar，或设置里有对应的投屏入口；
手机侧不需要安装任何 MiAuto 配套应用，也不需要转接盒。

## 下载

使用[下载页](https://piggybackbayonet.github.io/MiAuto/zh-Hans/)或
[GitHub Release](https://github.com/PiggyBackBayonet/MiAuto/releases/tag/v0.1.1)。
下载 `MiAuto-0.1.1.apk`。校验值随 APK 一并提供。

## 安装与连接

1. 停车。使用你的车机固件所允许的方式安装 APK。若用浏览器 / 文件管理器安装，
   请在提示时允许该应用「安装未知来源应用」。若车机不提供 APK 安装入口，
   仅在固件允许的前提下改用 ADB。
2. 打开 MiAuto。若系统请求蓝牙、WiFi / 位置、麦克风、通知与悬浮窗权限，请依次授予。
3. 保持手机的**蓝牙与 WiFi 开启**，在手机上打开 CarWith、Car+ 车联或 Jovi InCar
   （或在系统设置里找到对应的投屏入口），在附近设备列表中选择本车机。
4. 在手机上确认 6 位配对码。手机画面随后出现在车机上。

车机侧**无需按任何按钮**：待连状态下首页会持续显示配对码并自动广播。

## 找不到车机怎么办

- 确认手机蓝牙与 WiFi 都开着，两块屏幕都亮着。
- 确认 MiAuto 首页显示的是**待连接**状态（有 6 位配对码），而不是已经连上别的设备。
- 车机在未连接时**每 30 秒重新广播一次**，所以手机上被关掉的配对弹窗会自己回来 ——
  不必重启应用。首页也提供「刷新配对码」按钮做即时重试。
- 若仍不行，请重启手机的蓝牙与 WiFi 后重试；部分手机在省电模式下会限制后台扫描。

## 可选的 ADB 安装

在电脑上安装 Android platform-tools，用你的车机支持的方式开启 ADB，并让电脑与车机处于同一可信网络。
把下面的 `CAR_IP` 换成车机当前 IP；本项目不假设任何固定地址。

```sh
adb connect CAR_IP:5555
adb -s CAR_IP:5555 install -r MiAuto-0.1.1.apk
```

仓库内的安装脚本还会顺带授予 MiAuto 所需的运行时权限、打开定位、放行悬浮窗，
并把应用加入电池优化白名单：

```sh
./scripts/install.sh CAR_IP:5555 /absolute/path/to/MiAuto-0.1.1.apk
```

装的是 debug 构建时，包名是 `com.miauto.app.debug`，用 `PKG=` 覆盖：

```sh
PKG=com.miauto.app.debug ./scripts/install.sh CAR_IP:5555 MiAuto-0.1.1-debug.apk
```

## 升级与签名

MiAuto 使用**自己的**包名（`com.miauto.app`）和**自己的**发布签名密钥，与它所基于的项目相互独立。
本仓库发布的版本由同一把密钥签名，因此后续公开版本可以原地覆盖升级。

自行构建的 APK 若用的是另一把密钥，Android 会拒绝覆盖安装。此时请先从既有应用导出设置，
卸载它，再安装新包；卸载会清除本地设置，可能需要重新配对。

## 已知边界

- **未在比亚迪或其他量产车机上验证过。** 本版本的真机验证是在一台 Android 11 的 arm64 测试设备上完成的。
- **仪表盘镜像与挡风玻璃 HUD 导航尚未与 MiAuto 的协议栈一起验证。** 这两条链路继承自基座，
  且默认关闭；若车机未向应用开放仪表区域，设置项会报告「功能不可用」。
- **32 位车机（`armeabi-v7a`，即 `uname -m` 显示 `armv7l`）可以安装 —— 但有一个功能缺口。**
  APK 是四 ABI 通用包，不会因 ABI 被拒装。不过内置的 FFmpeg 软件解码只有 arm64 版，
  32 位上设置里的「内置 FFmpeg」会如实显示为不可用，H.265 只能依赖车机自己的 MediaCodec 硬解。
- **系统版本不能太低。** ICCOA 链路靠 **BLE 广播**让手机发现车机，而 BLE 广播 API 从
  **Android 5.0（API 21）** 才存在；manifest 虽然允许装到 Android 4.1，但 4.4 及以下的车机
  装上也连不上。
- 手机端需支持 ICCOA CarLink（小米 CarWith / OPPO Car+ 车联 / vivo Jovi InCar 等）；
  这不是 Android Auto，也不是 Apple CarPlay。

## 反馈问题

请在 [Issues](https://github.com/PiggyBackBayonet/MiAuto/issues) 中提交，并附上：车机型号与固件、
手机型号与 Android 版本、MiAuto 版本、连接方式，以及可复现的步骤。
公开发布日志或截图前，请先抹去位置信息、设备标识与账号信息。
