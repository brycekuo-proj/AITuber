package com.aituber.poc.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The touchable parts of the floating character. The character window itself lets every
 * touch through to the AI app underneath, so these small windows carry the controls:
 *  - a handle at the character's corner: tap for the menu, drag to move the character;
 *    while the character is being resized it turns into a "done" button;
 *  - the menu: move/resize, tuck away, back to AITuber, stop;
 *  - the bubble shown while the character is tucked away: tap to bring it back, drag to move.
 *
 * Main thread only.
 */
internal class FloatingControls(
    private val context: Context,
    private val windowManager: WindowManager,
    private val listener: Listener
) {
    interface Listener {
        fun onHandleDragStart()
        fun onHandleDrag(totalDx: Int, totalDy: Int)
        fun onHandleDragEnd()
        fun canAdjust(): Boolean
        fun onAdjustRequested()
        fun onAdjustDone()
        fun onCollapseRequested()
        fun onExpandRequested()
        fun onOpenAppRequested()
        fun onStopRequested()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val anchor = Rect()
    private var adjusting = false
    private var collapsed = false
    private var released = false

    private var handleView: TextView? = null
    private var handleParams: WindowManager.LayoutParams? = null
    private var menuView: View? = null
    private var menuDismissedByOutsideAtMs = 0L
    private var bubbleView: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private val dismissMenuRunnable = Runnable { dismissMenu() }

    /** True while the user is in the middle of something here: keep the character visible. */
    val busy: Boolean
        get() = menuView != null

    fun attach(characterBounds: Rect) {
        anchor.set(characterBounds)
        if (collapsed) showBubble() else showHandle()
    }

    fun updateAnchor(characterBounds: Rect) {
        anchor.set(characterBounds)
        positionHandle()
    }

    fun setAdjusting(value: Boolean) {
        if (adjusting == value) return
        adjusting = value
        dismissMenu()
        styleHandle()
        positionHandle()
    }

    fun setCollapsed(value: Boolean, characterBounds: Rect) {
        anchor.set(characterBounds)
        if (collapsed == value) return
        collapsed = value
        dismissMenu()
        if (value) {
            removeHandle()
            showBubble()
        } else {
            hideBubble()
            showHandle()
        }
    }

    fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        dismissMenu()
        removeHandle()
        hideBubble()
    }

    // ---- Handle -------------------------------------------------------------

    private fun showHandle() {
        if (released || handleView != null) return
        val handle = TextView(context).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            elevation = dp(6f)
        }
        val params = WindowManager.LayoutParams(
            dp(HANDLE_DP),
            dp(HANDLE_DP),
            OverlayWindowConfig.windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        handleView = handle
        handleParams = params
        styleHandle()
        computeHandlePosition(params)
        installHandleTouch(handle)
        runCatching { windowManager.addView(handle, params) }.onFailure {
            OverlayLifecycleTrace.record("handle attach failed: ${it::class.java.simpleName}")
            handleView = null
            handleParams = null
        }
    }

    private fun removeHandle() {
        handleView?.let { view -> runCatching { windowManager.removeView(view) } }
        handleView = null
        handleParams = null
    }

    private fun styleHandle() {
        val handle = handleView ?: return
        val params = handleParams ?: return
        if (adjusting) {
            handle.text = "✓ 完成"
            handle.textSize = 15f
            handle.typeface = Typeface.DEFAULT_BOLD
            handle.setTextColor(WINE)
            handle.background = GradientDrawable().apply {
                cornerRadius = dp(20f)
                setColor(GOLD)
            }
            handle.contentDescription = "完成調整"
            params.width = dp(DONE_WIDTH_DP)
        } else {
            handle.text = "⋯"
            handle.textSize = 22f
            handle.typeface = Typeface.DEFAULT_BOLD
            handle.setTextColor(GOLD)
            handle.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(190, 43, 8, 18))
                setStroke(dp(1), GOLD)
            }
            handle.contentDescription = "AITuber 角色選單"
            params.width = dp(HANDLE_DP)
        }
        params.height = dp(HANDLE_DP)
    }

    private fun positionHandle() {
        val handle = handleView ?: return
        val params = handleParams ?: return
        computeHandlePosition(params)
        // addView sets the parent at once; isAttachedToWindow turns true only after the next frame.
        if (handle.parent != null) {
            runCatching { windowManager.updateViewLayout(handle, params) }
        }
    }

    /** Top-right corner of the character, kept on screen. */
    private fun computeHandlePosition(params: WindowManager.LayoutParams) {
        val metrics = context.resources.displayMetrics
        val margin = dp(6)
        val top = statusBarHeight()
        params.x = (anchor.right - params.width - margin)
            .coerceIn(0, max(0, metrics.widthPixels - params.width))
        params.y = (anchor.top + margin)
            .coerceIn(top, max(top, metrics.heightPixels - params.height))
    }

    private fun installHandleTouch(view: View) {
        var downX = 0f
        var downY = 0f
        var dragging = false
        view.setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) >= touchSlop || abs(dy) >= touchSlop)) {
                        dragging = true
                        dismissMenu()
                        listener.onHandleDragStart()
                    }
                    if (dragging) listener.onHandleDrag(dx.roundToInt(), dy.roundToInt())
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) listener.onHandleDragEnd() else touched.performClick()
                    dragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) listener.onHandleDragEnd()
                    dragging = false
                    true
                }
                else -> true
            }
        }
        view.setOnClickListener {
            if (adjusting) listener.onAdjustDone() else toggleMenu()
        }
    }

    // ---- Menu ---------------------------------------------------------------

    private fun toggleMenu() {
        if (menuView != null) {
            dismissMenu()
            return
        }
        // The tap on the handle already closed the menu as an outside touch: do not reopen it.
        if (SystemClock.uptimeMillis() - menuDismissedByOutsideAtMs < REOPEN_GUARD_MS) return
        showMenu()
    }

    private fun showMenu() {
        val handleP = handleParams ?: return
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14f)
                setColor(Color.argb(240, 33, 8, 14))
                setStroke(dp(1), Color.argb(160, 236, 207, 177))
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            elevation = dp(8f)
        }
        if (listener.canAdjust()) panel.addView(menuItem("↔  移動／縮放") { listener.onAdjustRequested() })
        panel.addView(menuItem("▾  收起角色") { listener.onCollapseRequested() })
        panel.addView(menuItem("⌂  回到 AITuber") { listener.onOpenAppRequested() })
        panel.addView(menuItem("■  停止 AITuber") { listener.onStopRequested() })
        panel.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                menuDismissedByOutsideAtMs = SystemClock.uptimeMillis()
                dismissMenu()
                true
            } else {
                false
            }
        }

        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        panel.measure(unspecified, unspecified)
        val width = panel.measuredWidth
        val height = panel.measuredHeight
        val metrics = context.resources.displayMetrics
        val gap = dp(6)
        var x = handleP.x + handleP.width - width
        var y = handleP.y + handleP.height + gap
        if (y + height > metrics.heightPixels) y = handleP.y - gap - height
        x = x.coerceIn(0, max(0, metrics.widthPixels - width))
        y = y.coerceIn(0, max(0, metrics.heightPixels - height))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowConfig.windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        runCatching { windowManager.addView(panel, params) }.onSuccess {
            menuView = panel
            handler.removeCallbacks(dismissMenuRunnable)
            handler.postDelayed(dismissMenuRunnable, MENU_TIMEOUT_MS)
        }
    }

    private fun menuItem(label: String, action: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 16f
        setTextColor(GOLD)
        gravity = Gravity.CENTER_VERTICAL
        minWidth = dp(176)
        setPadding(dp(14), dp(11), dp(18), dp(11))
        isClickable = true
        isFocusable = true
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(Color.argb(80, 236, 207, 177)))
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
        setOnClickListener {
            dismissMenu()
            action()
        }
    }

    private fun dismissMenu() {
        handler.removeCallbacks(dismissMenuRunnable)
        menuView?.let { view -> runCatching { windowManager.removeView(view) } }
        menuView = null
    }

    // ---- Bubble -------------------------------------------------------------

    private fun showBubble() {
        if (released || bubbleView != null) return
        val size = dp(BUBBLE_DP)
        val bubble = TextView(context).apply {
            text = "AI"
            gravity = Gravity.CENTER
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(GOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(230, 43, 8, 18))
                setStroke(dp(2), GOLD)
            }
            contentDescription = "顯示 AITuber 角色"
            isClickable = true
            isFocusable = true
            elevation = dp(6f)
        }
        val metrics = context.resources.displayMetrics
        val params = WindowManager.LayoutParams(
            size,
            size,
            OverlayWindowConfig.windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (prefs.getBoolean(KEY_BUBBLE_SAVED, false)) {
                x = prefs.getInt(KEY_BUBBLE_X, 0)
                y = prefs.getInt(KEY_BUBBLE_Y, 0)
            } else {
                x = metrics.widthPixels - size - dp(EDGE_MARGIN_DP)
                y = max(anchor.top + dp(24), (metrics.heightPixels * 0.3f).toInt())
            }
        }
        clampBubble(params)
        installBubbleTouch(bubble, params)
        runCatching { windowManager.addView(bubble, params) }
            .onSuccess {
                bubbleView = bubble
                bubbleParams = params
            }
            .onFailure { OverlayLifecycleTrace.record("bubble attach failed: ${it::class.java.simpleName}") }
    }

    private fun hideBubble() {
        bubbleView?.let { view -> runCatching { windowManager.removeView(view) } }
        bubbleView = null
        bubbleParams = null
    }

    private fun installBubbleTouch(view: View, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        view.setOnTouchListener { touched, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) >= touchSlop || abs(dy) >= touchSlop)) dragging = true
                    if (dragging) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        clampBubble(params)
                        runCatching { windowManager.updateViewLayout(touched, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        snapBubbleToEdge(params)
                        runCatching { windowManager.updateViewLayout(touched, params) }
                        prefs.edit()
                            .putBoolean(KEY_BUBBLE_SAVED, true)
                            .putInt(KEY_BUBBLE_X, params.x)
                            .putInt(KEY_BUBBLE_Y, params.y)
                            .apply()
                    } else {
                        touched.performClick()
                    }
                    dragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    true
                }
                else -> true
            }
        }
        view.setOnClickListener { listener.onExpandRequested() }
    }

    private fun clampBubble(params: WindowManager.LayoutParams) {
        val metrics = context.resources.displayMetrics
        val top = statusBarHeight()
        params.x = params.x.coerceIn(0, max(0, metrics.widthPixels - params.width))
        params.y = params.y.coerceIn(top, max(top, metrics.heightPixels - params.height))
    }

    private fun snapBubbleToEdge(params: WindowManager.LayoutParams) {
        val metrics = context.resources.displayMetrics
        val margin = dp(EDGE_MARGIN_DP)
        params.x = if (params.x + params.width / 2 < metrics.widthPixels / 2) {
            margin
        } else {
            metrics.widthPixels - params.width - margin
        }
        clampBubble(params)
    }

    // ---- Helpers ------------------------------------------------------------

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun dp(value: Int): Int = (value * density).roundToInt().coerceAtLeast(1)

    private fun dp(value: Float): Float = value * density

    private companion object {
        const val PREFS = "aituber_floating_controls"
        const val KEY_BUBBLE_SAVED = "bubble_saved"
        const val KEY_BUBBLE_X = "bubble_x"
        const val KEY_BUBBLE_Y = "bubble_y"

        const val HANDLE_DP = 40
        const val DONE_WIDTH_DP = 92
        const val BUBBLE_DP = 52
        const val EDGE_MARGIN_DP = 6
        const val MENU_TIMEOUT_MS = 8_000L
        const val REOPEN_GUARD_MS = 600L

        val GOLD = Color.rgb(236, 207, 177)
        val WINE = Color.rgb(43, 8, 18)
    }
}
