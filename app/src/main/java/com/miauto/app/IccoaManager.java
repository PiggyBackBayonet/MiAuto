package com.miauto.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.Surface;

import com.share.connect.WifiOwnerConfig;
import com.ucar.vehiclesdk.ICarConnectListener;
import com.ucar.vehiclesdk.ICarInitCallback;
import com.ucar.vehiclesdk.IPhoneDataListener;
import com.ucar.vehiclesdk.UCarAdapter;
import com.ucar.vehiclesdk.UCarCommon;
import com.ucar.vehiclesdk.UCarConfig;

import java.util.List;
import java.util.Random;

/**
 * MiAuto 车机端 ICCOA CarLink（小米 CarWith）SDK 生命周期管理器。
 *
 * <p>本类只管 SDK：初始化 / 广播 / 投屏 Surface / 回调转发。UI 与 BYD 输出通过
 * {@link Listener} 解耦（由 Activity 或 {@code IccoaNavigationBridge} 实现）。
 *
 * <p><b>★ init-once 状态机</b>：{@link UCarAdapter#init} 在 SDK 内部是「进程一次性」
 * （{@code sIsServiceInitialized} 守门）。重启只 {@code startAdvertise()}，绝不重复 init，
 * 否则 BLE 广播状态残留，手机扫不到、{@code onPinCode} 不再触发。
 * {@link #stop()} 断开但保留初始化标志。
 */
public class IccoaManager {

    private static final String TAG = "MiAuto/ICCOA";

    /** 事件回调。由 UI / 导航桥接实现。 */
    public interface Listener {
        void onLog(String msg);
        /** 已连接状态变化：0 断开 / 1 连接中 / 2 已连接 / 3 已建立 / 4 已连接(SDK 实测) / -1 瞬时未知 */
        void onConnectionState(int state);

        /**
         * 协商进度 0..100。
         *
         * <p><b>100 = 链路已建立</b>，这是「手机连上了」最可靠的信号：SDK 随后会把状态打成
         * {@code -1}（瞬时/未知），若只看状态码会把刚建立的会话误判成断开，投屏页就永远不会被
         * 拉起来 —— 而投屏页正是那个提供 Surface 的地方，缺了它手机连上也没地方画。
         */
        void onConnectProgress(int progress);
        void onPinCode(String pinCode, String carDeviceName);
        void onCastStarted();
        /** 手机导航信息（POC-2 / BYD 仪表与 HUD 的数据源）。 */
        void onNavigation(UCarCommon.NavigationInfo info);
    }

    private final Context appCtx;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private UCarAdapter adapter = null;
    private Surface surface = null;

    /** {@link #tryStartCast()} 的重试预算（×500ms = 最长 10 秒）。 */
    private static final int CAST_RETRY_MAX = 20;
    private int castRetry = 0;

    // ---- 状态机 ----
    private volatile boolean sdkInitialized = false;
    private volatile boolean sdkReady = false;
    private volatile boolean castStarted = false;
    private volatile String lastPinCode = null;

    /** 会话结束后重新广播的延时：留出 SDK 内部收尾时间，避免与断连流程竞争。 */
    private static final long RE_ADVERTISE_DELAY_MS = 1200L;

    /**
     * 配对码自动刷新周期。
     *
     * <p>车机一直在广播并不足以把手机叫回来：BLE 广播帧内容不变时，手机端 CarWith 不会重新
     * 弹出配对界面。用户一旦关掉那个弹窗，之后就必须手动去 CarWith 里找车机才能重连。
     * 荣耀车联 / HiCar 的车机端做法是周期性重播，本常量即那个周期。
     */
    private static final long AUTO_REFRESH_INTERVAL_MS = 30_000L;

