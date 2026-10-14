package com.andrerinas.openheadunit.icoca

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.connection.IcocaBridge
import com.andrerinas.openheadunit.utils.AppLog
import kotlinx.coroutines.launch

/**
 * Full-screen host for the ICCOA CarLink phone stream.
 *
 * The ICCOA SDK does not decode video itself — it hands the H.264 stream to a Surface you supply
 * via `UCarAdapter.startCast(surface, w, h)`. This activity exists purely to own that surface and
 * keep it alive while the phone is projecting, which is why it stays deliberately thin: one
 * [SurfaceView], a status hint, and a way out.
 *
 * It deliberately does *not* reuse `AapProjectionActivity`. That activity is wired end-to-end into
 * the Android Auto session (video-focus negotiation, `CommManager` transport state, AAP keyframes)
 * and MiAuto never opens an AA session, so every one of its branches would be dead code guarded by
 * a status that can never occur.
 */
class IcocaProjectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A projection session can run for a long time; never let the panel sleep mid-drive.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_icoca_projection)
        hideSystemUi()

        val bridge = App.provide(this).icocaBridge
        val surfaceView = findViewById<SurfaceView>(R.id.icoca_surface)
        surfaceView.holder.addCallback(
            object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    AppLog.i("ICCOA projection: surfaceCreated")
                    bridge.setSurface(holder.surface)
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    AppLog.i("ICCOA projection: surfaceChanged ${width}x$height")
                    // Re-bind on every change: a size change tears the surface down underneath the
                    // SDK, and the stale reference would otherwise keep receiving frames.
                    bridge.setSurface(holder.surface)
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    AppLog.i("ICCOA projection: surfaceDestroyed")
                    // Tell the SDK to stop before it writes another frame: its decoder is bound to
                    // this BufferQueue, and writing into a destroyed one kills the video pipeline
                    // for good (error -19, isRecoverable: false) while the audio path keeps playing.
                    bridge.onSurfaceDestroyed()
                }
            }
        )

        // Touch back-channel (UIBC). The SDK hands the phone's video to this Surface but never sees
        // the car's panel, so without this the picture is a one-way mirror — the user can watch but
        // not tap. Mirrors what the SDK's own `UCarSurfaceView.onTouchEvent` does: raw action,
        // per-pointer id, and coordinates scaled into the display area the SDK expects.
        surfaceView.isClickable = true
        surfaceView.setOnTouchListener { view, event ->
            forwardTouch(view as SurfaceView, event)
            true
        }

        findViewById<View>(R.id.icoca_disconnect).setOnClickListener {
            AppLog.i("ICCOA projection: disconnect tapped")
            // Drop the session but keep advertising: the standby screen goes straight back to
            // "waiting for phone" and a fresh pairing code, instead of a dead link that needs an
            // app restart to recover.
            bridge.disconnectSession()
            finish()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                bridge.state.collect { state ->
                    findViewById<View>(R.id.icoca_hint)?.visibility =
                        if (state.phase == IcocaBridge.Phase.Connected) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isForeground = true
    }

    /**
     * Hands one touch event to the phone.
     *
     * The SDK expects coordinates in the display area it was told about
     * (`UCarConfig.setVideoDisplayWidth/Height`) and converts them to the phone's own screen with
     * `x / displayWidth * sourceWidth`. The SurfaceView is full-screen here so the two usually
     * coincide, but the scale is applied explicitly so a smaller surface (or a future windowed
     * mode) still lines up with what the user touched.
     */
    private fun forwardTouch(view: SurfaceView, event: MotionEvent) {
        val bridge = App.provide(view.context).icocaBridge
        val count = event.pointerCount
        val trackIds = IntArray(count)
        val xs = IntArray(count)
        val ys = IntArray(count)
        val (displayW, displayH) = bridge.displaySize()
        val viewW = if (view.width > 0) view.width else displayW
        val viewH = if (view.height > 0) view.height else displayH
        for (i in 0 until count) {
            val pointerId = event.getPointerId(i)
            trackIds[i] = pointerId
            val index = event.findPointerIndex(pointerId)
            xs[i] = (event.getX(index) * displayW / viewW).toInt()
            ys[i] = (event.getY(index) * displayH / viewH).toInt()
        }
        // Raw action (NOT actionMasked) and raw coordinates: this is exactly what the SDK's own
        // UCarSurfaceView.onTouchEvent() passes, so it is what the phone-side UIBC decoder expects.
        bridge.sendTouch(event.action, count, trackIds, xs, ys)
        if (event.action == MotionEvent.ACTION_DOWN) view.performClick()
    }

    override fun onStop() {
        isForeground = false
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    companion object {
        /** Read by MainActivity to avoid stacking a second projection instance. */
        @Volatile
        var isForeground: Boolean = false
            private set

        fun intent(context: Context): Intent = Intent(context, IcocaProjectionActivity::class.java)
    }
}
