# MiAuto 可行性研究报告

> 目标：把 DiAuto Pro（Android Auto 车机接收端）改造成 **MiAuto** —— 一个能让**小米 CarWith / OPPO Car+ 车联 / vivo Jovi InCar（三者同属 ICCOA CarLink）**投屏上车的车机端应用。
>
> **术语澄清（2026-10-13）**：**ICCOA CarLink 是小米、OPPO、vivo 三家联合发起的标准**（联盟 2021-09-28 成立）；**CarWith（小米 / Redmi）/ OPPO Car+ 车联 / vivo Jovi InCar（vivo / iQOO）是同一协议的三个手机侧前端**，车机侧看到的是同一套广告与配对流程。因此本报告里凡是出现「小米 CarWith」的地方，都应理解为「ICCOA CarLink 手机端」的一个实例 —— 报告之所以通篇以 CarWith 取证，只是因为**当时的实测样本就是小米手机**，而不是协议只服务小米。品牌大小写按官方写法：`vivo` 全小写、`OPPO` 全大写。
>
> 调研日期：2026-10-02　|　结论可信度：高（协议层已找到官方实现 + 真实样本实测通过）
>
> **修订记录**：
> - 2026-10-02 晚 —— 依据真实样本 `智慧车联_v1.5.3-支持方控.apk` 的逆向结果，**技术路线由 CarLife 改为 ICCOA CarLink**。详见 [`APK_ANALYSIS_ZHL-v1.5.3.md`](./APK_ANALYSIS_ZHL-v1.5.3.md)。
> - 2026-10-03 —— **POC-1 编译与打包打通**：SDK 改由 **smali 往返**提取，最小工程 `repos/MiAuto/poc1/` 产出可安装 APK（7.10 MB）。§9.2 / §9.4 / §9.5 按实测重写，三处 API 推测被证伪（无 `stopCast()`、`setCarBrMac(byte[])`、`onInitFailed(int)`）。真机验证待做。
> - 2026-10-03 晚 —— **真机反馈修复 + 逻辑对齐样本**。用户实测报告两点：①「点启动按钮就闪退」②「样本打开时显示一个链接码，你的 DEMO 怎么不是这个逻辑？」。逐条修复：**坑 4**（跨 dex 漏 86 个 Tink 类 + 3 个 `easy.logger` 类 → `init()` 时 `NoClassDefFoundError`）已补齐，dex 4,232 → **4,740 类**；**存根字段 `static` 丢失**（§9.4.1.1）已修，`WorkMode.*` 静态常量可正常引用；启动改为**打开即自动初始化**、`onPinCode` 参数语义按 smali 铁证修正为 `(pinCode, carDeviceName)`、`onSelectWorkMode` 改为挑单一模式 —— 三项对齐见 **§9.4.3**。
> - 2026-10-03 深夜 —— **MiAuto 主工程从 poc1 派生完成并本地编译出完整可装 APK**：包名 `com.miauto.app`；`IccoaManager` 用 **init-once 状态机**根治用户报的「停止→启动不弹链接码」（SDK 的 `init()` 进程一次性，重启只 `reAdvertise()`，绝不重复 init；修复同步落到 poc1）；`app/build.gradle.kts` 内建 `injectIccoaDex` 任务（纯 `java.util.zip`，取代 `tools/package-poc.sh`）把 SDK dex 追加为 `classes4.dex` + `zipalign -p -f 4` + `apksigner` 重签；`NavigationCluster` 作为 **POC-2 接入位**已接 `onNavigationInfoReceived`（屏上浮层显示）。主工程 APK **7,271,616 B** / SHA-256 `ff4e442a…`。详见 **§9.2.1.1**。
> - 2026-10-03（午）—— **真机复验通过 + POC-2 导航卡 v2 + 应用图标**。用户实测主工程「**断开重连一切正常**」。`NavigationCluster` 重构为**车机仪表级导航卡**（方向图标 / 距离 / 操作 / 路名 / 目的地 / 导航状态 + HUD 镜像）；**★ `NavigationInfo.getDirectionIcon()` 返回 `byte[]`**（`javap` 从 stub 确认）→ 采用**位图解码优先 + 文本箭头回退**的防御式渲染 + 字节诊断日志。新增 **launcher 图标**（「MA」logo，A 底对齐 M 底；自适应图标 + 各密度）。构建通过、签名有效，新 APK **7,785,091 B** / SHA-256 `32277c531c375d4b7f8d46003ca36355f20ab53c87d30acb3ba8e4e7a8b4bb53`。详见 **§9.2.1.2**。
> - 2026-10-03（下午）—— **应用图标换版 + 毛玻璃底**。用户提供新 logo `branding/MA.png`（宝蓝底 `(63,72,204)` + 橙白「MA」字标）并要求**给蓝底加毛玻璃效果**。做法：从纯平底色软边抠出字形作 alpha 遮罩 → 按「乳白化 + 斜向柔光 + 暗角 + 高光 + 多尺度霜面颗粒（低频云+中频起伏+逐像素细粒）」重建毛玻璃底 → 字形柔光 bloom → 合成母图。自适应图标改为 **`ic_launcher_background`（毛玻璃位图）+ `ic_launcher_foreground`（字形按 0.45×108dp 居中）** 分层；5 密度方形/圆形/前景/背景共 22 个条目。新 APK **7,998,544 B** / SHA-256 `05ee4e789daa44a0ee8f1ef000756711eb75d22f5fbbbbdd3a520a71068ded00`。详见 **§9.2.1.2**。
> - 2026-10-03（下午·续）—— **车机显示名改为 `MiAutoCar`**。用户要求改掉手机端链接码下方显示的「ICCOA汽车」。根因（smali 铁证）：`com.share.connect.Device.getPName()` / `getShortName()` **无条件硬编码 `return "ICCOA汽车"`**（不读字段，`setPName` 形同虚设）；SDK 内**唯一调用者**是 `UCarConnectProxy.readCarName()`，其结果经 Intent `car_name`/`car_short_name` 交给 `ShareLinkService` → 下发手机显示。解法：baksmali 反汇编 → patch `Device.smali` 两处常量 → `tools/smali/smali.jar` 重汇编 → 新 dex（**7,084,144 B** / SHA-256 `e5236eb6…`）→ 部署 MiAuto + poc1 → 重建 APK（**7,998,544 B** / SHA-256 `f10fe5be…`）。**往返无损已验证**（4740 类反汇编回来 `diff -rq` 零差异）。详见 **§9.2.1.3**。
> - 2026-10-03（夜·架构重建）—— **MiAuto 由「poc1 派生的极简工程」改为「以 DiAuto 为基座重建」**。起因：用户发现 MiAuto 里**没有设置页**，DiAuto 那套「常规 + 高级」设置（`仪表投屏` / `投屏时机` / `HUD 未识别不显示`）全都不存在。根因：MiAuto 是从 POC-1 派生的最小 Java 工程（仅 4 个类 + 1 个布局 + 275 行 MainActivity）。决策与执行：把 `repos/DiAuto` **完整派生**为 MiAuto 基座 → 品牌化（`applicationId=com.miauto.app`、名称 MiAuto、MA 毛玻璃图标、独立密钥 `miauto-release-key.jks`）→ 保留 DiAuto 全部 UI 与 **13 分类设置体系** → 移植 ICCOA SDK（`compileOnly` stubs + `injectIccoaDex` 把 SDK dex 追加为 **`classes4.dex`**）。**DiAuto 原工程原样保留在 `repos/DiAuto`**，旧 poc 版 MiAuto 归档在 `repos/MiAuto-poc-legacy`。详见 **§9.6**。产物：`com.miauto.app.debug_0.1.0-debug_debug.apk`，**20,738,640 B** / SHA-256 `1c060f38463e3f6089edeaab08c13cc3c6d2824b298535f39ef3c49cd5ff727a`。
> - 2026-10-03（深夜·协议接线 + 真机「连不上」根因）—— 用户实测反馈两点：①「**装上就是 DiAuto，没有任何不同，也没有链接码**」②「**手机不弹窗连接、点开始配对无效**」。逐条定位并修复**四个根因**（详见 **§9.7**）：**(a)** ICCOA SDK 全工程**零调用**（`IccoaManager` 无人 new，SDK 装进去却从未启动）→ 新增 `connection/IcocaBridge.kt` 桥接 + `MainActivity` 启动广播；**(b)** 首页文案仍是 `DiAuto`/`Android Auto` → 5 语言包重写为 MiAuto + ICCOA 语义，并跳过 AA 设置向导；**(c)** ★ **SDK 的清单组件与权限随清单合并一起丢失** —— 4 个 `com.share.connect.*` Service（BLE 广播 / 会话共享 / WiFi-AP / WiFi-P2P）+ `androidx.room.MultiInstanceInvalidationService` 未注册、`TETHER_PRIVILEGED` / `OVERRIDE_WIFI_CONFIG` / `NETWORK_SETTINGS` / `NETWORK_STACK` / `LOCAL_MAC_ADDRESS` 等权限缺失 → **`startAdvertise()` 静默失败，手机根本发现不到车机**（这才是「不弹窗、无配对码」的直接根因）；**(d)** ★ **SDK 的运行期传递依赖丢失** —— dex 解析确认 SDK dex 内 `androidx/room/*` **定义数 = 0**（仅引用），缺 `androidx.room:room-runtime` → 补 `implementation("androidx.room:room-runtime:2.4.3")`。同时按用户要求把首页改为**荣耀车联式全自动**（移除「开始配对」「通过蓝牙配对」按钮；配对码自动出现、断开后自动重新广播并刷新），并用 `BuildConfig.ICCOA_ONLY` 关掉 `AapService` 抢 WiFi P2P/SoftAP 的 AA 副作用。产物：**20,816,877 B** / SHA-256 `6f6f4b6befd18d5a0d0ad0cda33f097be490995c8e9b8c39c592100fe97d0c9c`（注入 dex 顺延为 **`classes5.dex`**）。
> - 2026-10-03（深夜·续：配对码周期重播 + 文案合规）—— 用户真机反馈 **「能正常连接了」**（§9.7 修复生效），并提出两点：①「**断开后刷新的链接码如果当时没人连，后面就不再刷新；手机关掉弹窗后就再也连不上**」② 首页文案要去掉「小米」字样、加免责声明。逐条处理（详见 **§9.8**）：**(a) ★ 配对码 30 秒看门狗** —— 根因是「车机一直在广播」**不等于**「手机能重新发现」（**BLE 广播帧不变时手机端 CarWith 不会重新弹窗**），故只做「断开刷新一次」必然断链；**且重播必须是 `stopAdvertise()` → `startAdvertise()`**（对已广播链路重复 `startAdvertise()` 会被 SDK 直接忽略），并加 `sessionActive` / `castStarted` 避让与 `onPinCode` 计时重置，另加「刷新配对码」手动按钮；**(b)** 首页文案去厂商名：`MiAutoCar · 车机端` / `MIAUTO CARLINK · CARWITH` /「把手机投屏到这块车机屏幕或仪表盘/HUD。」，5 语言同步；**(c)** 首页底部新增法务声明 `da_legal`（非 BYD 官方、与 ICCOA / 小米无隶属或授权关系、仅供学习交流、禁止商用）；**(d)** 顺带修掉 `tryStartCast()` 的**无上限 500ms 自我重试**（待机页无 Surface → 永不停止的定时器 = 持续耗电）。产物：**20,825,069 B** / SHA-256 `917fe3eaa0c46a5f5890b7cf36d295a5f80288673d4fc8103b6164a17d785163`，`apksigner verify` EXIT=0。
> - 2026-10-13 —— **公开仓库落地 + 文案口径放宽（v0.1.1）**。仓库发布到 `github.com/PiggyBackBayonet/MiAuto`（站点 + Release 见 README）。用户指出「手机端不应该只写小米」：ICCOA CarLink 是小米/OPPO/vivo 联合发起，故把**所有对外文案**统一为「小米 CarWith / OPPO Car+ 车联 / vivo Jovi InCar」。v0.1.1 覆盖的是 v0.1.0 里**打包进 APK、当时未及同步**的部分：首页 `da_description` / `da_hint` / `da_wireless`、五语言 `da_legal`（并补上 ICCOA CarLink 的归属说明）、`support_rules_phone_desc`（3 语言）、`AboutFragment` 正文与免责句、`CHANGELOG*.md`、`docs/INSTALL.md`；另**新增 `values-zh-rTW/diauto.xml`**（原先 zh-TW 下 `da_*` 整组回退英文）。**纯文案，行为与 0.1.0 一致**。

---

## 0. 结论前置

**可行，而且比预想的容易 —— 现成的车机端 SDK 直接可用，真实样本已实测能连上小米 CarWith。**

三句话结论：

1. **不需要逆向小米的协议。** CarWith 在车机侧**没有自己的私有协议**，它是一个"外壳"，复用车机自带的 **百度 CarLife / 亿连 / ICCOA CarLink** 三条通道之一（CarWith 包名 `com.miui.carlink`）。所以 MiAuto 要做的是**一个车机端互联应用**，而不是"模拟小米车机"。

2. **走 ICCOA CarLink 路线 —— 有现成的车机端 SDK，是成品组件，不是源码工程。** 一个真实样本（吉利换皮的 ICCOA 官方 demo，`智慧车联_v1.5.3-支持方控.apk`）**装到用户自己的车机上后，用户的小米手机直接识别并连接成功**。从该样本逆向出：
   - SDK 门面只有几个方法：`UCarAdapter.init()` / `startAdvertise()` / `startCast(Surface, w, h)` / `sendTouchEvent()`
   - 协议 658 行 `.proto` **明文附带在 APK 里**
   - 鉴权（ECDH + ECDSA + HMAC）**全在 Java 层，可读可改**（对比 CarLife 路线封在 `libencryption.so` 里改不了）
   - **证书零密码学校验**，且可用 `UCarConfig.setCcdFilePath()` 换成自己的

3. **真正的风险不在协议，而在"协议版本漂移"和"手机端黑盒策略"。** 样本 SDK 是 **v1.5.x（2024-01）**，而 CarWith 已迭代到 **2026 年的 3.8.x**；协议本身已到 **3.0（2025-11）**。手机端（CarWith）的校验/弹窗/能力协商逻辑**不在我们的可控范围内**，是唯一可能让连接失效的地方。

**建议路线**：先做 **POC-0（10 分钟）：装官方 ICCOA demo 到你的车机，用你的小米手机连一次** —— 确认"这台车机 + 这台手机"能通（✅ 已实测通过）。然后 **POC-1：写一个最小 App，脱离 demo UI 只调 `init()` + `startAdvertise()` + `startCast()`** —— ✅ **编译与打包已于 2026-10-03 打通**（产出可安装 APK）；同日依据**真机反馈**修掉「点启动闪退」（跨 dex 漏 86 个 Tink 类）并把启动逻辑对齐样本（打开即初始化 + 大字连接码），真机复验待做。这两步过了，后面全是工程量问题。

---

## 1. 关键事实：CarWith 到底是什么

### 1.1 它不是协议，是"换皮"