    /**
     * 协商「僵住」多久之后允许看门狗重播把链路救回来。
     *
     * <p>手机一旦开始协商（{@code newState == 1}），看门狗就不能立刻重播 —— 否则首次配对
     * （用户要看车机上的 6 位码、再在手机上确认）会被打断、配对码被换掉，表现就是手机上
     * 「认证失败/配对失败」。但也不能像早先那样把 CONNECTING 当成「会话进行中」无限期避让：
     * SDK 若一直停在 CONNECTING 不回落到 DISCONNECTED，看门狗就再也不会重播，手机端那个
     * 「能自己回来的配对弹窗」也就永远不回来了（实测反馈：打完补丁后连提示都没有了）。
     *
     * <p>所以取中间值：协商期间每轮先让位，只有当「距最后一次协商进展」超过这个时长，才判定
     * 为僵死并重播。新配对码下发（onPinCode）与协商进度回调都会刷新这个计时。
     */
    private static final long STUCK_CONNECTING_MS = 45_000L;

    /**
     * 车机热点（SoftAP）开关。
     *
     * <p>{@code false} = 只宣称 WiFi P2P，不宣称「车机做热点」。
     *
     * <p>实测依据（2026-10-14，HUAWEI MRX-W09 / Android 12 作车机端，手机为 vivo X300）：
     * 关掉 EasyLog 的缺类问题后，SDK 能 `✓ startAdvertise() = true`、能下发配对码，但协商到
     * <b>{@code onConnectingProgress 10} 就掉回 0</b>，随后 SDK 报 {@code STATE_10003} / {@code STATE_10008}。
     * 这与社区车机端圈子记录的现象完全一致 —— 「vivo 卡在 10%，解决方法是去掉 AP」
     * （见 https://www.cnblogs.com/gnz48/p/18346101 ）。
     *
     * <p>机理：{@code WifiApService} 建热点要 {@code TETHER_PRIVILEGED} / {@code OVERRIDE_WIFI_CONFIG}
     * / {@code NETWORK_SETTINGS}，这些是 {@code signature|privileged} 权限，**侧载应用永远拿不到**，
     * 所以热点这条数据通道根本不可能起来；但车机仍在 {@code GetUCarConfig} 里向手机宣称「我支持 AP」，
     * 手机（vivo/OPPO 尤其）会优先选 AP，把会话建在一个不存在的热点上 → 卡在 10% 后失败。
     * 只留 P2P 时，{@code WifiP2pService} 只用普通权限（API 31 定位 / API 33+ NEARBY_WIFI_DEVICES），
     * 才有机会真正把组拉起来。
     *
     * <p>若你的车机是系统签名/预装（真的能开热点），改成 {@code true} 即可恢复两种通道。
     */
    private static final boolean SUPPORT_SOFT_AP = false;

    /** 会话是否已真正建立（手机已连上）。由连接回调维护，看门狗据此避让。 */
    private volatile boolean sessionActive = false;

    /** 进入 CONNECTING 的时刻；0 表示当前不在协商中。 */
    private volatile long connectingSince = 0L;

    /** 最后一次「协商有进展」的时刻（onPinCode / onConnectingProgress）。 */
    private volatile long lastConnectProgressAt = 0L;

    /**
     * 会话结束后的重新广播。
     *
     * <p>车机端必须一直可被发现：手机断开后若不再广播，用户就得手动重启 App 才能重连。
     * 重新广播时会清掉旧配对码，手机因此走一次全新配对并下发【新的】配对码 —— 这就是
     * 「断开后自动刷新链接码」。
     */
    private final Runnable reAdvertiseRunnable = () -> {
        if (sdkReady && adapter != null) {
            log("会话结束 → 重新广播（等待手机再次连接，届时下发新的配对码）");
            reAdvertise();
        }
    };

    private void scheduleReAdvertise() {
        main.removeCallbacks(reAdvertiseRunnable);
        main.postDelayed(reAdvertiseRunnable, RE_ADVERTISE_DELAY_MS);
    }