CarWith 的机制是：**借用原车机的互联通道，把车机画面换成小米自己的极简车载 UI**。小米官方口径和多家实测都指向同一句话：

> "CarWith 不是独立互联协议，**依托百度 CarLife、亿连、ICCOA 通道运行**"
> —— 多个来源交叉印证

这里有一个**关键细节差异**，决定了本报告的路线修正：

| 通道 | 手机端要不要"翻译组件" |
|---|---|
| **百度 CarLife** | ⚠️ **要** —— 首次连接时自动下载安装「CarLife 组件」，实测文章比喻为"翻译官"。**版本不受你控制**，且实测成功率被描述为偏低 |
| **ICCOA CarLink** | ✅ **不要** —— CarWith **原生集成** ICCOA 车机端识别逻辑。车机装好 ICCOA 车机端 App，手机端直接扫到即连（用户实测确认） |

这直接决定技术路线：**MiAuto 应做 ICCOA CarLink 车机端 —— 这是 CarWith 的一等公民通道；CarLife 降为备选。**

### 1.2 连接方式（ICCOA 侧）

| 方式 | 车机侧角色 | 手机侧角色 | 触发机制 | 稳定性 |
|---|---|---|---|---|
| **有线 USB** | USB Host（**AOA**，`libusbio.so`） | USB Device | 插线 + 车机打开等待界面 | **最好**，零延迟、不断连、边走边充 |
| **无线·车机热点** | WiFi AP（`WifiApService`，走 `TetheringManager`） | STA | 手机连车机热点 | 好 |
| **无线·WiFi Direct** | P2P GO（`WifiP2pService`，组名 `DIRECT-ICCOA-*`） | P2P Client | BLE 广播触发后自动协商 | 好，无感 |
| **无线·手机热点** | WiFi STA | AP（5GHz） | 手机开热点，车机去连 | 一般，信号差会掉（ICCOA SDK 侧支持度待验证） |
| **无感连接** | BLE GATT 广播 | — | 蓝牙识别 → 自动建链 | 体验最好，`startAdvertise()` 已封装 |

> ⚠️ **第 4 行是 DiAuto Pro 需要"反过来"的地方**：Android Auto 无线是**车机做 AP**，CarWith 也支持**手机做 AP、车机做 STA**。DiAuto Pro 里的 `SoftApCredentialsProvider` / `HotspotManager` / `LocalHotspotPolicy` 在 MiAuto 里要改造或反向使用。
>
> 好消息：ICCOA SDK 已内置三种传输（AOA / SoftAP / P2P），由 `UCarConfig.setSupportedWorkModes()` 声明，**大部分传输细节不用自己写**。

### 1.3 CarWith 的车机侧行为（实测路径 · ICCOA）

```
车机侧：打开「智慧车联」/ ICCOA 车机端 App → 自动 startAdvertise() 广播
手机侧：设置 → 连接与共享 → 小米 CarWith → 打开总开关
       ↓ 手机 BLE 扫到车机（Service UUID 2abcc850-…-123456789100）
手机弹窗：识别到 ICCOA 车机 → 显示 PIN 码
       ↓ 车机侧输入/确认同一 PIN（AuthRequest.userConfirmed）
手机弹窗：屏幕截取 / 通讯录 / 通知权限 → 全部允许
       ↓ 手机起 Miracast 会话 → 车机 libovmsink.so 作 Sink 收流
       ↓ 车机屏幕出现小米车载 UI
```

**关键推论**：车机端要做的，是**一个完成 ICCOA 广告广播 + 等待连接的应用**。这不是"逆向小米"，而是**实现一个 ICCOA 车机端**——小米 CarWith 主动来连是它自己的行为。CarWith 的更新日志原文印证了它原生支持这条路：

> "优化无线连接流程：**车机打开 carlife**，非 HyperOS 手机如已连接过车机，连上车机蓝牙进 CarWith 界面会弹窗提示连接"
> —— 同一套流程在 ICCOA 通道上同样成立，且**不需要动态下载组件**

---

## 2. 三条通道的可行性裁决

> ### ⚠️ 2026-10-02 重大修正 —— 原结论已证伪
>
> 本节最初写的是「ICCOA CarLink ❌ 排除，因为必须过联盟认证，未认证设备手机根本不会识别」。
>
> **这个判断是错的。** 用户提供了一个真实样本 `智慧车联_v1.5.3-支持方控.apk`（实测装到车机后，小米 CarWith **直接识别并连接成功**）。完整逆向报告见 **[`APK_ANALYSIS_ZHL-v1.5.3.md`](./APK_ANALYSIS_ZHL-v1.5.3.md)**，要点：
>
> 1. 该包 **就是 ICCOA CarLink 车机端 SDK**（`ucarhusdk`，包名 `com.ucar.*` + `com.share.connect.*`，`libovmsink.so` 内含 `ICCOA` 字符串），是吉利换皮的 ICCOA 官方 demo。
> 2. 它能连上的原因是 **配对走"用户确认 + PIN 码"**，而不是 CA 信任链；**证书在车机 SDK 里零密码学校验**（`CarVerificationData.isValid()` 只判字段非空，字节码级证据）。
> 3. 它内置的是一份 `vendor_id=0001 / product_id=0001`、名字明写「测试车机」的**自签测试证书** —— 手机照样接受。
>
> **所以：ICCOA 路线对个人项目是通的，而且比 CarLife 路线门槛更低（不用自己实现协议栈，SDK 是现成的可编程组件）。**「必须过认证」约束的是**对外发布产品**，不是**自用/自研**。

### 2.1 修正后的裁决

| 通道 | 车机端实现可得性 | 技术门槛 | 自用可行性 | **裁决** |
|---|---|---|---|---|
| **ICCOA CarLink** | ✅ **有**，官方车机端 SDK（`ucarhusdk`）随 demo APK 公开分发；字节码可直接用 | **低**（API 干净：`init` / `startAdvertise` / `startCast(Surface,w,h)`） | ✅ **通**（实测未认证设备可连） | ✅ **首选** |
| **百度 CarLife** | ✅ 有，百度官方开源（Apollo DuerOS），Apache-2.0，86 proto | 中（SDK 老旧 2022-04，鉴权封在 `libencryption.so`） | ✅ 通 | ⚠️ 备选 |
| **亿连** | ❌ 无 | 高 | ❌ | ❌ 排除 |

### 2.2 为什么 ICCOA 反而比 CarLife 好

| 维度 | CarLife 路线 | ICCOA 路线 |
|---|---|---|
| 需要自己写协议栈吗 | 半要（SDK 老旧，proto 要迁移） | **不用**，SDK 是成品组件 |
| 鉴权你能改吗 | ❌ `libencryption.so` 预编译闭源 | ✅ 全在 Java 层（ECDH + ECDSA + HMAC，可读可改） |
| 证书要官方签发吗 | 不需要 | **不需要**（SDK 不校验；可 `setCcdFilePath` 换自己的） |
| 版本漂移风险 | 🔴 SDK 停在 2022-04，CarWith 已到 2026 3.8.x | 🟡 样本是 2024-01 的 SDK v1.5.x，仍落后但更近 |
| 与小米 CarWith 的匹配度 | 需要 CarWith 侧"CarLife 组件"当翻译层，实测成功率被描述为"低于 30%" | **CarWith 原生支持 ICCOA**，实测成功率接近 100% |

**结论：MiAuto 改走 ICCOA CarLink 路线。**

> ⚠️ 两条路线**共同的法律边界**：ICCOA SDK 是联盟发给成员的凭据。自己做、自己用没问题；**要对外发布产品仍需过认证**。详见分析报告 §8 风险表。

---

## 3. 可用的资产

### 3.1 主资产：ICCOA CarLink 车机端 SDK（来自真实样本 APK）

**这是本次路线修正的核心依据。** 一个可直接运行的成品组件，不需要编译、不需要迁移工具链。

| 项 | 内容 |
|---|---|
| **载体** | `智慧车联_v1.5.3-支持方控.apk`（6,495,573 B，SHA-256 `645127cb…e0d7e`） |
| **应用身份** | `applicationId = com.geely.carlink`（吉利换皮），launcher = `com.ucarhu.demo.UCarDemoActivity` |
| **SDK 内部代号** | `ucarhusdk`（UCar Head Unit），源自原生构建路径 `E:/1.5.3verson/ucarhusdk/core/src/main/cpp/` |
| **SDK 版本串** | `3.0.0-v1.5.3-202401092110-支持方控`（2024-01-09 构建） |
| **协议版本常量** | `ICCOA_PROTOCOL_VERSION_CODE_1_0` / `ICCOA_PROTOCOL_VERSION_CODE_1_5` |
| **交付形态** | **字节码（8 个 dex）+ 2 个原生库**，无源码 |
| **许可证** | ❌ **无**（第三方换皮包，未附任何 LICENSE） |
| **原生库** | `lib/arm64-v8a/libovmsink.so`(1,481,168 B，Miracast Sink) / `libusbio.so`(215,040 B，USB AOA) |
| **协议定义** | 根目录附 `ucar.proto`（**15,595 B / 658 行，明文**）+ `test_pb_data.proto` |
| **证书** | `assets/ccd.data`（2,120 B） |

> ⚠️ **工具链优势**：CarLife 路线要 JDK 8 + Gradle 6.7 + Kotlin 1.5.20 单独编译；ICCOA 路线**直接用反编译出的类或直接复用 APK 里的 dex/so**，`UCarConfig.Builder` 的 API 是稳定的，可以 **flatDir / jadx 导出源码后并入**。

### 3.2 SDK 门面 API（`UCarAdapter`，全部从字节码提取）

```java
// 初始化
UCarAdapter.getInstance().init(Context, UCarConfig, ICarInitCallback)

// 广播 + 投屏
startAdvertise()                       // 让手机发现车机（BLE GATT）
stopAdvertise()
startCast(Surface, int width, int height)   // ★ 只依赖一个 Surface，与渲染层解耦
stopCast()

// 输入回传
sendTouchEvent(...) / sendKeyEvent(...)     // UIBC 通道
sendCustomKeyEvent(CustomKeyEvent.KeyCode)  // 方向盘自定义键

// 车机数据上行（AA 没有的能力）
sendGPSInfo() / sendGearStateInfo() / sendLightsInfo()
sendOilInfo() / sendAccelerationInfo() / sendBatteryInfo()

// 回调注册
registerPhoneDataListener(IPhoneDataListener)     // 导航/音乐/通话
registerConnectListener(ICarConnectListener)
registerAudioRecorderListener(ICarAudioRecorderListener)
```

**`UCarConfig.Builder` 关键配置项**（决定"能不能适配你的车机"）：

```java
setCarBrMac(String)            // 车机蓝牙 MAC（用于无感连接识别）
setDpi(320) / setFps(60)
setScreenWidth(int) / setScreenHeight(int)
setVideoDisplayWidth(int) / setVideoDisplayHeight(int)
setSupportP2P(boolean) / setSupportSoftAP(boolean) / setSupportHevc(boolean)
setSupportedWorkModes(...) / setAllowed5gChannels(...)
setWifiSsid(String) / setWifiPassword(String)
setCcdFilePath(String)         // ★ 换掉内置测试证书
setSupportPOINav(boolean)
```

### 3.3 协议定义 `ucar.proto`（658 行明文，可读）

```protobuf
java_package = "com.ucar.databus.proto";

enum CmdCategory { NONE; CONTROL; SENSOR; AUTH; AUDIO; VIDEO; CERT; ACK; }
enum MessageType { SEND; REQ; RES; SEND_SYNC; }
enum ChannelType { CUSTOM; UIBC; RTSP; RTP; AUTH; CONTROL; MEDIA; SENSOR; CERT; VIDEO; }
```

覆盖能力（与 AA 的对照见 §4）：

| 类别 | 代表 message |
|---|---|
| 能力协商 | `GetUCarConfigRequest/Response`（screenWidth/Height、videoDisplayWidth/Height、dpi、fps、isSupportP2P、isSupportSoftAP、isSupportHevc、workModes、carBrMac、sdkVersion） |
| 鉴权 | `AuthRequest`（authPk / authPkHmac / agreementPk / agreementPkSig / random=Na / **userConfirmed** / productName）、`AuthResponse`（Nb / result / dayOrNightMode）、`AuthConfirm`（cipher = 会话密钥加密的手机本地 IP） |
| 证书 | `CarCertificate{bytes content}` / `CertIndex` |
| 投屏控制 | `NotifyMirrorState`（STOP_MIRROR / DISCONNECT_LINK / GO_TO_DESKTOP / BACK_TO_FOREGROUND） |
| 端口协商 | `GetPortRequest{ServerType: SENSOR / AUDIO / UIBC}` |
| 手机状态 | `NotifyPhoneState`（is_screen_locked / is_wechat_qq_call / is_low_power / is_overheat） |
| 媒体/导航 | `NotifyMusicInfo` / `NotifyNavigationInfo`（directionIcon / distance / distanceUnit / operation / where / title1 / title2） |
| 按键/语音 | `CustomKeyEvent` / `VRCmdToPhone` |
| 车机传感器 | `Gps` / `Lights` / `GyroScope` / `Acceleration` / `Oil` / `GearInfo` / `LightSensorInfo` |
| 应用列表 | `GetAppList` / `GetAppDetailInfo` / `InvokeApp` / `AppListChanged` / `AppStateChanged` / `AppCategory`(18 类) |
| 会话 | `Disconnect` / `Heartbeat` |

**自定义按键码**（`0x1FA7` 起，避开标准 Android keycode）：

```
KEY_CODE_MAIN     = 0x1FA7      KEY_CODE_VR_START  = 0x1FAB
KEY_CODE_TEL      = 0x1FA8      KEY_CODE_VR_STOP   = 0x1FAC
KEY_CODE_NAVI     = 0x1FA9      KEY_CODE_NAVI_QUIT = 0x1FB1
KEY_CODE_MEDIA    = 0x1FAA      KEY_CODE_NEXT_FOCUS= 0x1FB2
                                KEY_CODE_MEDIA_FAVOR = 0x1FB6
```

### 3.4 内置证书 `assets/ccd.data`（★ 关键发现）

解码结果（外层 base64 → JSON → 内层 UTF-8 JSON）：

```
外层: { content:[147 bytes], cryptoConfigIndex:"o-5T4plzjMmcilZs",
        signAlgorithm:"SHA256withRSA", signature:[256 bytes], version:1 }
内层: { short_name:"测0试1车2机3",  product_name:"测0试1车2机3 2022款",
        vendor_name:"测0试1车2厂3", vendor_id:"0001", product_id:"0001" }
```

> 名字里夹的 `0/1/2/3` 是行业常见的**泄漏溯源水印**；`vendor_id=0001 / product_id=0001` 是**测试用 ID**。

**`CarVerificationData.isValid()` 的完整逻辑**（字节码级证据，无省略）：

```java
return mContent != null && mContent.length > 0
    && mSignature != null && mSignature.length > 0
    && !TextUtils.isEmpty(mCryptoConfigIndex)
    && mVersion > 0;
```

→ **没有 RSA 验签、没有 CA 链校验、没有 vendor 白名单、没有在线校验。** `getCarDevice()` 只是用 gson 把 `short_name`/`vendor_id`/`vendor_name`/`product_id`/`product_name` 搬进对象。

**这意味着两条路都通**：
1. 直接沿用这份测试证书（跑得通，但有 R6 水印问题）
2. 用 `UCarConfig.setCcdFilePath()` 指向**自己造的 ccd.data**（格式已知，`signAlgorithm`/`signature` 字段反正不校验）

### 3.5 备选资产：百度 CarLife Android Vehicle SDK

ICCOA 路线若不测（见 §7 R1），CarLife 仍是可用备选。

| 项 | 内容 |
|---|---|
| **仓库** | `fgrcwp/apollo-DuerOS` → `CarLife-Android-Vehicle-V2.0/` |
| **上游原仓库** | `ApolloAuto/apollo-DuerOS` —— ⚠️ **已删除（404）**，只有 fork 还在 |
| **最后提交** | 2022-04-28（`fddb0be`） |
| **许可证** | Apache-2.0 |
| **工具链** | Kotlin 1.5.20、Android Studio 4.2+、**JDK 8**、Gradle 6.7+、minSdk 21 / targetSdk 30+ |
| **主入口** | `CarLifeContext`（Kotlin 接口，279 行） |
| **协议** | 86 个 `.proto`（`carlife-sdk/src/main/proto/`） |
| **交付形态** | 可自编译，或直接用预编译 `carlife-sdk-release.aar`（2.2 MB） |
| **⚠️ 预编译二进制** | `libencryption.so`（4 架构，共 5.2 MB）+ `encryption.jar` —— **鉴权封在里面，不可改** |

CarLife 侧通道常量（原文）：

```kotlin
const val MSG_CHANNEL_CMD = 1   MSG_CHANNEL_VIDEO = 2   MSG_CHANNEL_AUDIO = 3
const val MSG_CHANNEL_AUDIO_TTS = 4   MSG_CHANNEL_AUDIO_VR = 5
const val MSG_CHANNEL_TOUCH = 6   MSG_CHANNEL_UPDATE = 7
const val AOA_MESSAGE_MAX_SIZE = 64 * 1024 * 1024
const val BLUETOOTH_COMMUNICATE_UUID = "00001101-0000-1000-8000-00805F9B34FB"
```

> 注：源码里把 wireless 拼成了 `wirless`（少一个 e），是官方仓库原样，**不要"修正"**，否则找不到文件。

---

## 4. 协议栈对照：Android Auto vs ICCOA CarLink

这是决定"哪些代码能复用"的核心表格。

| 层 | DiAuto Pro（AA） | CarWith / ICCOA CarLink | 可否复用 |
|---|---|---|---|
| **发现/触发** | USB 插入自动识别；WiFi Direct / SoftAP 广播 | **BLE GATT 广播**（Service UUID `2abcc850-9935-4f8a-ba84-123456789100`）+ WiFi Direct 组名 `DIRECT-ICCOA-XXXX`（或车机热点） | ❌ 需重写（但 SDK 已封装，只需调 `startAdvertise()`） |
| **传输** | AOA USB、TCP Socket、libusb | ① USB AOA（`libusbio.so`）② 车机热点 ③ WiFi Direct；控制通道为自建 socket | ⚠️ 部分（`AccessoryConnection` 抽象可复用） |
| **握手/加密** | TLS（`AapSsl` + AA 证书链） | **ECDH 会话密钥 + ECDSA 身份签名 + HMAC**，**全在 Java 层**（`KeyNegotiator`/`ServerKeyNegotiator`），密钥落 Android Keystore | ❌ 必须替换，**但可读可改** |
| **配对** | AA 的 OAuth/证书授权 | **PIN 码 + 手机弹窗用户确认**（`AuthRequest.userConfirmed`），信任设备存 Room 表 `Peer` | ❌ 新增 |
| **消息协议** | AA protobuf（`aap/protocol/*`） | `ucar.proto`（658 行明文）+ 13 个通道 | ❌ 必须替换（SDK 已带） |
| **视频解码** | `VideoDecoder.kt`（**110 KB**，H.264/H.265，软硬解回退） | **H.264**（可选 HEVC），**收流由 `libovmsink.so` 的 Miracast Sink 完成**，解码结果送入 App 提供的 `Surface` | ✅ **高度可复用**（`startCast(surface,…)` 天然解耦） |
| **音频** | `AudioMixer` / `AudioTrackWrapper` | 三路独立：`STREAM_CAST_MUSIC(0x10)` / `STREAM_TTS(6)` / `STREAM_AI_ASSISTANT(3)` | ✅ 可复用（通道更多） |
| **麦克风** | `MicRecorder` | 车机采集 → UIBC/MIC 通道回传手机，支持立体声 | ✅ 可复用 |
| **输入** | Touch / KeyCode / 旋钮 / 滚动 | **UIBC**（`UibcManager`）→ `sendTouchEvent` / `sendKeyEvent` + `CustomKeyEvent.KeyCode`（`0x1FA7` MAIN / `0x1FA8` TEL / `0x1FA9` NAVI / `0x1FAA` MEDIA / `0x1FAB` VR_START …） | ✅ 映射即可 |
| **车机数据上行** | 无（AA 没有这条） | **有**：`sendGPSInfo` / `sendGearStateInfo` / `sendLightsInfo` / `sendOilInfo` / `sendAccelerationInfo` / `sendBatteryInfo` | ➕ 新增能力（手机据此判断行驶状态） |
| **仪表盘投屏** | `cluster/`（BYD `fission_bg_*` 图层 + Presentation/Activity） | 导航数据经 `IPhoneDataListener.onNavigationInfoReceived`（方向图标 / 距离 / 操作 / 路名） | ✅ **承载层可复用，只换数据源** |
| **挡风玻璃 HUD** | `AapNavigation` 三通道（stock receiver / SOME/IP / 广播） | 同上，CarWith 3.6 起原生支持 HUD 数据 | ✅ **承载层可复用，只换数据源** |

**一句话总结这个表**：**DiAuto Pro 的"后半身"（解码 → 渲染 → BYD 仪表/HUD 集成 → 设置 UI）几乎全部可复用；"前半身"（发现 → 配对 → 鉴权 → 收流）直接换成 ICCOA SDK 的调用，不需要自己写。**

> 与 CarLife 路线的最大差别：**要替换的"前半身"是一块装好的积木（几个方法调用），而不是一套要自己迁移的源码工程。**

---

## 5. 改造边界图

`AccessoryConnection` 这个接口是天然的分界线 —— DiAuto Pro 已经把它抽象出来了：

```kotlin
// 已存在的抽象，字节管道
interface AccessoryConnection {
    val isSingleMessage: Boolean
    fun sendBlocking(buf: ByteArray, length: Int, timeout: Int): Int
    fun recvBlocking(buf: ByteArray, length: Int, timeout: Int, readFully: Boolean): Int
    val isConnected: Boolean
    suspend fun connect(): Boolean
    fun disconnect()
}
```

```
┌──────────────────────── MiAuto（改造后） ─────────────────────────┐
│                                                                   │
│  ┌──────────── 保留自 DiAuto Pro（几乎不改） ────────────┐        │
│  │  decoder/VideoDecoder.kt      H.264/H.265 软硬解      │        │
│  │  decoder/AudioMixer.kt        音频混音                │        │
│  │  decoder/AudioTrackWrapper   音频输出                 │        │
│  │  decoder/MicRecorder.kt       麦克风采集              │        │
│  │  view/                        渲染与 Surface          │        │
│  │  cluster/                     BYD 仪表盘投屏 ✅        │        │
│  │  hud/ + AapNavigation 承载    挡风玻璃 HUD ✅         │        │
│  │  utils/Settings.kt            设置框架                │        │
│  │  main/                        主页与设置 UI           │        │
│  └───────────────────────────────────────────────────────┘        │
│                            ▲                                      │
│                            │ Surface / 已解析的导航·输入事件      │
│                            │                                      │
│  ┌──────────── 删除：AA 专有（整块拿掉） ────────────────┐        │
│  │  aap/AapTransport.kt (39KB)  aap/AapSsl*.kt (TLS)     │        │
│  │  aap/protocol/*              aap/AapService.kt (175KB)│        │
│  │  connection/*AaHandshakeManager.kt (84KB)             │        │
│  └───────────────────────────────────────────────────────┘        │
│                                                                   │
│  ┌──────────── 接入：ICCOA CarLink SDK（成品组件）───────┐        │
│  │  UCarAdapter.init(ctx, UCarConfig, ICarInitCallback)   │        │
│  │  UCarAdapter.startAdvertise()      ← 让手机发现车机    │        │
│  │  UCarAdapter.startCast(Surface,w,h)← 投屏到我们的 Surface│     │
│  │  UCarAdapter.sendTouchEvent/sendKeyEvent ← 方向盘/触屏  │        │
│  │  UCarAdapter.registerPhoneDataListener ← 导航/音乐/通话 │        │
│  │  UCarAdapter.sendGPSInfo/sendGearStateInfo/... ← 车机数据│      │
│  │  lib/arm64-v8a/libovmsink.so (Miracast Sink)           │        │
│  │  lib/arm64-v8a/libusbio.so    (USB AOA)                │        │
│  │  assets/ccd.data              (车辆证书，可自备)        │        │
│  └───────────────────────────────────────────────────────┘        │
│                                                                   │
│  ┌──────────── 新增：适配层（MiAuto 自写，主要工作量）────┐       │
│  │  IPhoneDataListener.NavigationInfo → AapNavigation      │       │
│  │    → cluster/ 的 BYD 仪表图层（fission_bg_*）           │       │
│  │    → HUD 承载（挡风玻璃）                                │       │
│  │  ICarConnectListener → 现有连接状态 UI                  │       │
│  │  ICarAudioRecorderListener → 现有 MicRecorder           │       │
│  │  车机传感器/挡位/GPS 采集 → send*Info()                 │       │
│  │  方向盘按键 → sendKeyEvent()（可复用 STC800 项目积累）  │       │
│  └───────────────────────────────────────────────────────┘        │
└───────────────────────────────────────────────────────────────────┘
```

**行号/文件级证据**：
- `aap/AapTransport.kt:60` `class AapTransport(...)` —— AA 协议传输（39 KB）
- `aap/AapSsl.kt:5` `interface AapSsl` / `aap/AapSslContext.kt:14` `class AapSslContext(keyManager: SingleKeyKeyManager): AapSsl` —— TLS 实现
- `aap/AapService.kt` —— 175 KB 巨兽，AA 会话主逻辑
- `connection/NativeAaHandshakeManager.kt` —— 84 KB，AA 原生握手
- `decoder/VideoDecoder.kt:427-429` `enum class CodecType { H264("video/avc"...), H265("video/hevc"...) }` —— **复用点**
- `cluster/ClusterTriggerPolicy.kt` —— 三档投屏时机，**可原样复用**
- `app/src/debug/java/.../hud/` —— ⚠️ HUD 相关文件在 **debug** 源集，是独立 demo，不是生产路径

---

## 6. 三条落地方案

MiAuto = **DiAuto Pro 的"后半身"** + **ICCOA CarLink SDK 的"前半身"**。

### 方案 A：直接复用现成 demo（0 开发）

直接把 `智慧车联_v1.5.3` 装到车机上用。

| 优点 | 缺点 |
|---|---|
| **0 行代码**，立刻能连 | 与 DiAuto Pro 完全无关，**没有 BYD 仪表盘/HUD 集成**、没有自己的设置框架 |
| 可以当作"路线可行性 + 参数标定"的基准 | R4（debug 签名）/ R3（私有权限）/ R6（水印证书）**三项风险全中** |

> **定位**：不做产品，只做 **POC-0 的对照组**（见 §9）。

### 方案 B：提取 SDK 融入 MiAuto（★ 推荐）

从样本 APK 中提取 ICCOA SDK（`com.ucar.*` + `com.share.connect.*` + 2 个 so + `ccd.data`），作为**成品组件**引入 MiAuto 工程；自写一层适配层把 ICCOA 事件接进 DiAuto 现有渲染/仪表/HUD 链路。

| 优点 | 缺点 |
|---|---|
| 继承 DiAuto Pro 的 110 KB 解码器、BYD 仪表盘、HUD、设置框架 | 需要写 **ICCOA 事件 → DiAuto 数据模型** 适配层（主要工作量） |
| **协议栈不用自己写**（SDK 是成品） | SDK 是字节码，**需要 jadx 导出源码**（本样本 jadx 挂起，见 §9.5 的替代方案） |
| 用户体验一致（同一套设置、同一套 BYD 集成） | 与上游 SDK 无正规更新渠道，版本固定在 v1.5.3 |

**工程要点**：
1. **先用 jadx 把 SDK 相关类导出**（`com.ucar.*` / `com.share.connect.*` / `com.ucarhu.demo.*`），剔除 demo 的 UI 部分（`UCarDemoActivity` 里的 `SurfaceView` / `MediaSession` 可以照抄）。
2. **原生库不要动**：`libovmsink.so`（Miracast Sink，1.48 MB）+ `libusbio.so`（USB AOA）。只需放入 `src/main/jniLibs/arm64-v8a/`。
3. **适配层是唯一主要工作量**，接口天然解耦：
   - `UCarAdapter.startCast(surface, w, h)` → **直接喂 DiAuto 现有的 Surface**，不需要改 `VideoDecoder.kt`
   - `IPhoneDataListener.NavigationInfo`（directionIcon / distance / operation / where / title）→ 接进 `AapNavigation` / `cluster/`
   - `UibcManager` 的触控事件 → 接到 DiAuto 的输入处理
   - 车机传感器/挡位/GPS → `send*Info()`（这是 AA 完全没有的新能力）
4. **⚠️ `ucar.proto` 是明文**，但不建议自己重新生成 protobuf 类；SDK 已带编译好的版本。

### 方案 C：依 `ucar.proto` + Java 层鉴权算法从零实现

按 §3.3 的协议定义 + §3.4 的鉴权算法（ECDH + ECDSA + HMAC）+ Miracast Sink 自己重写整套。

| 优点 | 缺点 |
|---|---|
| 架构最干净，无第三方代码依赖，**无许可证/水印问题** | 工作量最大，需自己实现 Miracast Sink + RTP/RTSP + UIBC |
| 可自由升级到 ICCOA 3.0 协议 | `libovmsink.so` 是预编译的，重写等于要自己啃 Wi-Fi Display Sink |

> **不建议作为起点。** 可作为**长期演进目标**（P4）：等方案 B 跑通、协议吃透后再替换。

---

**推荐：方案 B。** 但**先做 POC-0 与 POC-1 验证**（见 §9；POC-0 ✅ 已实测通过、POC-1 ✅ 编译与打包已打通），再决定是否投入适配层。**方案 B 若卡在 SDK 提取上，退路是方案 C 只重写"前半身"、保留 `libovmsink.so` 作 Sink**（此退路目前**不必启用** —— SDK 已用 smali 往返成功提取）。