    /**
     * 配对码看门狗：每隔 {@link #AUTO_REFRESH_INTERVAL_MS} 检查一次，仍未连上就重播一次。
     *
     * <p>这是「刷新出来的新码没人来连，之后就再也不刷新」的解法 —— 那种情况下手机端弹窗
     * 已经关掉，车机只能靠重播把它重新叫出来，否则用户点开 CarWith 也发现不了车机。
     *
     * <p>避让规则只有两条：
     * <ul>
     *   <li>手机<b>已经连上</b>（{@link #sessionActive}）或画面已经投出去（{@code castStarted}）
     *       —— 绝不打断进行中的会话；</li>
     *   <li>手机<b>正在协商</b>且距最后一次进展不超过 {@link #STUCK_CONNECTING_MS}
     *       —— 给首次配对留出看码/确认的时间，超时未果则重播救回。</li>
     * </ul>
     */
    private final Runnable autoRefreshRunnable = () -> {
        if (!sdkReady || adapter == null) return;
        if (sessionActive || castStarted) {
            log("自动刷新：会话已建立，跳过本轮");
            scheduleAutoRefresh();
            return;
        }
        long now = System.currentTimeMillis();
        long anchor = Math.max(connectingSince, lastConnectProgressAt);
        if (anchor > 0 && connectingSince > 0 && now - anchor < STUCK_CONNECTING_MS) {
            log("自动刷新：手机正在协商（" + ((now - anchor) / 1000) + "s 无进展，"
                    + (STUCK_CONNECTING_MS / 1000) + "s 后才重播），本轮让位");
            scheduleAutoRefresh();
            return;
        }
        log("★ 自动刷新：超过 " + (AUTO_REFRESH_INTERVAL_MS / 1000) + " 秒未连接 → 重播以重新触发手机弹窗");
        reAdvertise();
        scheduleAutoRefresh();
    };