---

### 三条方案对比一览

| | 方案 A（用现成 demo） | **方案 B（提取 SDK，推荐）** | 方案 C（从零实现） |
|---|---|---|---|
| 开发量 | 0 | **中**（只写适配层） | 高 |
| BYD 仪表/HUD | ❌ 无 | ✅ 复用 | ✅ 复用 |
| 复用 DiAuto 解码器 | ❌ | ✅ | ✅ |
| 许可证风险 | 🔴 高（无 LICENSE） | 🟡 中（同 A，但自主可控） | 🟢 低 |
| 协议升级能力 | ❌ 锁死 v1.5.3 | 🟡 需 SDK 更新 | ✅ 可自行实现 3.0 |
| 适合阶段 | POC-0 | **POC-1 起全程** | P4 以后 |

---

## 7. 风险清单（ICCOA 路线）

| # | 风险 | 严重度 | 说明 | 缓解措施 |
|---|---|---|---|---|
| **R1** | **协议版本漂移** | 🔴 **致命** | 样本 SDK 是 **v1.5.3（2024-01-09）**、协议 `ICCOA_PROTOCOL_VERSION_CODE_1_5`；CarWith 已到 **2026 年的 3.8.x**，ICCOA 协议本身已到 **3.0（2025-11）**。手机端握手版本不匹配 → 连接失败，且**手机端是黑盒，静态分析不了** | **POC-0 必须先测**（10 分钟成本）。若连不上 → 找更新的 SDK 样本 APK（换其他车型的车机端包） |
| **R2** | **手机端策略不可控** | 🔴 高 | CarWith 的车机识别/白名单/弹窗策略**不在这个 APK 里**，无法静态证明它"永远不校验"。样本能连是**实测事实**，但可能随 CarWith 版本变化 | 锁定验证过的 CarWith 版本；把"手机端行为"列为每次升级必测项；README 写明兼容性边界 |
| **R3** | **私有系统权限** | 🔴 高 | 样本申请 8 个**非系统应用拿不到**的权限：`BLUETOOTH_PRIVILEGED` / `NETWORK_STACK` / `TETHER_PRIVILEGED` / `MANAGE_USB` / `OVERRIDE_WIFI_CONFIG` / `NETWORK_SETTINGS` / `LOCAL_MAC_ADDRESS` / `WRITE_SETTINGS`。**这是按预装系统应用设计的** | POC-1 实测：普通 APK 安装后这些权限是否被拒。若必须 → 需要 **root / 平台签名 / 车机厂商合作**（这会把项目性质从"个人自用"推高到"要谈授权"） |
| **R4** | **debug 签名 + `debuggable=true`** | 🟡 中 | 样本用 **Android 调试密钥**签名（`CN=Android, O=Android`，SHA-1 `61ed377e…af81`），manifest `android:debuggable="true"` + `allowBackup="true"` | 自用时无妨；**重新签名**即可消除（用你自己的 keystore）。注意重签后 `ccd.data` 不受影响（证书不带签名绑定） |
| **R5** | **32 位 ABI 缺内置软解** | 🟡 中 | 样本 `lib/` 只有 `arm64-v8a`，`libusbio.so` / `libovmsink.so` 无 32 位版本。但静态核对 `MiAuto-0.1.1.apk`：**发布包四 ABI 齐全**，`armeabi-v7a/` 下有 `libusbhelper.so` / `libusb1.0.so` / `liblocal_hotspot_radio.so` / `libconscrypt_jni.so` / `libhur_soft_hevc.so` —— 正是 App 自身 `System.loadLibrary()` 会用到的那几个。那两个样本残留库在 Java/Kotlin 源码和 SDK dex 里都没有 `.so` 引用，不构成阻塞。真正缺的只有 **FFmpeg 软解**（`libavcodec` / `libavutil` / `libswscale` 仅 arm64） | armeabi-v7a 车机**装得上**：manifest `minSdkVersion=16`、`uses-feature` 全部 `required=false`，不会因 ABI 或硬件被拒装。设置页会把「内置 FFmpeg」如实标为不可用，H.265 回退 MediaCodec。要恢复 32 位软解：按 `app/src/main/cpp/ffmpeg/README.md` 交叉编译该 ABI 的三个 `.so` 放进 `jniLibs/armeabi-v7a/`。**能否连接另受 R3（特权权限）与 BLE 广播（需 API 21+）约束，32 位真机尚未验证** |
| **R6** | **第三方换皮 + 水印证书** | 🟡 中 | 包是吉利换皮的 ICCOA 官方 demo，内置证书带**泄漏溯源水印**（`short_name` 里夹 `0/1/2/3`），`vendor_id=0001` | 用 `setCcdFilePath()` 换成**自造证书**（校验逻辑为零，见 §3.4）。不要原样分发这份带水印的包 |
| **R7** | **无开源、无许可证** | 🟡 中 | 样本**未附任何 LICENSE**，SDK 与原生库的来源/授权范围不明 | 自用无碍；**公开分发前**需澄清授权（这本质上等同于第 R8 条）。README 不声称"官方 SDK" |
| **R8** | **联盟凭据合规** | 🟡 中 | ICCOA 车机端 SDK 是联盟发给成员的**凭据**。自己做、自己用没问题；**要对外发布产品仍需过 ICCOA 认证** | 保持"个人自用 / 自研"边界；若要对公众发布 → 走 §10 的合规路径 |
| **R9** | **AGPL-3.0 传染性** | 🟢 低 | DiAuto Pro 是 **AGPL-3.0**（LICENSE 661 行）。fork 出的 MiAuto **必须同样 AGPL 开源**，不能闭源商业化 | 接受开源；若需闭源，得从零写（工作量翻倍） |
| **R10** | **无线方向相反** | 🟢 低 | AA 是**车机做 AP**，CarWith 也支持**手机做 AP、车机做 STA** | ICCOA SDK 已带三种传输（`setSupportedWorkModes()`），额外加 STA 模式工作量不大 |

**R3 值得单独展开** —— 这是本路线**最容易被低估**的一条。样本的设计场景是**车机厂商预装**（系统应用），而我们面对的是**用户自己 adb install 的普通 APK**：

| 权限 | 普通 App | 系统 App | 影响 |
|---|---|---|---|
| `BLUETOOTH_PRIVILEGED` | ❌ 拒 | ✅ | 可能影响 BLE 广播参数设置 |
| `TETHER_PRIVILEGED` | ❌ 拒 | ✅ | **车机做 AP 的无线模式可能直接不可用** |
| `NETWORK_STACK` / `NETWORK_SETTINGS` | ❌ 拒 | ✅ | 切换网络/优先级调整失败 |
| `OVERRIDE_WIFI_CONFIG` | ❌ 拒 | ✅ | 无法覆盖已有 WiFi 配置 |
| `LOCAL_MAC_ADDRESS` | ❌ 拒 | ✅ | 拿不到真实 MAC（P2P GO 协商可能失败） |
| `MANAGE_USB` | ❌ 拒 | ✅ | USB AOA 模式可能受限 |
| `WRITE_SETTINGS` | ⚠️ 需用户授权 | ✅ | 可手动授予 |

> **好消息**：既然用户**实测这个包在普通安装下就能连上小米手机**，说明 **BLE 广播 + 握手 + Miracast 这条主链路不依赖上述特权权限**（否则连不上）。受阻的应该是**无线 AP 模式**等分支。**POC-1 需要逐项确认到底哪几个模式可用。**
>
> **POC-1 现状（2026-10-03）**：可安装 APK 已产出（`poc1/app/build/outputs/apk/debug/app-debug-poc.apk`，7.10 MB），权限清单照抄样本的可用子集。**上述权限在普通 `adb install` 下是否被拒，仍需真机逐项验证**（`poc1/README.md` §五 有完整的 `pm grant` 与判定表）。

**R2 也值得说明**：这不是"取证式"的项目 —— MiAuto 做的是**ICCOA 车机端实现**，CarWith 主动来连是它自己支持的行为。我们没有反编译 CarWith、没有模拟小米服务、没有绕过任何加密。保持这个边界即可。

---

## 8. 分阶段路线图

```
POC-0 ─ 基准验证（10 分钟）          ← 已有事实：用户实测通过 ✅
  ☑ 装「智慧车联_v1.5.3」到车机，小米手机连一次  ← 用户已完成，结论：能连
  ☐ 记录：CarWith 版本号、车机 SoC 架构（arm64？）、连接方式（USB/无线）
  ☐ 记录：连上后小米 UI 的显示/触控/音频是否正常
  ☐ 记录：logcat 里 ICCOA 相关日志（手机端无法抓，车机端可抓）

POC-1 ─ 最小可编程载体（✅ 编译打包已通 / ⏳ 真机待验）  ← 决定"能否自己控制"
  ☑ 导出 SDK：jadx 对本样本挂起 → 改走 **smali 往返**（baksmali→筛包→smali assemble）
  ☑ 新建空工程（`applicationId=com.miauto.poc1`），只保留 SDK dex + 2 个 so + ccd.data
  ☑ 生成编译存根 jar（`compileOnly`，不进 APK）
  ☑ 写 MainActivity：UCarAdapter.init() + startAdvertise() + 纯色 Surface 上 startCast()
  ☑ 打包出 `app-debug-poc.apk`（7.10 MB；`classes4.dex` 注入 + zipalign + 重签）
  ☑ 静态校验：13 项跨 dex 关键类全部解析
  ☑ **MiAuto 主工程（`repos/MiAuto/`，包名 `com.miauto.app`）已从 poc1 派生并本地编译出完整可装 APK**（7.27 MB；内建 `injectIccoaDex` 任务自动注入 `classes4.dex` + zipalign + apksigner 重签；init-once 状态机根治「停止→启动不弹链接码」；`NavigationCluster` 已接 `onNavigationInfoReceived` 作 POC-2 接入位）。详见 §9.2.1.1
  ☐ **真机**：手机连上来 → 看到纯色画面 = 前半身打通
  ☐ 逐项测试 8 个私有权限在普通安装下是否被拒（R3）

POC-2 ─ 导航承载层（★ GO/NO-GO，实现完成 / ⏳ 真机判定）
  ☑ **NavigationCluster 升级为车机仪表级导航卡**：`onNavigationInfoReceived` → 方向图标/距离/操作/路名/目的地/导航状态
  ☑ **方向图标 byte[] 防御式处理**：位图解码优先，失败回退文本箭头（←/→/↑/↩/↰/↱/↗）；字节诊断已打日志
  ☑ **HUD 镜像（btn_hud）**：`scaleY=-1` 承载，挡风玻璃投射
  ⏳ **真机 GO/NO-GO 判定**：连手机导航 → 确认 ① 导航卡实时刷新 ② 方向图标真实格式（PNG/JPEG/编码码）
  ☐ （后续可选）把导航卡并进 DiAuto `cluster/` BYD 仪表图层（fission_bg_*）做深度定制

P1 ─ 有线 USB 跑通（1 周）
  ☐ 方案 B 正式并入 MiAuto：替换 aap/ 为 ICCOA 适配层
  ☐ 验证：画面/触摸回控/音频/麦克风四项全通
  ☐ 用自造 ccd.data 替换水印证书（R6）

P2 ─ 无线（车机热点 / WiFi Direct）（1 周）
  ☐ 复用 SDK 的 WifiApService / WifiP2pService
  ☐ 热点参数走 UCarConfig.setWifiSsid/Password/setAllowed5gChannels
  ☐ 验证：拔线后自动重连、P2P 无感连接

P3 ─ 融合桌面 + 方控（1–2 周）
  ☐ 方向盘按键 → sendKeyEvent / sendCustomKeyEvent（0x1FA7 起；可复用 STC800 项目积累）
  ☐ 车机传感器/挡位/GPS → send*Info（手机据此判断行驶状态）
  ☐ 验证：行车中禁用投屏等策略

P4 ─ 自研证书 + 协议升级（可选）
  ☐ 生成自有 ccd.data，vendor_id 走正规申请或自用
  ☐ 评估是否升级到 ICCOA 协议 3.0（需更新 SDK 样本）

P5 ─ CarWith 特有 UI 特性（按需）
  ☐ 竖屏显示、小窗模式、旋钮控制
  ☐ 应用列表通道（GetAppList/InvokeApp）→ 车机桌面集成
```

---

## 9. POC 操作手册

### 9.1 POC-0：先确认事实（已完成一半）

用户已经实测过：**装上这个包，小米手机能直接识别并连接**。POC-0 剩下的只是**把事实变成可复现的数据**：

| 要记录的东西 | 为什么重要 |
|---|---|
| CarWith 版本号（手机端 设置→应用→小米CarWith） | R1 版本漂移的基线 |
| 车机有没有弹 PIN 码 / 手机有没有弹确认框 | 确认走的是"用户确认"配对而非证书信任链 |
| `adb shell getprop ro.product.cpu.abi` | **必须是 `arm64-v8a`**，否则 R5 命中 |
| `adb logcat \| grep -iE "ucar\|iccoa\|ovmsink\|share.connect"` | 看车机端握手走到哪一步 |
| 连接方式：USB / 车机热点 / WiFi Direct | 确认哪条传输路径可用 |

### 9.2 POC-1：最小可编程载体（✅ 编译与打包已打通，真机待验）

**目标**：证明"我们自己写的 App 也能被小米手机识别"—— 与"直接用现成 demo"的本质区别。

**工程位置**：`repos/MiAuto/poc1/`（`applicationId = com.miauto.poc1`，**与样本 `com.geely.carlink` 不同**；变量只有一个：包名）。
**操作手册（最详细）**：见 [`../poc1/README.md`](../poc1/README.md)。

#### 9.2.1 实测状态（2026-10-03）

| 阶段 | 状态 |
|---|---|
| SDK 抽取（dex + 编译存根） | ✅ 通过（`tools/build-sdk.sh` 可复现）。**已修正跨 dex 漏类** → dex 4,232 → **4,740 类** |
| APK 编译 | ✅ 通过（JDK 17 / AGP 8.5.2 / Gradle 8.13 / platform 34） |
| APK 打包（dex 注入 + zipalign + 重签） | ✅ 通过（`tools/package-poc.sh` → 见 §9.2.2 产物表） |
| 静态校验（跨 dex 依赖完整性） | ✅ 对最终 APK 复扫：`defined=9318 / referenced=10563 / dangling=65`，**SDK 侧（`com/ucar` `com/share` `com/ucarsink` `com/easy` `com/google`）零悬空**；余 65 项为 Kotlin/Kotlinx 内置别名与 androidx.paging 片段（本 App dex 带来，不在 SDK 路径上） |
| 启动逻辑 | ✅ **对齐样本**：打开界面**自动初始化**（不再需要点按钮），见 §9.4.3 |
| 配对码（「链接码」） | ✅ 参数语义已按样本 smali 铁证修正为 `onPinCode(pinCode, carDeviceName)`，见 §9.4.3 |
| **车机真机验证**（手机能否扫到 / 配对 / 投屏） | ⏳ **待做 —— 需一台 arm64 车机 + 小米手机** |