    private void scheduleAutoRefresh() {
        main.removeCallbacks(autoRefreshRunnable);
        main.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL_MS);
    }

    private void cancelAutoRefresh() {
        main.removeCallbacks(autoRefreshRunnable);
    }

    // ---- 屏幕尺寸 ----
    private int screenW = 1920, screenH = 720, videoW = 1920, videoH = 720;

    public IccoaManager(Context ctx, Listener listener) {
        this.appCtx = ctx.getApplicationContext();
        this.listener = listener;
    }

    public void setScreenSize(int w, int h) {
        screenW = w; screenH = h; videoW = w; videoH = h;
    }

    /** 投屏显示区宽度 —— 与 {@code UCarConfig.setVideoDisplayWidth()} 声明的一致。 */
    public int getDisplayWidth() { return videoW; }

    /** 投屏显示区高度 —— 与 {@code UCarConfig.setVideoDisplayHeight()} 声明的一致。 */
    public int getDisplayHeight() { return videoH; }

    /**
     * 把车机侧的触摸事件回传给手机（SDK 的 UIBC 回传通道）。
     *
     * <p>参数与 {@code UCarAdapter.sendTouchEvent} 完全对应，签名来自 SDK 的
     * {@code MethodParameters}：{@code (action, count, trackID[], x[], y[])}。
     *
     * <ul>
     *   <li>{@code action}：直接用 {@link android.view.MotionEvent#getAction()} 的原始值
     *       （**不要** {@code actionMasked}）—— 参考实现 SDK 自带的
     *       {@code com.ucar.vehiclesdk.UCarSurfaceView.onTouchEvent()} 就是这么传的；</li>
     *   <li>{@code trackID[i]}：{@code event.getPointerId(i)}；</li>
     *   <li>{@code x[i]} / {@code y[i]}：**display 空间**坐标（即 {@code videoDisplayWidth/Height}
     *       那块区域）。SDK 内部会按 {@code x / displayWidth * sourceWidth} 换算成手机屏幕坐标，
     *       所以这里传 SurfaceView 上的坐标时，要先按视图尺寸缩放到 display 尺寸。</li>
     * </ul>
     */
    public boolean sendTouchEvent(int action, int count, int[] trackId, int[] x, int[] y) {
        if (adapter == null) return false;
        try {
            return adapter.sendTouchEvent(action, count, trackId, x, y);
        } catch (Throwable t) {
            log("sendTouchEvent 异常: " + t);
            return false;
        }
    }

    public String getLastPinCode() { return lastPinCode; }

    /** Surface 就绪 / 变化（Activity 的 surfaceChanged 回调里调用）。 */
    public void setSurface(Surface s) {
        this.surface = s;
        castRetry = 0;          // 新 Surface 到位，重试预算重置
        tryStartCast();
    }

    // ==================================================================
    // 主入口
    // ==================================================================

    /** 启动（或重启）投屏链路。权限由调用方（Activity）负责。 */
    public void start() {
        log("=== 启动（ICCOA CarWith）===");
        if (sdkInitialized && adapter != null) {
            log("重启：SDK 已初始化，仅重新广播");
            reAdvertise();
            return;
        }
        UCarConfig config;
        try {
            config = buildConfig();
        } catch (Throwable t) {
            log("✗ 配置构造失败: " + t);
            return;
        }
        try {
            adapter = UCarAdapter.getInstance();
            adapter.registerCarConnectListener(connectListener);
            adapter.registerPhoneDataListener(phoneDataListener);
        } catch (Throwable t) {
            log("✗ getInstance/register 异常: " + t);
            return;
        }
        initSdk(config);
    }

    private void initSdk(UCarConfig config) {
        boolean posted = adapter.init(appCtx, config, new ICarInitCallback() {
            @Override
            public void onInitSuccess() {
                log("★ onInitSuccess()");
                sdkReady = true;
                sdkInitialized = true;   // ★ init-once 守门
                main.post(() -> safe("doAdvertise", IccoaManager.this::doAdvertise));
                main.post(IccoaManager.this::scheduleAutoRefresh);   // ★ 启动配对码看门狗
            }

            @Override
            public void onInitFailed(int errCode) {
                log("✗ onInitFailed(" + errCode + ")"
                        + (errCode == 10001 ? " ← HandlerThread 启动失败" : ""));
            }
        });
        if (!posted) log("✗ init() 返回 false —— 任务未提交");
    }

    /**
     * 重播：先停旧广播，再启新广播。
     *
     * <p><b>必须先 {@code stopAdvertise()}</b> —— 对一条已经在广播中的链路重复调用
     * {@code startAdvertise()} 会被 SDK 直接忽略，广播帧内容不变，手机端因此不会重新发现
     * 车机、也就不会重新弹窗。只有「停-启」才是一次真正的重播。
     */
    private void reAdvertise() {
        if (adapter == null) return;
        try {
            adapter.registerCarConnectListener(connectListener);
            adapter.registerPhoneDataListener(phoneDataListener);
        } catch (Throwable t) {
            log("reAdvertise 重新注册异常: " + t);
        }
        try { adapter.stopAdvertise(); } catch (Throwable t) { log("stopAdvertise(重播前) 异常: " + t); }
        sdkReady = true;
        castStarted = false;
        castRetry = 0;
        lastPinCode = null;
        // 重播即「重新开始一轮待连」：清掉协商计时，免得上一轮的残留把看门狗一直摁住。
        sessionActive = false;
        connectingSince = 0L;
        lastConnectProgressAt = 0L;
        doAdvertise();
    }

    /**
     * 用户主动「刷新配对码」：立刻重播一次，不必等看门狗的 30 秒周期。
     *
     * <p>与自动刷新走同一条路径，重播后看门狗重新计时。
     */
    public void refreshPairing() {
        log("=== 手动刷新配对码 ===");
        if (!sdkReady || adapter == null) {
            log("刷新失败：SDK 尚未就绪");
            return;
        }
        reAdvertise();
        scheduleAutoRefresh();
    }

    public void stop() {
        log("=== 停止 ===");
        if (adapter != null) {
            try { adapter.pauseCast(); } catch (Throwable t) { log("pauseCast 异常: " + t); }
            try { adapter.disconnect(); } catch (Throwable t) { log("disconnect 异常: " + t); }
            try { adapter.stopAdvertise(); } catch (Throwable t) { log("stopAdvertise 异常: " + t); }
            try {
                adapter.unregisterCarConnectListener(connectListener);
                adapter.unregisterPhoneDataListener(phoneDataListener);
            } catch (Throwable ignored) { }
        }
        main.removeCallbacks(reAdvertiseRunnable);
        cancelAutoRefresh();
        sessionActive = false;
        connectingSince = 0L;
        lastConnectProgressAt = 0L;
        castStarted = false;
        sdkReady = false;
        lastPinCode = null;
        // 保留 sdkInitialized —— init() 进程一次性，重启只走 reAdvertise()。
    }

    /**
     * 仅断开当前会话（用户主动点「断开」），保持广播 —— 手机可立即重新连接。
     *
     * <p>与 {@link #stop()} 的区别：{@code stop()} 会停掉广播（相当于彻底关机位），
     * 本方法只拆会话，车机仍可被发现。
     */
    public void disconnectSession() {
        log("=== 断开当前会话（保持广播）===");
        if (adapter != null) {
            try { adapter.pauseCast(); } catch (Throwable t) { log("pauseCast 异常: " + t); }
            try { adapter.disconnect(); } catch (Throwable t) { log("disconnect 异常: " + t); }
        }
        sessionActive = false;
        connectingSince = 0L;
        lastConnectProgressAt = 0L;
        castStarted = false;
        lastPinCode = null;
        scheduleReAdvertise();
        scheduleAutoRefresh();
    }

    // ==================================================================
    // 广播 / 投屏
    // ==================================================================

    private void doAdvertise() {
        if (adapter == null) return;
        log("广播参数：P2P=true SoftAP=" + SUPPORT_SOFT_AP + " 屏幕=" + screenW + "x" + screenH);
        boolean ok = adapter.startAdvertise();
        log((ok ? "✓ " : "✗ ") + "startAdvertise() = " + ok);
        tryStartCast();
    }

    /**
     * 尝试起投。
     *
     * <p>重试有上限：停车待机页没有 Surface，无上限重试会变成一个永不停止的 500ms 定时器
     * （待机页常驻，等于白耗电）。Surface 稍后就绪的常见情形由 {@link #setSurface} 兜底，
     * 这里的十秒预算只用于「Surface 已设但尚未 valid」的短暂窗口。
     */
    private void tryStartCast() {
        if (!sdkReady || castStarted || adapter == null) return;
        Surface s = surface;
        if (s == null || !s.isValid()) {
            if (castRetry < CAST_RETRY_MAX) {
                castRetry++;
                main.postDelayed(this::tryStartCast, 500);
            }
            return;
        }
        castRetry = 0;
        boolean ok = adapter.startCast(s, videoW, videoH);
        castStarted = ok;
        log((ok ? "✓ " : "✗ ") + "startCast(" + videoW + "x" + videoH + ") = " + ok);
        if (ok) {
            main.post(listener::onCastStarted);
        }
    }

    // ==================================================================
    // SDK 回调
    // ==================================================================

    private final ICarConnectListener connectListener = new ICarConnectListener() {
        @Override
        public void onPinCode(String pinCode, String carDeviceName) {
            lastPinCode = pinCode;
            log("★ onPinCode pinCode=" + pinCode + " carDeviceName=" + carDeviceName);
            // 新码下发 = 这一轮协商刚刚有新进展 → 看门狗重新计时，保证用户拿到完整的有效期去确认，
            // 也保证不会刚出码就被重播顶掉。
            connectingSince = System.currentTimeMillis();
            lastConnectProgressAt = connectingSince;
            scheduleAutoRefresh();
            main.post(() -> listener.onPinCode(pinCode, carDeviceName));
        }

        @Override
        public void onConnectStateChanged(String deviceID, int oldState, int newState) {
            log("onConnectStateChanged " + stateName(oldState) + " -> " + stateName(newState));
            if (newState == 0 && oldState != 0) {
                // 会话真正结束（不是启动时的初始 DISCONNECTED）。清掉已过期的配对码并延时重新
                // 广播：手机端 CarWith 会重新发现车机并触发一次全新配对（新的 onPinCode）。
                sessionActive = false;
                connectingSince = 0L;
                lastConnectProgressAt = 0L;
                lastPinCode = null;
                castStarted = false;
                scheduleReAdvertise();
                scheduleAutoRefresh();
            } else if (newState >= 2) {
                // 已连上：2 = CONNECTED、3 = ESTABLISHED，SDK 实测还会发 4。
                // 撤销任何待执行的重新广播，免得刚连上就被重启广播打断（那会把会话连同投屏一起拆掉）；
                // 看门狗也会因此空转。
                sessionActive = true;
                connectingSince = 0L;
                lastConnectProgressAt = 0L;
                main.removeCallbacks(reAdvertiseRunnable);
            } else if (newState == 1) {
                // 协商中：这一段**不算** sessionActive —— 看门狗必须保留「救回僵死协商」的能力。
                // 它改为按时间避让：只要距最后一次进展不超过 STUCK_CONNECTING_MS 就让位，
                // 超过就重播。若这里改成 sessionActive = true，SDK 一旦停在 CONNECTING 不回落到
                // DISCONNECTED，看门狗就永远不再重播，手机端连配对提示都不会再出现。
                connectingSince = System.currentTimeMillis();
                lastConnectProgressAt = connectingSince;
                sessionActive = false;
                main.removeCallbacks(reAdvertiseRunnable);
            }
            final int st = newState;
            main.post(() -> listener.onConnectionState(st));
        }

        @Override
        public void onConnectingProgress(String phoneId, int progress) {
            log("onConnectingProgress " + progress);
            // 有进度 = 这条链路还活着，把「僵死」计时往后推。
            lastConnectProgressAt = System.currentTimeMillis();
            final int p = progress;
            main.post(() -> listener.onConnectProgress(p));
        }

        @Override
        public void onReconfigureWifiInfo(WifiOwnerConfig config) {
            log("onReconfigureWifiInfo ssid=" + (config == null ? "?" : config.getSsid()));
        }

        @Override
        public void onRethinkBluetoothConnect(boolean rethink) {
            log("onRethinkBluetoothConnect=" + rethink);
        }

        @Override
        public UCarCommon.WorkMode onSelectWorkMode(String deviceID, UCarCommon.WorkMode supported) {
            UCarCommon.WorkMode chosen;
            if (supported == null) {
                chosen = UCarCommon.WorkMode.NONE;
            } else if (supported.isSupportNormalCast()) {
                // ★ 先选 NORMAL_CAST：本车机在 UCarConfig 里只声明了 {@code setSupportNormalCast(true)}，
                //   渲染也是往一个普通 Surface 上投。之前的顺序优先挑 AppWindowCast（融合桌面小窗），
                //   与声明的能力不一致，属于「手机按小窗模式协商、车机按整屏 Surface 收流」的错配。
                chosen = UCarCommon.WorkMode.NORMAL_CAST;
            } else if (supported.isSupportAppFullScreenCast()) {
                chosen = UCarCommon.WorkMode.APP_FULLSCREEN_CAST;
            } else if (supported.isSupportAppWindowCast()) {
                chosen = UCarCommon.WorkMode.APP_WINDOW_CAST;
            } else if (supported.isSupportDataTransfer()) {
                chosen = UCarCommon.WorkMode.DATA_TRANSFER;
            } else {
                chosen = UCarCommon.WorkMode.NONE;
            }
            log("onSelectWorkMode → " + chosen);
            return chosen;
        }
    };

    private final IPhoneDataListener phoneDataListener = new IPhoneDataListener() {
        @Override
        public void onNavigationInfoReceived(String phoneId, UCarCommon.NavigationInfo info) {
            listener.onNavigation(info);
        }

        @Override public void onMusicInfoReceived(String phoneId, UCarCommon.MusicInfo info) { }
        @Override public void onCallInfoReceived(String phoneId, UCarCommon.CallInfo info) { }
        @Override public void onPhoneStateInfoReceived(String phoneId, UCarCommon.PhoneStateInfo info) { }
        @Override public void onPOIReceived(String phoneId, UCarCommon.POIAddress poi) { }
        @Override public void onAudioFocusChanged(UCarCommon.AudioType type, int focus) { }

        @Override
        @SuppressWarnings("rawtypes")
        public void onAppListStateChanged(String phoneId, List list, UCarCommon.AppListState state) { }

        @Override
        public void onAppStateChanged(String phoneId, int state, boolean flag,
                                      int a, int b, UCarCommon.AppState appState) { }

        @Override
        public UCarCommon.AudioAttributes getAudioAttributesByType(UCarCommon.AudioType type) {
            return null;
        }
    };

    // ==================================================================
    // 配置构造（SDK 硬校验，越界即抛）
    // ==================================================================

    private UCarConfig buildConfig() {
        byte[] carId = generateCarId();
        if (carId == null || carId.length != 6) {
            carId = new byte[6];
            new Random().nextBytes(carId);
            log("! carId 长度异常，强制随机 6 字节");
        }
        android.util.DisplayMetrics dm = appCtx.getResources().getDisplayMetrics();
        int densityDpi = dm.densityDpi;
        int dpi = clamp(320, 120, 640);
        int realDpi = clamp(densityDpi, 120, 640);
        int fps = clamp(60, 15, 60);
        int sw = Math.max(1, screenW), sh = Math.max(1, screenH);
        int vw = Math.max(1, videoW), vh = Math.max(1, videoH);

        UCarCommon.WorkMode workMode = new UCarCommon.WorkMode.Builder()
                .setSupportNormalCast(true)
                .setSupportAppFullScreenCast(false)
                .setSupportAppWindowCast(false)
                .build();

        return new UCarConfig.Builder()
                .setCarBrMac(carId)                  // 必须恰好 6 字节
                .setDpi(dpi)                         // 120..640
                .setRealDpi(realDpi)                 // 120..640
                .setFps(fps)                         // 15..60
                .setScreenWidth(sw)
                .setScreenHeight(sh)
                .setVideoDisplayWidth(vw)
                .setVideoDisplayHeight(vh)
                .setSupportP2P(true)
                // SoftAP（车机做热点）：开关见 SUPPORT_SOFT_AP 的注释。侧载应用拿不到
                // TETHER_PRIVILEGED / OVERRIDE_WIFI_CONFIG / NETWORK_SETTINGS（signature|privileged），
                // 热点其实起不来；若无脑向手机宣称「支持 AP」，选 AP 的手机会把会话建在
                // 一个不存在的热点上（社区实测 vivo 最容易卡住），所以这个开关值得按需关掉。
                .setSupportSoftAP(SUPPORT_SOFT_AP)
                .setSupportCamera(false)
                .setSupportHevc(false)
                .setSupportMic(true)
                .setUseCustomAudioRecord(false)
                .setSupportStereoRecord(false)
                .setSupportLowLatencyDecodingMode(false)
                .setSupportPOINav(false)
                .setCcdFilePath("ccd.data")
                .setSupportedWorkModes(workMode)
                .build();
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private byte[] generateCarId() {
        SharedPreferences prefs = appCtx.getSharedPreferences("car_id_prefs", Context.MODE_PRIVATE);
        String saved = prefs.getString("car_id_key", "");
        if (saved != null && !saved.isEmpty()) {
            try {
                byte[] cached = Base64.decode(saved, Base64.NO_WRAP);
                // 旧版本缓存下来的 02:00:00:00:00:00（Android 的占位蓝牙地址）要一并作废，
                // 否则打过补丁也仍然在用那个假身份。
                if (cached != null && cached.length == 6 && cached[0] != 0
                        && !"02:00:00:00:00:00".equals(toMacString(cached))) {
                    return cached;
                }
                log("carId 缓存不可用（长度≠6 / 占位地址 02:00:00:00:00:00）→ 作废重生成");
            } catch (Throwable t) {
                log("carId 缓存解码失败: " + t);
            }
        }
        byte[] raw = getBluetoothMac();
        if (raw == null || raw.length != 6) {
            raw = new byte[6];
            new Random().nextBytes(raw);
        }
        prefs.edit().putString("car_id_key", Base64.encodeToString(raw, Base64.NO_WRAP)).apply();
        return raw;
    }

    /**
     * 车机蓝牙 MAC —— SDK 的 {@code UCarConfig.setCarBrMac()}，也就是车机对手机自称的蓝牙身份。
     *
     * <p>老实现直接读 {@link android.bluetooth.BluetoothAdapter#getAddress()}，但 Android 6 起这个
     * 调用对**非系统应用恒返回占位地址 {@code 02:00:00:00:00:00}**（还需要
     * {@code LOCAL_MAC_ADDRESS}，同样是 privileged 权限）。照单全收的结果是：所有 MiAuto 车机都
     * 自称同一个不存在的蓝牙设备，手机端按这个地址去认车/回连时必然对不上。真实车机（系统签名、
     * 或社区那些预装的车机端 App）拿到的是真地址，这也是 MiAuto 与它们的差异之一。
     *
     * <p>顺序：手动指定 → {@code Settings.Secure.bluetooth_address}（特权应用可读）→ 适配器地址
     * （只接受真实地址，占位地址视为读不到）→ 随机 6 字节。手动指定写进
     * {@code car_id_prefs/car_id_override_mac}，格式 {@code AA:BB:CC:DD:EE:FF}：把平板设置里
     * 看到的蓝牙地址填进去，即可让手机端按真实地址认车。
     */
    @android.annotation.SuppressLint("MissingPermission")
    private byte[] getBluetoothMac() {
        SharedPreferences prefs = appCtx.getSharedPreferences("car_id_prefs", Context.MODE_PRIVATE);

        byte[] override = parseRealMac(prefs.getString(CAR_ID_OVERRIDE_KEY, null));
        if (override != null) {
            log("carId 采用手动指定的蓝牙 MAC: " + toMacString(override));
            return override;
        }

        byte[] fromSettings = parseRealMac(readSecureString("bluetooth_address"));
        if (fromSettings != null) {
            log("carId 采用 Settings.Secure 里的蓝牙 MAC: " + toMacString(fromSettings));
            return fromSettings;
        }

        byte[] fromAdapter = parseRealMac(readAdapterAddress());
        if (fromAdapter != null) {
            log("carId 采用适配器蓝牙 MAC: " + toMacString(fromAdapter));
            return fromAdapter;
        }

        log("carId 读不到真实蓝牙 MAC（Android 6+ 非系统应用只会得到 02:00:00:00:00:00）→ 随机 6 字节；"
                + "若手机端需要按真实车机蓝牙地址认车，请写入 car_id_prefs/" + CAR_ID_OVERRIDE_KEY);
        return null;   // 交给调用方随机生成
    }

    /** 覆盖车机蓝牙 MAC 的偏好键（{@code AA:BB:CC:DD:EE:FF}）。 */
    private static final String CAR_ID_OVERRIDE_KEY = "car_id_override_mac";

    private String readSecureString(String key) {
        try {
            return android.provider.Settings.Secure.getString(appCtx.getContentResolver(), key);
        } catch (Throwable t) {
            return null;
        }
    }

    private String readAdapterAddress() {
        try {
            android.bluetooth.BluetoothManager bm =
                    (android.bluetooth.BluetoothManager) appCtx.getSystemService(Context.BLUETOOTH_SERVICE);
            android.bluetooth.BluetoothAdapter a = (bm == null) ? null : bm.getAdapter();
            return (a == null) ? null : a.getAddress();
        } catch (Throwable t) {
            log("读蓝牙 MAC 被拒（缺 BLUETOOTH_CONNECT / LOCAL_MAC_ADDRESS？）: " + t);
            return null;
        }
    }

    /**
     * 解析并校验一个「像真的」的蓝牙 MAC。
     *
     * <p>拒绝：空值、{@code 02:00:00:00:00:00}（Android 的占位地址）、段数不是 6、非十六进制、
     * 以及首字节为 0（全零/异常地址）。返回 null 表示「拿不到可用地址」。
     */
    private byte[] parseRealMac(String mac) {
        if (mac == null) return null;
        String s = mac.trim();
        if (s.isEmpty() || "02:00:00:00:00:00".equals(s)) return null;
        String[] seg = s.split(":");
        if (seg.length != 6) return null;
        byte[] out = new byte[6];
        for (int i = 0; i < 6; i++) {
            if (seg[i].length() != 2) return null;
            try {
                out[i] = (byte) Integer.parseInt(seg[i], 16);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (out[0] == 0) return null;
        return out;
    }

    private static String toMacString(byte[] mac) {
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < mac.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", mac[i] & 0xFF));
        }
        return sb.toString();
    }

    private void log(String msg) {
        Log.i(TAG, msg);
        try { listener.onLog(msg); } catch (Throwable ignored) { }
    }

    private void safe(String where, Runnable r) {
        try { r.run(); } catch (Throwable t) { log("!!! " + where + " 异常: " + t); }
    }

    private static String stateName(int s) {
        switch (s) {
            case 0: return "DISCONNECTED";
            case 1: return "CONNECTING";
            case 2: return "CONNECTED";
            case 3: return "ESTABLISHED";
            default: return "STATE_" + s;
        }
    }
}