> 本机 `adb devices` 为空，因此"能否被 CarWith 识别"**尚未实测**。所有"已验证 / 未验证"边界逐条标明。
>
> ⚠️ **真机反馈记录（两轮）**：
> 1. 「**点启动按钮就闪退**」→ 根因：跨 dex 漏 86 个 Tink 类 + 3 个 `easy.logger` 类（§9.4.1 坑 4），已修。
> 2. 「**崩溃：startPipeline**」→ 根因：`setCarBrMac()` 收到 17 字节 ASCII MAC 而 SDK 要求**恰好 6 字节二进制**，直接抛 `InvalidParameterException`（§9.4.1 坑 5 + §9.4.1.2 校验表），已修；同时补上 `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` 权限、崩溃信息进状态行、一键「复制日志」按钮。

##### 9.2.1.1 MiAuto 主工程（从 poc1 派生，2026-10-03 本地编译通过）

**工程位置**：`repos/MiAuto/`（`applicationId = com.miauto.app`，version `0.1.0`，minSdk 27 / targetSdk 30 / compileSdk 34）。

从 `poc1/` 复制脚手架（gradle wrapper、assets、jniLibs、`icar-sdk.dex`、stubs、res），新建 `AndroidManifest.xml` 与 4 个 Java 类：

| 文件 | 职责 |
|---|---|
| `MiAutoApp.java` | `Application`：全局崩溃写盘 `crash_<ts>.txt` + `miauto_run.log`；`probeNativeLibraries()` 预载 `ovmsink`/`usbio` |
| `IccoaManager.java` | **SDK 生命周期 + init-once 状态机** + `IccoaUi` 回调接口 + 6 方法 `ICarConnectListener` + 9 方法 `IPhoneDataListener`；`buildConfig()` 已 clamp + 6 字节 `setCarBrMac` + 缓存作废重生成 |
| `NavigationCluster.java` | **POC-2 接入位 v2**：`onNavigation(info)` → 车机仪表级导航卡（方向图标 `byte[]` 位图解码/文本箭头回退 + 距离/操作/路名/目的地/状态 + HUD 镜像） |
| `MainActivity.java` | UI 层：打开即 `autoStart`、5 按钮（启动/停止/重显链接码/复制日志/**HUD 镜像**）、Surface 回调补 `startCast`、一键复制日志 |

**★ init-once 状态机（根治用户报的「停止→启动不弹链接码」）**

根因：`UCarAdapter.init()` 在 SDK 内部是**进程一次性**（`sIsServiceInitialized` 守门），样本「重启」只调 `startAdvertise()`，绝不重复 init。旧实现 `stop()` 把初始化标志复位 → 再启动走 `init()` 分支 → BLE 广播状态残留，手机扫不到、`onPinCode` 不再触发。

修复：`IccoaManager.start()` 头部判断 `if (sdkInitialized && adapter != null) → reAdvertise()`（重注册两个 listener + `startAdvertise()`，不重 init）；`onInitSuccess()` 置 `sdkInitialized=true`；`stop()` 断开但**保留 `sdkInitialized=true`**，只清 `lastPinCode`/`castStarted` 等运行态，等新的 `onPinCode` 刷新。同一修复也落到 `poc1/MainActivity.java`。

**内建 dex 注入（取代 `tools/package-poc.sh`）**

`app/build.gradle.kts` 内含 `injectIccoaDex` 任务（纯 `java.util.zip`，无外部脚本）：
- `dependsOn("assembleDebug")` + `afterEvaluate { tasks.named("assembleDebug") { finalizedBy(injectIccoaDex) } }` —— 每次 `assembleDebug` 自动产出完整可装 APK；
- 从 `classes2` 起找最小空序号 → 实际注入 **`classes4.dex`**；保留原条目压缩方式（**STORED 才设 `compressedSize=size`，DEFLATED 不预设**，否则 `invalid entry compressed size`）；剥离 META-INF 签名后 `zipalign -p -f 4` + `apksigner` 用 debug keystore 重签；
- 踩坑（已解决）：Gradle Kotlin DSL 顶层 `java` 被遮蔽 → `import java.util.zip.*` 短名；`android` 扩展顶层不可见 → 用 `ANDROID_HOME` 环境变量定位 build-tools；`tasks.named("assembleDebug")` 须在 `afterEvaluate` 内取。

**主工程 APK 产物**（已 `assembleDebug` BUILD SUCCESSFUL）：

| 产物 | 大小 | SHA-256 |
|---|---|---|
| `app/build/outputs/apk/debug/app-debug.apk`（含毛玻璃图标 + 车机名 `MiAutoCar`） | 7,998,544 B | `f10fe5be691fc0126b322d7822beb731404f9517e2768e75894c4844fad852ad` |

含 `classes.dex`(6,731,120) + `classes2`(158,932) + `classes3`(42,852) + **`classes4.dex`(7,084,144 = 已 patch 的 SDK dex)** + `lib/arm64-v8a/libovmsink.so`(1,481,168) + `libusbio.so`(215,040) + `assets/ccd.data`(2,120) + `res/mipmap-*` 全套图标（方形/圆形/自适应前景/背景，5 密度，共 22 条目）。`apksigner verify` EXIT=0；`aapt2 dump badging` 确认 `application: icon='res/mipmap-anydpi-v26/ic_launcher.xml'`；`dexdump classes4.dex` 确认 `com.ucar.*` 齐全；APK 内 `classes4.dex` 含 `MiAutoCar` ×1、`ICCOA汽车` ×0。

**应用图标（毛玻璃版，2026-10-03 下午换版）**：源图 `branding/MA.png`（宝蓝 `(63,72,204)` + 橙白「MA」字标）经三步派生：

1. **字形分离**：按「与底色色距」做软边 alpha 遮罩（ramp 18→55）抠出字形 → `branding/glyph_rgba.png`（透明底）。
2. **毛玻璃底**：以纯蓝为基，叠加 ① 乳白化（mix 到 `(132,142,236)`，k=0.18）② 斜向柔光（左上 +17%、右下 −11%）③ 暗角（−11%）④ 左上高光（`spec=0.26·(1−d)²`）⑤ **多尺度霜面颗粒**（低频云 8× + 中频 4× + 逐像素 1×，振幅 9/13/6）→ `branding/frost_bg.png`（512²）。
3. **合成**：字形 mask 高斯模糊（r=8）做柔光 bloom（×0.26）再合成字形 → `branding/master_frost.png`（方形）/ `branding/master_round.png`（圆形，字形内缩 0.90）。

派生资源：`res/mipmap-{m,h,xh,xxh,xxxh}dpi/` 下 `ic_launcher.png`（48/72/96/144/192）+ `ic_launcher_round.png`（超采样圆形裁剪）+ `ic_launcher_background.png`（毛玻璃底 × 108dp）+ `ic_launcher_foreground.png`（字形按 **0.45×108dp** 居中，落 66dp 安全圆内）+ `res/mipmap-anydpi-v26/{ic_launcher,ic_launcher_round}.xml`（`<background>@mipmap/ic_launcher_background` + `<foreground>@mipmap/ic_launcher_foreground`）+ manifest `android:icon`/`android:roundIcon`。预览 `branding/icon_preview.png`（方形/圆形/自适应 72dp 可见圆/48px 四态）。

> 未提供 `<monochrome>` 主题图标层：MA 字标所有非底像素并集后收成一个实心拱形块，辨识度低于彩色版，故不启用（Android 13 未提供该层时启动器正常回退彩色自适应图标，无副作用）。

> ✅ **真机复验已通过**（2026-10-03 用户实测）：主工程**断开重连一切正常** —— init-once 状态机修复坐实。

#### 9.2.1.2 POC-2 导航承载（`NavigationCluster` v2）

`IPhoneDataListener.onNavigationInfoReceived(String, UCarCommon.NavigationInfo)` 是 POC-2 的 GO/NO-GO 入口。

**已实现（v2，车机仪表级导航卡）**：主工程把它接到 `NavigationCluster.show(info)`，渲染——

| 字段 | 来源 | 呈现 |
|---|---|---|
| 方向图标 | `getDirectionIcon()` → **`byte[]`** | `BitmapFactory.decodeByteArray` 成功则显示 `iv_nav_dir`；失败回退 `tv_nav_arrow` 文本箭头 |
| 距离 | `getDistance()` + `getDistanceUnit()` | 28sp 大字 |
| 下一步操作 | `getOperation()` | 动作文本（并用于推导回退箭头 `←/→/↑/↩/↰/↱/↗`） |
| 路名 | `getTitle1()` + `getTitle2()` | 标题行 |
| 目的地 | `getWhere()` | 副行 |
| 状态 | `isNavigating()` | ● 导航进行中 / ○ 导航空闲 |

外加 **HUD 镜像**（`btn_hud` → `box.setScaleY(-1)`，挡风玻璃投射）。

**★ 关键不确定点已用防御式方案覆盖**：`getDirectionIcon()` 返回 **`byte[]`**（`javap -p -classpath <stub.jar>` 确认，非 Drawable/int），真实格式未定（可能 PNG/JPEG 位图，也可能编码方向码）。`IccoaManager.onNavigationInfoReceived` 同步打印**方向图标字节长度 + 前缀 hex**（自动标注 PNG=`89 50 4E 47` / JPEG=`FF D8 FF`），真机实测即可确认真实格式并去掉回退分支。

**待真机判定**：连上小米手机跑导航，确认 ① 导航卡实时刷新 ② 方向图标真实格式。数据能稳定落地 → POC-2 通过，进入工程化；并入 DiAuto `cluster/` BYD 仪表图层（`fission_bg_*`）为后续可选的深度定制（§5 / §9.3）。

#### 9.2.1.3 车机显示名 patch（「ICCOA汽车」→「MiAutoCar」，2026-10-03）

**现象**：车机开始广播后，手机端输入链接码界面下方把车机显示为「ICCOA汽车」。

**根因（smali 铁证）**：`com/share/connect/Device.smali` 的 `getPName()` 与 `getShortName()` **无条件返回硬编码常量**（`.line 30 / .line 46`）：

```
const-string v0, "ICCOA\u6c7d\u8f66"     # 直接返回，不读 mPName 字段
return-object v0
```

即 **`setPName()/setShortName()` 写了字段却无人读取**，连 `ccd.data` 里解析出的 `product_name` 也到不了这里（`CarVerificationData` 确实调了 setter，但 getter 不读）。

**调用链（SDK 内唯一路径）**：`grep -rn "Lcom/share/connect/Device;->getPName()"` 全程**仅一处**调用者：

```
Device.getPName() / getShortName()            ← 硬编码 "ICCOA汽车"
  → UCarConnectProxy.readCarName()            ← 唯一调用者（:812 / :819）
  → mCarName / mCarShortName
  → Intent.putExtra("car_name" / "car_short_name")
  → ShareLinkService.mCarName / mCarShortName  （EXTRA_KEY_CAR_NAME / EXTRA_KEY_CAR_SHORT_NAME）
  → 下发手机端显示
```

**改法**（无 API 入口，只能 patch dex）：

1. `java -jar tools/baksmali.jar d app/libs/icar-sdk.dex -o .work/sdk_smali`（4740 类）；
2. `com/share/connect/Device.smali` 中两处 `ICCOA\u6c7d\u8f66` → `MiAutoCar`；
3. `java -Xmx4g -cp tools/smali/* org.jf.smali.Main assemble .work/sdk_smali -o icar-sdk-new.dex`；
4. 部署到 `app/libs/`（**先写 `.work` 再 `mv` 进 `app/libs`**，否则 Gradle 持有句柄会拒绝访问）→ `assembleDebug` 自动注入重建 APK。

**验证**：新 dex **7,084,144 B**（原 7,084,148，仅短 4 B = 字符串变短后的 section 对齐位移）/ 4740 类 / DEX `035`；将新 dex **反汇编回来与 patch 输入 `diff -rq` 零差异**（证明 smali 往返保真、无类丢失、无意外改动）；APK 内 `classes4.dex` 含 `MiAutoCar` ×1、`ICCOA汽车` ×0；签名 `EXIT=0`。

> 说明：`ICCOA` 字样仍余 8 处（`ICCOA_CARLINK` 协议版本常量等），属协议标识，非显示名，保留正确。

### 9.2.2 构建流程（两个脚本，取代旧的手工解包 + jadx 提源码）

```bash
cd D:/Workbuddy_Work/APK_test

# ① 从样本 APK 重建 SDK 依赖（换样本版本时才需重跑；产物已入库可跳过）
bash tools/build-sdk.sh
#   六步：解 dex → 逐 dex baksmali → 按包筛 SDK+依赖 → smali assemble → 生成签名存根 → 安装
#   -> poc1/app/libs/icar-sdk.dex         (7,084,148 B / 4,740 类)  ← 运行期真身
#   -> poc1/app/stubs/icar-sdk-stubs.jar  (348,268 B / 500 类)     ← 编译期签名存根

# ② 编译 + 注入 dex + 对齐 + 重签
bash tools/package-poc.sh
#   五步：gradlew assembleDebug → inject_dex.py → zipalign -p -f 4 → apksigner → verify
#   -> poc1/app/build/outputs/apk/debug/app-debug-poc.apk   ← 装这个
```

> ⚠️ 直接跑 `./gradlew :app:assembleDebug` 产出的 `app-debug.apk` **不含 SDK**（见坑 1）。必须走 `package-poc.sh`。

#### 9.2.3 最小 Activity（精确 API，非推测）

```java
UCarConfig cfg = new UCarConfig.Builder()
        .setCarBrMac(byte[])                 // ★ 蓝牙 MAC 原始字节，不是 String
        .setDpi(320).setFps(60)
        .setScreenWidth(1920).setScreenHeight(720)
        .setVideoDisplayWidth(1920).setVideoDisplayHeight(720)
        .setSupportP2P(true).setSupportSoftAP(true)
        .setCcdFilePath("ccd.data")
        .build();

UCarAdapter adapter = UCarAdapter.getInstance();
adapter.registerCarConnectListener(...);     // 6 个方法全部要实现
adapter.registerPhoneDataListener(...);      // 9 个方法
adapter.init(appContext, cfg, new ICarInitCallback() {
    @Override public void onInitSuccess() {          // 无参
        adapter.startAdvertise();                    // 开始广播，等手机发现
        adapter.startCast(surface, 1920, 720);       // surface = 一个纯色 SurfaceView
    }
    @Override public void onInitFailed(int code) { }  // ★ 只有一个 int 参数
});
```

> ⚠️ **三处早期推测已被实测证伪**：① **没有 `stopCast()`**（只有 `pauseCast()` / `disconnect()` / `stopAdvertise()`）；② `setCarBrMac()` 收 **`byte[]`**；③ `onInitFailed()` 只有**一个 int 参数**。完整签名见 `poc1/README.md` §三。

#### 9.2.4 判定标准

| 观察点 | 通过 | 不通过 |
|---|---|---|
| 手机 BLE 扫到车机 | ✅ 手机弹出识别提示 | 🔴 完全没反应 → 检查 `BLUETOOTH_PRIVILEGED` 是否被拒（R3） |
| 配对 | ✅ 弹 PIN 码 + 确认框 | 🔴 提示"非认证设备" → **R2 命中，需换更新样本** |
| `onInitSuccess` | ✅ | 🔴 报错 → 看错误码，多半是权限或 so 未加载（`armeabi-v7a` 下样本的 so 会加载失败；MiAuto 自身所需 so 四 ABI 齐全，见 R5） |
| `startCast` 后 | ✅ 车机出现纯色画面 | 🔴 手机弹"协议版本不支持" → **R1 命中** |

完整判定表（含 `onInitFailed(10001)`、`onReconfigureWifiInfo` 等分支）见 `poc1/README.md` §五。

### 9.3 为什么 POC-2 才是真正决定性的

**POC-1 通过只说明"能连"**；POC-2 才回答**"连上之后有没有价值"**：

> 如果从 ICCOA 拿到的导航信息（方向图标/距离/路名）接不进 DiAuto 的 BYD 仪表盘/HUD，那 MiAuto 就退化成一个"能投屏的车机 App"——而现成 demo（方案 A）已经做到了，**项目就没有存在意义**。

所以 **POC-2 的结论直接决定 GO / NO-GO**，不要跳过。

### 9.4 已识别的技术坑（POC-1 实战修订）

#### 9.4.1 五个决定性的工程坑（已踩过，含解法）

| # | 坑 | 表现 | 解法 |
|---|---|---|---|
| **1** | **AGP 不消费 `.dex` 文件依赖** | `implementation(files("*.dex"))` 与 `runtimeOnly` 都**不进 APK**；`mergeLibDexDebug` 任务正常执行但输出为空，产物里 `com.ucar.*` 类数 = 0 | 打包完成后把 SDK dex 作为 **`classes4.dex`** 追加进 APK（Android 原生支持 `classes1..N.dex`，由 `BaseDexClassLoader` 全部载入），再 `zipalign -p -f 4` + `apksigner sign`。见 `tools/inject_dex.py` / `tools/package-poc.sh` |
| **2** | **dex 不进 javac 编译类路径** | `import com.ucar.vehiclesdk.UCarAdapter;` 报「程序包不存在」 | 从 smali 自动生成**纯签名存根**（方法体返回零值），以 `compileOnly` 引入，不进 APK。见 `tools/dex2stub.py` |
| **3** | **只取 `com.ucar.*` 会在运行期炸** | 早期版本只打包 SDK（1.69 MB），运行到 BLE 广播或鉴权就 `ClassNotFoundException` | SDK 引用了 4 个第三方库必须一并打包：`com.google.protobuf`(9166 引用) / Guava `com.google.common`(37) / `com.google.gson`(30) / `com.google.crypto.tink`(5)。**位置分散**：Guava 在 `classes.dex`，protobuf/gson/Tink 在 `classes3.dex` |
| **4** | **★ 跨 dex 漏类 → 点启动必闪退**（真机复现） | 用户实测「**点启动按钮就闪退**」。根因：`collect_sdk_smali.py` 早期按「dex → 包」**硬分配**（假设 Tink 在 `classes3`、Guava 在 `classes`），但 **Tink 的 86 个核心类实际在 `classes.dex`**、`com.easy.logger` 3 个类在 `classes6.dex`；且 `build-sdk.sh` 只反汇编 6 个 dex，**从未处理 `classes6.dex` / `classes8.dex`**。结果 `init()` → 密钥协商 `KeyNegotiator` 时 `NoClassDefFoundError` 直接崩 | ①`collect_sdk_smali.py` 改为**跨 dex 全局按包前缀筛选**，自动扫描全部 `classes*.dex`；②`build-sdk.sh` 的 dex 列表改为**动态扫描**，不再手工列举。校验脚本 `tools/dex_dangling_refs.py`（DEX 字符串池求差集）+ `tools/trace_missing_classes.py`（溯源缺失类在哪个 dex） |
| **5** | **★★ `setCarBrMac()` 收 6 字节二进制，不是 MAC 字符串** | 用户第二次实测：「**崩溃：startPipeline**」（崩溃兜底生效，异常被捕获）。根因：`UCarConfig$Builder.setCarBrMac(byte[])` 的字节码是**硬长度校验**——<br>`if (brMac != null && brMac.length == 6) { arraycopy(...); return this; }`<br>`EasyLog.e("UCarConfig", "Car Br Mac is invalid, the length must be 6 size!");`<br>`throw new InvalidParameterException("Car Br Mac is invalid");`<br>而早期实现写的是 `a.getAddress().getBytes()` → **17 字节 ASCII**（`"AA:BB:CC:DD:EE:FF"`），一进 `buildConfig()` 就抛 | 照样本 `UCarDemoActivity.getBluetoothMac()`（smali:2070）**逐行等价**复刻：<br>`byte[] macBytes = new byte[6];`<br>`String[] seg = mac.split(":");`<br>`for (i<6) macBytes[i] = (byte) Integer.parseInt(seg[i], 16);`<br>拿不到 MAC（`null` / 段数≠6 / 抛 SecurityException）时用 `new Random().nextBytes(macBytes)` **随机 6 字节兜底**。<br>⚠️ **旧缓存必须作废**：SharedPreferences 里可能存着上一版的 17 字节 base64，`generateCarId()` 解码后**校验长度 == 6**，不符就丢弃重生成（否则老值继续抛） |

> 五个坑的完整推导与复现命令见 [`../tools/README.md`](../tools/README.md) 与 [`../poc1/README.md`](../poc1/README.md)。

#### 9.4.1.2 `UCarConfig$Builder` 的硬校验表（越界即抛 `InvalidParameterException`）

字节码里逐条挖出来的约束 —— **任何一条不满足都直接崩**，且异常发生在 `startPipeline` 的同步路径上：

| 方法 | 约束 | 报错消息 |
|---|---|---|
| `setCarBrMac(byte[])` | 长度**必须恰好 6**（且非 null） | `Car Br Mac is invalid` |
| `setDpi(int)` | ∈ [120, 640] | `dpi must be between 120 and 640` |
| `setRealDpi(int)` | ∈ [120, 640] | `real dpi must be between 120 and 640` |
| `setFps(int)` | ∈ [15, 60] | `fps must be between 15 and 60` |
| `setScreenWidth/Height(int)` | **不得为负** | `screen width/height cannot be negative!` |
| `setVideoDisplayWidth/Height(int)` | **不得为负** | `video display height cannot be negative!` |
| `setCarCustomField(byte[])` | 长度**必须恰好 2** | `Car custom filed is invalid, the length must be 2 size!` |
| `setAllowed5gChannels(int[])` | 非 null，每项 ∈ [36, 165] | `5G channel must be between 36 and 165` / `channels can't be null` |
| `setBleRSSIThreshold(int)` | 必须为负且 ≥ −80 | `The bluetooth rssi threshold must be negative value!` |

未调用的项靠 Builder 构造函数的默认值兜底（默认 `mCarBrMac = new byte[6]`、`mCarCustomField = new byte[2]`、`mAllowed5gChannels` = 9 项内置表、`mBleRSSIThreshold = -60`）。

**POC 的做法**：`buildConfig()` 里对 `dpi` / `realDpi` / `fps` 一律 **clamp 后再传**（车机的 `densityDpi` 完全可能超出 [120,640]），屏幕/视频尺寸用 `Math.max(1, …)` 保证非负。

> 提取这些约束的脚本手法：切分 `.method` 块 → 找含 `InvalidParameterException` 的方法 → 抓 `const-string` 消息 + `const/16` 边界值。

#### 9.4.1.1 存根保真度坑（真 SDK 字段是 `static`）

| 坑 | 表现 | 解法 |
|---|---|---|
| **存根丢失字段 `static`** | 真 SDK 里 `UCarCommon$WorkMode.APP_WINDOW_CAST` 等是 **`public static final`**（smali `.field public static final`），但早期 `dex2stub.py` 的字段生成**完全忽略 smali 修饰符**、恒发 `public `，把静态常量降级成**实例字段** → 调用方写 `WorkMode.APP_WINDOW_CAST` 报 `non-static variable cannot be referenced from a static context` | `_field_lines()` 改为读取 smali 的 `fmods`，含 `static` 即输出 `static`。对象类型字段初值用 `null` 即可（`static final X = null` 合法） |

#### 9.4.2 逆向工具链坑

| 坑 | 表现 | 解法 |
|---|---|---|
| **jadx 对样本挂起** | 14 MB / 8 dex / 大量 protobuf，**>20 分钟无产出**（连 793 KB 的单 dex 都跑不出），`timeout 240` 退出码 124 | 见 9.5 —— 最终放弃 jadx，改走 **smali 往返** |
| **androguard 4.1.4 API 陷阱** | `MethodAnalysis.get_source()` **不存在**（与 `ClassDef.get_methods()` 返回的 `EncodedMethod` 是两个 API） | 用 `m.get_code().get_bc().get_instructions()` 做**字节码级 dump** |
| **常量取不到** | `_const.txt` 只采到 `<clinit>` 里的 `const-string`，`DEFAULT_CCD_FILE` 是索引引用 | 从 **DEX 字符串池全量扫描**（header `string_ids_size` @ `0x38`、`string_ids_off` @ `0x3C`，uleb128 长度前缀） |
| **Windows Python 读不了 `/d/` 路径** | `FileNotFoundError` | 用 `r"D:\Workbuddy_Work\APK_test\apk\..."` |
| **`bytes(j['content'])` 报 ValueError** | 数组含负数 | `bytes(x & 0xFF for x in j['content'])` |
| **沙箱拦截批量删除** | `shutil.rmtree` 报 `[SAFE_DELETE_BULK_CONFIRM_REQUIRED]`（981 个文件），中断脚本、汇编到旧目录 | 脚本改用**时间戳化新目录**，不依赖删除 |
| **目录复用导致 dex 被撑大** | 复用 `.work/sdk_full` 残留 `com/ucarhu` → dex 被撑大（曾出现 6,796,832 B 的异常值） | 每次新目录 + 明确 `DROP` 规则 |

#### 9.4.3 启动逻辑与「链接码」——对齐样本（用户实测反馈）

样本（智慧车联）的行为是：**打开 App 即自动进入连接流程并显示一个「链接码」**，手机端随即弹出输入提示。
早期 POC 却是「手动点启动按钮」，且按钮点击**必闪退**（坑 4）。三点已全部对齐：

**① 打开即自动初始化（无需按钮）**

样本 `UCarDemoActivity$1`（一个 `Runnable`，由**布局完成回调**触发）：

```smali
.method public run()V
    ...
    sget-boolean v0, Lcom/ucarhu/demo/UCarDemoActivity;->sIsServiceInitialized:Z
    if-nez v0, :cond_x
    invoke-static {}, ...->initUCarSDK()V      # ← 打开界面、布局完成即初始化
```

→ POC 已在 `onCreate` 末尾 `main.postDelayed(this::autoStart, 400)`，`autoStart()` 幂等，等价于样本行为；「启动」按钮保留为手动重试入口。

**② 「链接码」= SDK 的 PIN 码，参数语义曾写反（★ 关键）**

样本 `UCarDemoActivity$10.smali:205` 是**调试信息级铁证**：

```smali
.method public onPinCode(Ljava/lang/String;Ljava/lang/String;)V
    .param p1, "pinCode"        # ← 第 1 个参数才是「链接码」
    .param p2, "carDeviceName"  # ← 第 2 个是车机名
```

样本把 `p1` 放进 `Bundle.PIN_CODE`、`p2` 放进 `Bundle.DEVICE_NAME`，再用自定义控件 `UCarPinCodeView` 显示。

→ 早期 POC 写成 `onPinCode(String phoneId, String pinCode)`，**两个参数整个反了**：Toast 弹出来的是**车机名**而不是配对码。这既解释了对不上样本观感，也是用户质疑的直接来源。现已按 `(pinCode, carDeviceName)` 修正，并在投屏区用 **64sp 大字 + 逐位分隔**显示码值；连接建立（state ≥ 2）后自动收起。

**③ `onSelectWorkMode` 应「挑一个」而非「退回去」**

样本原文逻辑：SDK 交给车机的是「车机自身支持的**集合**」，车机必须从中挑出**唯一一个**返回：

```
isSupportAppWindowCast()      → APP_WINDOW_CAST
isSupportAppFullScreenCast()  → APP_FULLSCREEN_CAST
isSupportNormalCast()         → NORMAL_CAST
isSupportDataTransfer()       → DATA_TRANSFER
否则                          → NONE
```

→ 早期 POC `return mode;`（原样退还整个集合）是错的，已按上式修正。

### 9.5 jadx 挂起时的替代方案（★最终采用 smali 往返）

| 方案 | 可行性 |
|---|---|
| **★ smali 往返**（baksmali 反汇编 → smali 重新汇编） | ✅ **最终采用** —— 100% 字节级保真，`<clinit>` / `-$$Lambda$` 合成类 / 泛型签名全部原样保留，产物可直接当依赖。见 `tools/build-sdk.sh` |
| **jadx 限线程 + 加大内存** | ⚠️ `jadx --threads-count 1 -Xmx4g` 对本样本**仍挂起**；其他包可先试 |
| **androguard 字节码 dump** | ✅ 可用 —— 能拿到方法调用序列与字符串引用，**足够还原 API 用法**，但拿不到可编译源码（本 POC 用它做交叉校验） |
| **换更小的样本** | 从其他车型的车机端包找 ICCOA SDK（只含 SDK 的包会更小，反编译更快） |

> **实测结论**：本样本（14 MB / 8 dex）**jadx 与 dex2jar 都不可用**（jadx 无产出；dex2jar 的 Maven 坐标查询超时 / 只有 aggregator pom）。
> **最终路线 = smali 往返**：`baksmali 2.5.2` 逐 dex 反汇编 → 按包筛选 → `smali 2.5.2` 重新汇编成单个 dex（消除跨 dex 引用）→ 作为 `classes4.dex` 注入 APK。这套流程已固化为 `tools/build-sdk.sh`，换样本只需改 `collect_sdk_smali.py` 的 `RULES` 表。

---

### 9.6 MiAuto 架构重建：以 DiAuto 为基座（2026-10-03 夜）

**触发**：用户报告「**HUD 开关和仪表投屏等设置怎么消失了？应该和 DiAuto 保持一致**」。

**根因（代码级）**：上一版 MiAuto 是从 POC-1 派生的**极简 Java 工程** —— `repos/MiAuto/app` 只有
4 个类（`MiAutoApp` / `IccoaManager` / `NavigationCluster` / `MainActivity`）+ 1 个布局 `activity_main.xml`，
**没有菜单入口、没有任何 `Settings`/`Preference`/`SharedPreferences` 设置存储**。
而 DiAuto 的设置体系是 `main/SettingsActivity.kt` + `main/SettingsFragment.kt`（3644 行）+ `utils/Settings.kt`（1653 行），
按 **13 个分类**组织：常规 · 无线连接 · 深色模式 · 自动化 · **导航** · 图形 · 视频 · 输入 · 音频 · 备份 · 重置 · 调试 · 信息。
用户点名的两项正是导航分类里的：

| 设置项 | key | 位置 | 门控 |
|---|---|---|---|
| **仪表投屏** | `cluster-map-enabled` | `SettingsFragment.kt:1225` | 无条件显示（注释：能自我隐藏的控件无法与「功能没做」区分） |
| **投屏时机**（常开/巡航和导航/仅导航）| `cluster-map-trigger` | `SettingsFragment.kt:1245` | 无条件显示（`SegmentedButton`） |
| **HUD（比亚迪抬头显示和仪表盘）** | `byd-navigation-enabled` | `SettingsFragment.kt:1203` | **`BydNavigationOutputs.available(ctx)`** —— SDK≥28 且（独立 HUD 可用 或 装了 `com.byd.amapservice`/`com.ts.car.someip.service`）。**这就是「HUD 未识别时不显示该开关」**；旁边还留了无条件的「比亚迪通道诊断」入口解释开关为何缺失（`SettingsFragment.kt:1219`）|

**决策**：以 `repos/DiAuto` **完整派生**为 MiAuto 基座，保留 DiAuto 全部 UI 与设置，把手机侧协议换成 ICCOA。

**执行（可复现）**：

1. 归档旧版 → `repos/MiAuto-poc-legacy`；`tar` 排除 `.git/.github/build/.gradle/.cxx/site` 复制 DiAuto → `repos/MiAuto`（275 个 `.kt/.java` + `cpp` + `jniLibs` + `contract` 模块）。
2. 品牌化：`settings.gradle.kts` `rootProject.name=MiAuto`；`app/build.gradle.kts` `applicationId=com.miauto.app` / `versionName=0.1.0` / debug 后缀 `.debug`；`app/src/debug/res/xml/shortcuts.xml` 7 处 `targetPackage` 同步（否则 `androidComponents` 的漂移检查会让构建失败）→ 21 个语言包的 `title`/`app_name` → `MiAuto`；debug manifest 的 `android:label` → `MiAuto (debug)`。
3. 图标：删 DiAuto 的 `mipmap-*/*.webp` + `mipmap-anydpi/` + `values/ic_launcher_background.xml`，换入 MA 毛玻璃 PNG + 自适应 XML（`@mipmap/ic_launcher_background` + `@mipmap/ic_launcher_foreground`）。
4. 签名：删除 DiAuto 的 `headunit-release-key.jks`，`keytool` 生成 `miauto-release-key.jks`（alias `miauto`），更新 `key.properties`。
5. 移植 ICCOA：`app/libs/icar-sdk.dex`（7,084,144 B，含 `MiAutoCar`）+ `app/stubs/icar-sdk-stubs.jar` + `jniLibs/arm64-v8a/{libovmsink,libusbio}.so` + `assets/ccd.data`；
   `app/build.gradle.kts` 加 `compileOnly(files("stubs/icar-sdk-stubs.jar"))` 与 **`injectIccoaDex`** 任务（`afterEvaluate` 里对 `assembleGithubDebug`/`assemblePlaystoreDebug` 挂 `finalizedBy`；纯 `java.util.zip`，追加 `classes4.dex` → `zipalign -p -f 4` → `apksigner` 重签）。
6. 保留内部包名 `com.andrerinas.openheadunit`（避免 275 文件 + JNI/proto/AIDL 批量改名风险），只改 `applicationId`。

**产物验证**（`./gradlew :app:assembleGithubDebug`，BUILD SUCCESSFUL）：

| 项 | 值 |
|---|---|
| APK | `com.miauto.app.debug_0.1.0-debug_debug.apk`，**20,738,640 B**，SHA-256 `1c060f38463e3f6089edeaab08c13cc3c6d2824b298535f39ef3c49cd5ff727a` |
| 包名 / 名称 | `com.miauto.app.debug` / `MiAuto (debug)` |
| 图标 | `application: icon='res/mipmap-anydpi-v26/ic_launcher.xml'`，22 个图标条目 |
| dex | `classes.dex` + `classes2` + `classes3` + **`classes4.dex`(7,084,144 = ICCOA SDK)**；`MiAutoCar`×1、`ICCOA汽车`×0 |
| 原生库 | `lib/arm64-v8a/libovmsink.so`、`libusbio.so`（+ 基座自带 `libavcodec/avutil/swscale/libusb1.0`） |
| 签名 | `apksigner verify` → **EXIT=0** |

**下一步：ICCOA 协议接线（需真机迭代）**。探查确认的最干净接缝：

| 目标 | 接缝 | 位置 |
|---|---|---|
| 传输 | 新增 `IcocaConnection : AccessoryConnection`（SDK 自带传输）| `connection/AccessoryConnection.kt`（7 成员接口）；`CommManager.kt:270/321/355` 注入点 |
| 跳过 AA 握手 | 短路 `commManager.startHandshake()` | `AapService.kt:1029`；`AapProjectionActivity.kt:1563` |
| 视频渲染 | 把 `onSurfaceChanged` 的 `Surface` 交给 SDK，绕过 `videoDecoder` | `AapProjectionActivity.kt:1516 / 1596` |
| 连接状态 | 继续产出 `CommManager.ConnectionState` 7 态，UI 零改 | `CommManager.kt:86-117` |
| **导航 → BYD 仪表/HUD** | `onNavigationInfoReceived` → 构造 `BydGuidance` → `BydNavigationOutputs.update(...)` + `NavigationActivitySignal.*` | `hud/BydNavigationOutputs.kt:71`；`cluster/NavigationActivitySignal.kt` |
| 会话生命周期 | `BydNavigationOutputs.start/stop`、`ClusterProjectionController.onSessionStarted/Stopped` | `AapService.kt:996 / 1161` |
| 装配 | 替换 `AppComponent.commManager` | `AppComponent.kt:25` |

> ⚠️ **已知不确定项**：ICCOA 的 `NavigationInfo.getOperation()` 是**文本**（`getDirectionIcon()` 是 `byte[]`，真实格式未知），
> 而 `BydNavigationMapper.codes(type, exit)` 的入参是 **Android Auto 的 maneuver 枚举数字**。
> 因此「ICCOA 导航 → BYD 图标码」需要真机抓 `onNavigationInfoReceived` 的原始字段后校准映射表。

---

### 9.7 ICCOA 协议接线 + 真机「连不上」的四个根因（2026-10-03 深夜）

用户真机反馈：「**装上就是 DiAuto，没有任何不同，也没有链接码**」「**手机不弹窗连接，点击开始配对也无效**」。
逐条定位到**四个根因**，其中 (c)(d) 是**真正让 SDK 静默失效的两颗地雷**。

#### 9.7.1 根因 (a)：SDK 全工程零调用

```bash
grep -rn "IccoaManager\|UCarAdapter" app/src/main/java
# → 只命中 IccoaManager.java 自己
```

资产搬进来了、编译通过了、`classes4.dex` 也注入成功了 —— **但没有任何代码 new 过 `IccoaManager`**。
`UCarAdapter.init()` / `startAdvertise()` 一次都没执行 → BLE 没广播 → 手机发现不了车机。
**「编译通过 ≠ 功能存在」**。

**修复**：新增 `connection/IcocaBridge.kt`（把 Java 回调式 SDK 折成 `StateFlow<State>`：`Idle` / `Advertising` /
`Pairing+配对码` / `Connected`），`AppComponent` 里做进程级单例（SDK 每进程只能 init 一次），
`MainActivity.onCreate` 启动广播、`Connected` 时自动拉起投屏页。

#### 9.7.2 根因 (b)：首页文案与首启向导仍是 DiAuto/Android Auto

- `res/values*/diauto.xml` 的 `da_brand` = `DiAuto`、标语全是 Android Auto 语义；
- 因换了 `applicationId` 等于全新安装，`checkSetupFlow()` 必弹 AA 设置向导（内含 "Welcome to DiAuto"）。

**修复**：5 个语言包（`values` / `-ar` / `-es` / `-ru` / `-zh-rCN`）全量改写为 MiAuto + ICCOA CarWith 语义；
`checkSetupFlow()` 改为只申请权限、不再进 AA 向导（车机侧无需配置，那向导纯属误导）。

#### 9.7.3 ★ 根因 (c)：SDK 的清单组件与权限随清单合并一起丢失

**这是「手机扫不到车机、界面无配对码」的直接根因。**

一个 AAR/APK 形态的 SDK 有**四样东西**，把 SDK 抽成 `.dex` 只搬走了第一样：

| SDK 的组成部分 | 只抽 dex 时 | 后果 |
|---|---|---|
| 类（dex） | ✅ 带走 | —— |
| **`<service>` / `<receiver>` / `<provider>` 组件** | ❌ 随清单合并一起丢 | 系统无法启动 SDK 的 Service → **静默失效，零报错** |
| **`<uses-permission>` / `<uses-feature>`** | ❌ 丢 | 权限不足 → API 抛异常或静默失败 |
| **传递依赖库**（Gradle 坐标，包名前缀不属于 SDK 自身） | ❌ 丢 | 运行到该路径 `NoClassDefFoundError` |

从 DiAuto 基座派生后，MiAuto 的 `AndroidManifest.xml` 里**一个 `com.share.connect.*` 服务都没有**，
而 SDK 的**整个传输栈**都以独立 Service 形式运行：

| Service | 作用 |
|---|---|
| `com.share.connect.ble.BluetoothLeService` | **BLE 广播 / 发现**（手机能看到「MiAutoCar」全靠它） |
| `com.share.connect.ShareLinkService` | 会话共享与链接协商 |
| `com.share.connect.wifiap.WifiApService` | SoftAP 直连（投屏数据通道之一） |
| `com.share.connect.wifip2p.WifiP2pService` | WiFi P2P 直连（投屏数据通道之二） |
| `androidx.room.MultiInstanceInvalidationService` | Room 跨进程失效（信任设备 Peer 表） |

**修复**：把这 5 个 Service 显式注册进主 `AndroidManifest.xml`，并逐条对齐样本的权限
（`QUERY_ALL_PACKAGES` / `MANAGE_USB` / `CAMERA` / `VIBRATE` / `BLUETOOTH_PRIVILEGED` /
`ACCESS_BACKGROUND_LOCATION` / `LOCAL_MAC_ADDRESS` / `NETWORK_SETTINGS` / `NETWORK_STACK` /
`TETHER_PRIVILEGED` / `OVERRIDE_WIFI_CONFIG`）+ `uses-feature bluetooth_le` + nav app `queries`。

#### 9.7.4 ★ 根因 (d)：SDK 的 Room 运行期依赖丢失

`grep` 字符串池看到 `Landroidx/room/RoomDatabase;` **不能证明类在其中** —— 字符串池同时收录**引用**。
必须解析 dex 的 `class_defs` 取「已定义类集合」：

```
sdk.dex  defined=4740
  Lcom/share/connect/ble/BluetoothLeService;   DEFINED
  Lcom/ucar/vehiclesdk/UCarAdapter;            DEFINED
  Landroidx/room/RoomDatabase;                 NOT-DEFINED   ← 只有引用！
  Landroidx/room/*  定义数 = 0 / 引用数 = 25
```

SDK 用 Room 维护「信任设备(Peer)」表，而 `androidx.room` 来自 **Gradle 坐标**（不属于 SDK 自身包前缀，
因此没被抽进 dex）。旧 poc 工程靠 `androidx.room:room-runtime:2.4.3` 提供；新基座**没有这个依赖**。

**修复**：`app/build.gradle.kts` 加 `implementation("androidx.room:room-runtime:2.4.3")`
（版本与旧 poc 一致；先确认本机 Gradle 缓存已有 → 可离线构建）。
**不要把 Room 也抽进自己的 dex** —— 会和宿主版本冲突且徒增体积。

#### 9.7.5 附带修复：启动时机、UI 与资源隔离

| 项 | 问题 | 修复 |
|---|---|---|
| **启动时机** | `MainActivity.onCreate` 在运行时权限授予前就 `start()`；API 31+ 无 `BLUETOOTH_ADVERTISE/CONNECT` 时 `startAdvertise()` **静默失败** | 新增 `AppPermissions.bluetoothReady()` + 幂等 `ensureIcocaStarted()`（`onCreate` + `onResume` 各调一次，授予后自动补启） |
| **首页 UI** | 用户要求「移除开始配对 / 通过蓝牙配对按钮；配对码自动出现；断开后自动刷新；和荣耀车联一样」 | `fragment_home.xml`（含 `layout-port` 竖屏变体）改为无配对按钮；新增 `pairing_code_block` / `pairing_code` 大号配对码。`HomeFragment` 改为纯状态渲染，仅 `Connected` 时出现「打开投屏」按钮 |
| **断开刷新配对码** | 断开后无重新广播、且**残留过期配对码** | `IccoaManager`：`onConnectStateChanged(newState==0 && oldState!=0)` → 清 `lastPinCode` + 延时 `reAdvertise()`（手机重新发现 → 下发**新**配对码）；新增 `disconnectSession()`（只拆会话、保持广播）。`IcocaBridge.updatePhase()` 在非 `Pairing` 阶段清 `pinCode` |
| **AA 资源抢占** | `AapService.onCreate` 无条件 `enableCarMode()` / `MediaSession.isActive=true` / 构造 `WifiDirectManager` + `SoftApCredentialsProvider` + `LocalHotspotCredentialsProvider` + `NearbyManager` —— **正是 ICCOA 投屏数据通道要用的 WiFi P2P 组与 SoftAP** | 新增 `BuildConfig.ICCOA_ONLY = true`，在**构造函数入口**（`initWifiMode()` / `setupCarMode()` 内部 + 构造调用点）统一短路。这些字段全可空且下游一律 `?.`，跳过构造即自动 no-op |

#### 9.7.6 产物校验（全部通过）

| 项 | 值 |
|---|---|
| APK | `app/build/outputs/apk/github/debug/com.miauto.app.debug_0.1.0-debug_debug.apk` |
| 大小 / SHA-256 | **20,816,877 B** / `6f6f4b6befd18d5a0d0ad0cda33f097be490995c8e9b8c39c592100fe97d0c9c` |
| 包名 / label | `com.miauto.app.debug` / `MiAuto (debug)` |
| **合并清单 5 个 SDK 服务** | ✅ 全部在册（`aapt2 dump xmltree` 核对） |
| **Room 类进 APK** | ✅ `androidx/room` **184 类**（classes / classes2），`RoomDatabase` + `MultiInstanceInvalidationService` 均已定义 |
| **ICCOA SDK** | ✅ `classes5.dex`（7,084,144 B）：`com/ucar/vehiclesdk` **223 类** + `com/share/connect` **205 类** |
| 权限抽查 | ✅ `TETHER_PRIVILEGED` / `NETWORK_STACK` / `LOCAL_MAC_ADDRESS` / `OVERRIDE_WIFI_CONFIG` / `QUERY_ALL_PACKAGES` / `BLUETOOTH_ADVERTISE` 全在 |
| 门控编入 | ✅ `ICCOA-only build` 特征串命中 classes2.dex |
| 签名 | ✅ `apksigner verify` EXIT=0 |

#### 9.7.7 仍未做（诚实边界）

- ⏳ **导航 → BYD 仪表/HUD 映射未接**：`IcocaBridge.navigation` 已拿到 `NavigationInfo`，但
  `onNavigation → BydGuidance → BydNavigationOutputs.update()` 的转换尚未实现。故设置里的「仪表投屏」
  目前**只有镜像画面**，导航卡不会动。且 ICCOA 的 `getOperation()` 是**文本**、`getDirectionIcon()` 是 `byte[]`，
  而 `BydNavigationMapper.codes(type, exit)` 入参是 **AA maneuver 枚举数字** → 图标码需真机抓原始字段后校准。
- ⏳ **反向触控**未接（投屏当前只显示，不能触控手机）。
- ⚠️ **协议版本漂移风险**仍在（样本 v1.5.3 / 2024-01 vs CarWith 2026）。
- ⏳ **全自动逻辑待真机确认**：配对码自动出现/断开自动刷新需真机验证。

---

### 9.8 配对码自动刷新 + 品牌文案合规（2026-10-03 深夜·续）

#### 9.8.1 真机反馈

用户实测确认 **「能正常连接了」**（§9.7 的四个根因修复生效）。随后提出两组需求：

1. **配对码不再刷新**：「断开后刷新的链接码如果当时没有被连接，后面就不会继续刷新；手机关闭连接弹窗，也就不会触发连接弹窗了，无法再次连接。是否参考荣耀车联的逻辑，增加自动刷新周期，如果 30 秒没连接就重新刷一次新的链接码来触发弹窗，或者增加一个刷新配对码的按钮？」
2. **首页文案**：`小米 CarWith · 车机端` → `MiAutoCar · 车机端`；`ICCOA CARLINK · 小米 CARWITH` → `MIAUTO CARLINK · CARWITH`；「把小米手机投屏到这块车机屏幕。」→「把手机投屏到这块车机屏幕或仪表盘/HUD。」；**去掉所有「小米」字样**；底部加一行免责声明。

#### 9.8.2 根因：只做「断开后刷新一次」是不够的

§9.7 已实现「断开 → 清码 → 延时 1.2s 重播 → 手机重新发现 → 下发新码」。但这条链有一个隐含前提：
**手机端还会再来连一次**。一旦手机端 CarWith 的配对弹窗被用户关掉，前提就不成立了：

- 车机「一直在广播」**不等于**「手机能重新发现」。**BLE 广播帧内容不变时，手机端不会重新弹出配对界面**；
- 于是刷新出来的那张新码永远等不到人来连，码就「冻」在那里，用户只能重启 App 才能重连。

这正是荣耀车联 / HiCar 车机端要做**周期性重播**的原因 —— 用一次「广播内容的变化」把手机的发现流程重新触发一遍。

#### 9.8.3 实现（`com.miauto.app.IccoaManager`）

| 机制 | 实现要点 |
|---|---|
| **30 秒看门狗** | `AUTO_REFRESH_INTERVAL_MS = 30_000`；`autoRefreshRunnable` 到期检查，未连接则重播并再次调度 |
| **★ 重播 = 停-启** | `reAdvertise()` 内先 `stopAdvertise()` 再 `startAdvertise()`。**对已处于广播态的链路重复调用 `startAdvertise()` 会被 SDK 直接忽略**，广播帧不变 → 手机端不会有任何反应。这是本次最关键的一处修正 |
| **避让** | 新增 `sessionActive` 标志：`newState 2/3`（已连）置 true → 看门狗跳过；`castStarted`（画面已投出）同样跳过。`newState 1`（CONNECTING）**刻意不置位** —— 正常协商只需数秒，若卡死超过一个周期，看门狗需要能把它救回来 |
| **计时重置** | `onPinCode` 时 `scheduleAutoRefresh()`，保证用户拿到完整的 30 秒去确认，不会刚出码就被重播顶掉 |
| **生命周期** | `onInitSuccess` 后启动看门狗；`stop()` 里 `cancelAutoRefresh()`；`disconnectSession()` / 断开回调里重新计时 |
| **手动入口** | `refreshPairing()`（公开）→ 与看门狗同一条路径，只是不等 30 秒。经 `IcocaBridge.refreshPairing()` 暴露给 UI |

#### 9.8.4 UI：文案与法务口径

- 首页状态卡的 `da_wireless` 标签、`da_tagline` 品牌行、`da_description` 正文字面量全部改写（5 语言同步：`values` / `-zh-rCN` / `-ar` / `-es` / `-ru`）。
- 新增 `da_refresh_pin`（刷新配对码 / Refresh pairing code / …）。
- 新增 **`da_legal`**：首页最底部固定一行小字（`legal_footer` TextView，两个布局变体都加），声明 MiAuto 与 BYD 官方、ICCOA CarLink、小米 CarWith 及其商标持有者**均无隶属 / 授权 / 合作关系**，名称与商标归各自所有者，**仅供学习与技术交流使用、禁止商用**，后果由使用者自负。
- 「刷新配对码」按钮（`refresh_pin_button`，`OutlinedButton`）与「打开投屏」（`wifi_button`）**互为镜像**：未连接时显示刷新，已连接时只显示打开投屏。
- ⚠️ `layout-port/fragment_home.xml` 必须与 `layout/fragment_home.xml` 同步 —— `findViewById` 对缺失 id 返回 null，竖屏会直接崩。

#### 9.8.5 顺带修掉的隐患

`tryStartCast()` 原先在 Surface 无效/为空时会 `main.postDelayed(this::tryStartCast, 500)` **无上限自我重试**。
待机页（首页）根本没有 Surface，于是这条链变成**永不停止的 500ms 定时器**，而待机页恰恰是常驻的 —— 等于持续耗电。
现加 `CAST_RETRY_MAX = 20`（≈10 秒）预算，`setSurface()` 与 `reAdvertise()` 均重置计数；
Surface 稍后就绪的常见情形由 `setSurface()` 兜底触发。

#### 9.8.6 产物校验

```
com.miauto.app.debug_0.1.0-debug_debug.apk
  大小    20,825,069 B
  SHA-256 917fe3eaa0c46a5f5890b7cf36d295a5f80288673d4fc8103b6164a17d785163
  包名    com.miauto.app.debug    label  MiAuto (debug)
  dex     classes.dex(2,197,404) + classes2(9,648,168) + classes3(9,215,400) + classes4(493,492)
          + classes5.dex(7,084,144 = ICCOA SDK，含 MiAutoCar)
  附加    libovmsink.so / libusbio.so / assets/ccd.data
  清单    5 个 SDK 服务齐全（4× com.share.connect.* + androidx.room.MultiInstanceInvalidationService）
  文案    aapt2 dump strings 命中：MiAutoCar · 车机端 / MIAUTO CARLINK · CARWITH / MIAUTOCAR · HEAD UNIT
          / 刷新配对码 / 「仪表盘 / HUD」五语言版本 / da_legal 五语言版本；旧文案（XIAOMI CARWITH、
          小米 CARWITH、ICCOA CARLINK）**零命中**
  签名    apksigner verify → EXIT=0
```

---

## 10. 许可证与合规

| 组件 | 许可证 | 对 MiAuto 的影响 |
|---|---|---|
| **DiAuto Pro** | **AGPL-3.0**（LICENSE 661 行，另含 `COPYRIGHT_MICHAEL_REID_GPLv3AFFERO.txt`） | MiAuto **必须 AGPL-3.0 开源**，含网络服务条款 |
| **ICCOA CarLink SDK**（来自样本 APK） | ⚠️ **无 LICENSE**，来源授权范围不明 | **自用无碍；公开分发需澄清**，见 R7 / R8 |
| **`libovmsink.so` / `libusbio.so`** | ⚠️ **不明**（二进制） | 同上 |
| **`assets/ccd.data`** | ⚠️ **带溯源水印的测试证书** | **必须换成自造证书**（R6），不要原样分发 |
| **（备选）CarLife SDK（apollo-DuerOS）** | Apache-2.0 | 兼容 AGPL（Apache-2.0 → AGPL-3.0 单向兼容） |
| **小米 CarWith** | 专有 | **不接触** —— MiAuto 不逆向 CarWith、不打包、不模拟小米服务 |

**推荐做法**：
- MiAuto 采用 **AGPL-3.0**，与 DiAuto Pro 一致
- README 明确写三方来源：`DiAuto Pro (AGPL-3.0)` + `ICCOA CarLink 车机端 SDK（来源：车机端 APK 逆向 / 自研）`
- 保留 DiAuto Pro 的 fork 署名（AGPL 要求）
- **不声称"支持小米"或"官方 ICCOA"**，而是「实现了 ICCOA CarLink 车机端协议；小米 CarWith 可通过其 ICCOA 通道连接」
- **对外发布前必须裁决 R8**：自己开发、自己使用，不构成"发布 ICCOA 认证产品"；一旦要公开分发，需评估是否触碰联盟凭据条款

---

## 附录 A：ICCOA SDK 关键类索引

> 路径基于样本 APK 的 dex 反编译结果（包名前缀 `com.ucar.*` / `com.share.connect.*` / `com.ucarhu.demo.*`）。

| 类 / 路径 | 说明 |
|---|---|
| `com.ucar.vehiclesdk.UCarAdapter` | **SDK 门面**（单例），`init` / `startAdvertise` / `startCast` / `sendTouchEvent` / `sendKeyEvent` / `register*Listener` |
| `com.ucar.vehiclesdk.UCarConfig` + `UCarConfig.Builder` | 配置（dpi / fps / 分辨率 / P2P / SoftAP / HEVC / ccd 路径 / WiFi SSID…） |
| `com.ucar.vehiclesdk.UCarCertManager` | 证书管理，构造参数含 `config.getCcdFilePath()` |
| `CarVerificationData` | `ccd.data` 解析 + **`isValid()`（零密码学校验）** |
| `CarVerifier` | `getCcdReader()` / `getDefaultReader()`（默认读 `assets/ccd.data`） |
| `UCarCertProtocol` | `createCarCertificateMessage()` → `CmdCategory.CERT` |
| `KeyNegotiator` / `ServerKeyNegotiator` | ECDH 会话密钥协商 |
| `UCarPinCodeView` / `setPinCode` | PIN 码配对 UI |
| `com.ucar.protocol.*` | 协议编解码（CERT / AUTH / ACK 等） |
| `com.ucar.databus.proto.*` | `ucar.proto` 生成的 protobuf 类 |
| `com.ucar.connect.aoa.AOAConnectManager` | USB AOA 连接（配 `libusbio.so`） |
| `com.share.connect.ble.BluetoothLeService` | **BLE GATT 广播**（服务 `2abcc850-9935-4f8a-ba84-123456789100`） |
| `com.share.connect.wifiap.WifiApService` | 车机做 WiFi AP（走 `TetheringManager`） |
| `com.share.connect.wifip2p.WifiP2pService` | WiFi Direct（组名 `DIRECT-ICCOA-*`） |
| `com.share.connect.ShareLinkService` | 连接框架主服务 |
| `com.ucarhu.demo.UCarDemoActivity` | **官方 demo 主 Activity = launcher**，`initUCarSDK()` + Surface 投屏的**参考实现** |
| `com.ucarhu.demo.MyMediaSessionService` | demo 的媒体会话 |
| `lib/arm64-v8a/libovmsink.so` | **Miracast Sink**（内含 AOSP `WifiDisplaySink` / RTSP / RTP / UIBC） |
| `lib/arm64-v8a/libusbio.so` | USB AOA 原生支持 |
| `assets/ccd.data` | 车辆证书（2,120 B，带水印） |
| `ucar.proto`（APK 根目录） | **协议定义，658 行明文** |
| `test_pb_data.proto`（APK 根目录） | 测试用 protobuf 样例（209 B） |

## 附录 B：ICCOA 连接状态与通知（协议原文）

**投屏状态**（`NotifyMirrorState`）：

| 值 | 含义 |
|---|---|
| `STATE_STOP_MIRROR` | 停止投屏（保留连接） |
| `STATE_DISCONNECT_LINK` | 断开连接 |
| `STATE_GO_TO_DESKTOP` | 手机切回桌面 |
| `STATE_BACK_TO_FOREGROUND` | 手机应用回前台 |

**鉴权阶段**（`AuthRequest` / `AuthResponse` / `AuthConfirm`）：

| 消息 | 关键字段 | 说明 |
|---|---|---|
| `AuthRequest`（手机→车机） | `authPk` / `authPkHmac` / `agreementPk` / `agreementPkSig` / `random=Na` / `id` / `model` / **`userConfirmed`** / `productName` | 手机提交身份公钥 + 会话协商公钥，**`userConfirmed` 是配对确认标志** |
| `AuthResponse`（车机→手机） | `Nb` / `result` / `workModes` / `dayOrNightMode` / `screenInfo` | 车机回协商参数与结果 |
| `AuthConfirm`（手机→车机） | `cipher` | **会话密钥加密的手机本地 IP**，用于后续直连 |

> **握手不需要联网**：ECDH + ECDSA + HMAC 全在本地完成，密钥落 Android Keystore（`auth_key` / `session_key` / `uibc_session_key`），信任设备持久化在 Room 表 `Peer(id, auth_key, connection_time)`。

**通道类型**（`ChannelType`）：`CUSTOM` / `UIBC` / `RTSP` / `RTP` / `AUTH` / `CONTROL` / `MEDIA` / `SENSOR` / `CERT` / `VIDEO`

**投屏通道技术栈**：`libovmsink.so` 内含 AOSP `WifiDisplaySink.cpp` / `ANetworkSession.cpp`，走 **RTSP 协商**（`wfd_client_rtp_ports: RTP/AVP/`）+ **RTP 端口 19000** + **UIBC 输入回传**；视频 **H.264**（可选 HEVC）。

## 附录 C：待核实项（POC 时确认）

- ☐ **CarWith 的 ICCOA 侧协议版本**（对比车机侧 `ICCOA_PROTOCOL_VERSION_CODE_1_5`）—— R1 的核心
- ☐ 普通安装（非系统应用）下，8 个私有权限**具体哪几个被拒**，各自影响哪条传输路径 —— R3 的核心
- ☐ 车机 SoC 是否 `arm64-v8a`（`adb shell getprop ro.product.cpu.abi`）—— R5 的决定项
- ☐ 无线场景下 P2P 组名 `DIRECT-ICCOA-*` 是否必须保持前缀
- ☐ `cryptoConfigIndex`（样本值 `o-5T4plzjMmcilZs`）是否被用于选择加密算法套件（若被使用，自造证书要填对）
- ☐ `UCarConfig` 里 `setCarBrMac()` 传入非真实 MAC 是否影响无感连接
- ☐ 小米 CarWith 的 HUD 数据通道走 ICCOA 的哪个 message（`NotifyNavigationInfo` 还是自定义扩展）
