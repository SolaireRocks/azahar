// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv3 or any later version
// Refer to the LICENSE.txt file included.

package org.citra.citra_emu.overlay

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.VectorDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceView
import android.view.View
import android.view.View.OnTouchListener
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.preference.PreferenceManager
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.R
import org.citra.citra_emu.features.input.Hotkey
import org.citra.citra_emu.features.settings.model.BooleanSetting
import org.citra.citra_emu.features.settings.model.CitraSettings
import org.citra.citra_emu.utils.ComboHelper
import org.citra.citra_emu.utils.EmulationMenuSettings
import org.citra.citra_emu.utils.TurboHelper

/**
 * Draws the interactive input overlay on top of the
 * [SurfaceView] that is rendering emulation.
 */
class InputOverlay(context: Context?, attrs: AttributeSet?) :
    SurfaceView(context, attrs),
    OnTouchListener {

    interface OverlayActionListener {
        fun onQuicksave()
        fun onQuickload()
        fun onOpenSavestatePicker(isSaving: Boolean)
        fun onTogglePause()
        fun onOpenDrawerMenu()
        fun onTakeScreenshot()
        fun isEmulationPaused(): Boolean
        fun onSelectedControlChanged(control: OverlayControl?)
    }

    private val overlayButtons: MutableSet<InputOverlayDrawableButton> = LinkedHashSet()
    private val overlayDpads: MutableSet<InputOverlayDrawableDpad> = LinkedHashSet()
    private val overlayJoysticks: MutableSet<InputOverlayDrawableJoystick> = LinkedHashSet()

    private var isInEditMode = false
    private var buttonBeingConfigured: InputOverlayDrawableButton? = null
    private var dpadBeingConfigured: InputOverlayDrawableDpad? = null
    private var joystickBeingConfigured: InputOverlayDrawableJoystick? = null

    var selectedControl: OverlayControl? = null
        private set

    var actionListener: OverlayActionListener? = null

    private var editDownX = 0f
    private var editDownY = 0f
    private var editMoved = false
    private var editTouchedEmptySpace = false
    private var isPinchScaling = false
    private var pinchBaseScale = 50f

    private val editBackdropPaint = Paint().apply {
        color = Color.argb(90, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(38, 255, 255, 255)
        strokeWidth = 1.5f
    }
    private val adornmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val disabledCrossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(215, 255, 85, 85)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val adornmentRect = RectF()

    private val scaleGestureDetector = ScaleGestureDetector(
        getContext(),
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                if (!isInEditMode) return false
                if (selectedControl == null) {
                    val hit = findControlAt(detector.focusX.toInt(), detector.focusY.toInt())
                    if (hit != null) {
                        selectControl(hit)
                    }
                }
                val ctrl = selectedControl ?: return false
                isPinchScaling = true
                buttonBeingConfigured = null
                dpadBeingConfigured = null
                joystickBeingConfigured = null
                pinchBaseScale = preferences.getInt(ctrl.scalePrefKey, 50).toFloat()
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val ctrl = selectedControl ?: return false
                val currentDisplayPct = pinchBaseScale + 50f
                val newDisplayPct = (currentDisplayPct * detector.scaleFactor).coerceIn(50f, 200f)
                pinchBaseScale = newDisplayPct - 50f
                val newScaleInt = pinchBaseScale.roundToInt().coerceIn(0, 150)
                if (preferences.getInt(ctrl.scalePrefKey, 50) != newScaleInt) {
                    preferences.edit().putInt(ctrl.scalePrefKey, newScaleInt).apply()
                    refreshControls()
                    actionListener?.onSelectedControlChanged(ctrl)
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isPinchScaling = false
            }
        }
    )

    // Stores the ID of the pointer that interacted with the 3DS touchscreen.
    private var touchscreenPointerId = -1

    init {
        if (!preferences.getBoolean("OverlayInit", false)) {
            defaultOverlay()
        }

        OverlayProfileManager.ensureLoaded()

        // Reset 3ds touchscreen pointer ID
        touchscreenPointerId = -1

        // Load the controls.
        refreshControls()

        // Set the on touch listener.
        setOnTouchListener(this)

        // Force draw
        setWillNotDraw(false)

        // Request focus for the overlay so it has priority on presses.
        requestFocus()

        // Deliver controller axis events as they arrive instead of batching them to the next
        // frame.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_JOYSTICK)
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        if (isInEditMode) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), editBackdropPaint)
            if (OverlayProfileManager.gridSnapEnabled && width > 0 && height > 0) {
                val gridStep = width.toFloat() / GRID_COLS
                var gx = 0f
                while (gx <= width) {
                    canvas.drawLine(gx, 0f, gx, height.toFloat(), gridPaint)
                    gx += gridStep
                }
                var gy = 0f
                while (gy <= height) {
                    canvas.drawLine(0f, gy, width.toFloat(), gy, gridPaint)
                    gy += gridStep
                }
            }
        }

        val isPaused = actionListener?.isEmulationPaused() == true

        overlayButtons.forEach { button ->
            if (button.enabled || isInEditMode) {
                button.draw(canvas, isPaused, isInEditMode)
                if (isInEditMode) {
                    drawEditAdornment(canvas, button.bounds, button.id, button.enabled)
                }
            }
        }
        overlayDpads.forEach { dpad ->
            if (dpad.enabled || isInEditMode) {
                dpad.draw(canvas, isInEditMode)
                if (isInEditMode) {
                    drawEditAdornment(canvas, dpad.bounds, dpad.upId, dpad.enabled)
                }
            }
        }
        overlayJoysticks.forEach { joystick ->
            if (joystick.enabled || isInEditMode) {
                joystick.draw(canvas, isInEditMode)
                if (isInEditMode) {
                    drawEditAdornment(
                        canvas,
                        joystick.bounds,
                        joystick.joystickId,
                        joystick.enabled
                    )
                }
            }
        }
    }

    private fun drawEditAdornment(canvas: Canvas, bounds: Rect, controlId: Int, enabled: Boolean) {
        val density = resources.displayMetrics.density
        if (!enabled) {
            val minDim = min(bounds.width(), bounds.height()).toFloat()
            disabledCrossPaint.strokeWidth = (minDim * 0.07f).coerceAtLeast(2f * density)
            val padX = bounds.width() * 0.22f
            val padY = bounds.height() * 0.22f
            canvas.drawLine(
                bounds.left + padX,
                bounds.top + padY,
                bounds.right - padX,
                bounds.bottom - padY,
                disabledCrossPaint
            )
            canvas.drawLine(
                bounds.right - padX,
                bounds.top + padY,
                bounds.left + padX,
                bounds.bottom - padY,
                disabledCrossPaint
            )
        }

        val isSelected = selectedControl?.id == controlId
        adornmentPaint.strokeWidth = if (isSelected) 3f * density else 2f * density
        adornmentPaint.color = when {
            isSelected -> Color.rgb(0xFF, 0xD3, 0x3A)
            enabled -> Color.rgb(0x3A, 0x6E, 0xA5)
            else -> Color.argb(150, 0xFF, 0x6B, 0x6B)
        }
        adornmentRect.set(bounds)
        val corner = 8f * density
        canvas.drawRoundRect(adornmentRect, corner, corner, adornmentPaint)
    }

    fun selectControl(control: OverlayControl?) {
        selectedControl = control
        actionListener?.onSelectedControlChanged(control)
        invalidate()
    }

    fun onActionShortTap(buttonId: Int) {
        when (buttonId) {
            NativeLibrary.ButtonType.BUTTON_QUICKSAVE -> actionListener?.onQuicksave()
            NativeLibrary.ButtonType.BUTTON_QUICKLOAD -> actionListener?.onQuickload()
            NativeLibrary.ButtonType.BUTTON_PAUSE -> {
                actionListener?.onTogglePause()
                invalidate()
            }
        }
    }

    fun onActionLongPress(buttonId: Int) {
        when (buttonId) {
            NativeLibrary.ButtonType.BUTTON_QUICKSAVE ->
                actionListener?.onOpenSavestatePicker(true)

            NativeLibrary.ButtonType.BUTTON_QUICKLOAD ->
                actionListener?.onOpenSavestatePicker(false)

            NativeLibrary.ButtonType.BUTTON_PAUSE ->
                actionListener?.onOpenDrawerMenu()
        }
    }

    private fun isFrontendActionButton(buttonId: Int): Boolean = when (buttonId) {
        NativeLibrary.ButtonType.BUTTON_SWAP,
        NativeLibrary.ButtonType.BUTTON_TURBO,
        NativeLibrary.ButtonType.BUTTON_TURBO_2,
        NativeLibrary.ButtonType.BUTTON_TURBO_3,
        NativeLibrary.ButtonType.BUTTON_QUICKSAVE,
        NativeLibrary.ButtonType.BUTTON_QUICKLOAD,
        NativeLibrary.ButtonType.BUTTON_PAUSE,
        NativeLibrary.ButtonType.BUTTON_SCREENSHOT,
        Hotkey.COMBO_BUTTON.button -> true

        else -> false
    }

    private fun swapScreen() {
        val isEnabled = !EmulationMenuSettings.swapScreens
        EmulationMenuSettings.swapScreens = isEnabled
        val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display.rotation
        } else {
            @Suppress("DEPRECATION")
            (context as Activity).windowManager.defaultDisplay.rotation
        }
        NativeLibrary.swapScreens(
            isEnabled,
            displayRotation
        )
    }

    fun hapticFeedback(type: Int) {
        if (EmulationMenuSettings.hapticFeedback) {
            performHapticFeedback(type)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (isInEditMode) {
            return onTouchWhileEditing(event)
        }

        // Deliver the touch events as they arrive instead of batching them to the next
        // frame.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            requestUnbufferedDispatch(event)
        }

        val motionEvent = event.action and MotionEvent.ACTION_MASK
        val isActionDown =
            motionEvent == MotionEvent.ACTION_DOWN || motionEvent == MotionEvent.ACTION_POINTER_DOWN
        val isActionMove = motionEvent == MotionEvent.ACTION_MOVE
        val isActionUp =
            motionEvent == MotionEvent.ACTION_UP || motionEvent == MotionEvent.ACTION_POINTER_UP

        val pointerList = (0 until event.pointerCount).toMutableList()
        // Move the pointer that triggered the most recent event to the front
        // of the list so that it is processed first
        val currentActionPointer = event.actionIndex
        pointerList.remove(pointerList.indexOf(currentActionPointer))
        pointerList.add(0, currentActionPointer)

        // Set up a loop for if we need to check touches other than the most recent one
        // (Only happens if we're dragging the touch)
        for (pointerIndex in pointerList) {
            val pointerId = event.getPointerId(pointerIndex)

            var xPosition = event.getX(pointerIndex).toInt()
            var yPosition = event.getY(pointerIndex).toInt()

            if (CitraSettings.activeSettings.get(BooleanSetting.EXPAND_TO_CUTOUT_AREA)) {
                val cutout = ViewCompat.getRootWindowInsets(this)?.displayCutout
                val marginsX = (cutout?.safeInsetLeft?.plus(cutout.safeInsetRight)) ?: 0
                val marginsY = (cutout?.safeInsetTop?.plus(cutout.safeInsetBottom)) ?: 0
                xPosition += marginsX
                yPosition += marginsY
            }

            var hasActiveButtons = false
            for (button in overlayButtons) {
                if (button.trackId == pointerId) {
                    hasActiveButtons = true
                    break
                }
            }

            var hasActiveDpad = false
            if (!hasActiveButtons) {
                for (dpad in overlayDpads) {
                    if (dpad.trackId == pointerId) {
                        hasActiveDpad = true
                        break
                    }
                }
            }

            var hasActiveJoystick = false
            if (!hasActiveButtons && !hasActiveDpad) {
                for (joystick in overlayJoysticks) {
                    if (joystick.trackId == pointerId) {
                        hasActiveJoystick = true
                        break
                    }
                }
            }

            val hasActiveOverlay = hasActiveButtons || hasActiveDpad || hasActiveJoystick

            if (preferences.getBoolean("isTouchEnabled", true) && !hasActiveOverlay) {
                if (isActionMove) {
                    NativeLibrary.onTouchMoved(xPosition.toFloat(), yPosition.toFloat())
                    continue
                } else if (isActionUp) {
                    NativeLibrary.onTouchEvent(0f, 0f, false)
                    break // Up and down actions shouldn't loop
                }
            }

            var anyOverlayStateChanged = false
            var shouldUpdateView = false
            if (!hasActiveDpad && !hasActiveJoystick) {
                for (button in overlayButtons) {
                    val stateChanged = button.updateStatus(
                        event,
                        pointerIndex,
                        hasActiveButtons,
                        this
                    )
                    if (!stateChanged) {
                        continue
                    }
                    anyOverlayStateChanged = true
                    when (button.id) {
                        NativeLibrary.ButtonType.BUTTON_SWAP -> {
                            if (button.status == NativeLibrary.ButtonState.PRESSED) {
                                swapScreen()
                            }
                        }

                        NativeLibrary.ButtonType.BUTTON_TURBO -> {
                            if (button.status == NativeLibrary.ButtonState.PRESSED) {
                                TurboHelper.toggleTurboSlot(1, true)
                            }
                        }

                        NativeLibrary.ButtonType.BUTTON_TURBO_2 -> {
                            if (button.status == NativeLibrary.ButtonState.PRESSED) {
                                TurboHelper.toggleTurboSlot(2, true)
                            }
                        }

                        NativeLibrary.ButtonType.BUTTON_TURBO_3 -> {
                            if (button.status == NativeLibrary.ButtonState.PRESSED) {
                                TurboHelper.toggleTurboSlot(3, true)
                            }
                        }

                        NativeLibrary.ButtonType.BUTTON_SCREENSHOT -> {
                            if (button.status == NativeLibrary.ButtonState.PRESSED) {
                                actionListener?.onTakeScreenshot()
                            }
                        }

                        Hotkey.COMBO_BUTTON.button -> {
                            ComboHelper.comboActivate(button.status)
                        }
                    }

                    if (!isFrontendActionButton(button.id)) {
                        NativeLibrary.onGamePadEvent(
                            NativeLibrary.TOUCHSCREEN_DEVICE,
                            button.id,
                            button.status
                        )
                    }

                    shouldUpdateView = true
                }
            }

            if (!hasActiveButtons && !hasActiveJoystick) {
                for (dpad in overlayDpads) {
                    val stateChanged = dpad.updateStatus(
                        event,
                        pointerIndex,
                        hasActiveDpad,
                        EmulationMenuSettings.dpadSlide,
                        this
                    )
                    if (!stateChanged) {
                        continue
                    }
                    anyOverlayStateChanged = true

                    NativeLibrary.onGamePadEvent(
                        NativeLibrary.TOUCHSCREEN_DEVICE,
                        dpad.upId,
                        dpad.upStatus
                    )
                    NativeLibrary.onGamePadEvent(
                        NativeLibrary.TOUCHSCREEN_DEVICE,
                        dpad.downId,
                        dpad.downStatus
                    )
                    NativeLibrary.onGamePadEvent(
                        NativeLibrary.TOUCHSCREEN_DEVICE,
                        dpad.leftId,
                        dpad.leftStatus
                    )
                    NativeLibrary.onGamePadEvent(
                        NativeLibrary.TOUCHSCREEN_DEVICE,
                        dpad.rightId,
                        dpad.rightStatus
                    )

                    shouldUpdateView = true
                }
            }

            if (!hasActiveDpad && !hasActiveButtons) {
                for (joystick in overlayJoysticks) {
                    val stateChanged = joystick.updateStatus(
                        event,
                        pointerIndex,
                        hasActiveJoystick,
                        this
                    )
                    if (!stateChanged) {
                        continue
                    }
                    anyOverlayStateChanged = true

                    val axisID = joystick.joystickId
                    NativeLibrary.onGamePadMoveEvent(
                        NativeLibrary.TOUCHSCREEN_DEVICE,
                        axisID,
                        joystick.xAxis,
                        joystick.yAxis
                    )

                    shouldUpdateView = true
                }
            }

            if (shouldUpdateView) {
                invalidate()
            }

            if (preferences.getBoolean("isTouchEnabled", true) &&
                isActionDown &&
                !anyOverlayStateChanged
            ) {
                var isDpadPressed = false
                for (dpad in overlayDpads) {
                    if (dpad.trackId == pointerId) {
                        isDpadPressed = true
                        break
                    }
                }
                var isJoystickPressed = false
                for (joystick in overlayJoysticks) {
                    if (joystick.trackId == pointerId) {
                        isJoystickPressed = true
                        break
                    }
                }

                if (!isDpadPressed && !isJoystickPressed) {
                    NativeLibrary.onTouchEvent(xPosition.toFloat(), yPosition.toFloat(), true)
                }
            }

            // We should only loop here if touch is being dragged
            if (!isActionMove) {
                break
            }
        }
        return true
    }

    private fun findControlAt(x: Int, y: Int): OverlayControl? {
        // First pass: check enabled controls so visible controls take priority over hidden ones
        for (button in overlayButtons) {
            if (button.enabled && button.bounds.contains(x, y)) {
                return OverlayControl.fromId(button.id)
            }
        }
        for (dpad in overlayDpads) {
            if (dpad.enabled && dpad.bounds.contains(x, y)) {
                return OverlayControl.fromId(dpad.upId)
            }
        }
        for (joystick in overlayJoysticks) {
            if (joystick.enabled && joystick.bounds.contains(x, y)) {
                return OverlayControl.fromId(joystick.joystickId)
            }
        }
        // Second pass: disabled controls (visible in edit mode)
        for (button in overlayButtons) {
            if (!button.enabled && button.bounds.contains(x, y)) {
                return OverlayControl.fromId(button.id)
            }
        }
        for (dpad in overlayDpads) {
            if (!dpad.enabled && dpad.bounds.contains(x, y)) {
                return OverlayControl.fromId(dpad.upId)
            }
        }
        for (joystick in overlayJoysticks) {
            if (!joystick.enabled && joystick.bounds.contains(x, y)) {
                return OverlayControl.fromId(joystick.joystickId)
            }
        }
        return null
    }

    private fun snapTopLeftToGrid(left: Int, top: Int, w: Int, h: Int): Pair<Int, Int> {
        if (!OverlayProfileManager.gridSnapEnabled || width <= 0 || height <= 0) {
            return left to top
        }
        val gridPx = width.toFloat() / GRID_COLS
        if (gridPx <= 1f) return left to top
        val cx = left + w / 2f
        val cy = top + h / 2f
        val snappedCx = (cx / gridPx).roundToInt() * gridPx
        val snappedCy = (cy / gridPx).roundToInt() * gridPx
        val newLeft = (snappedCx - w / 2f).roundToInt().coerceIn(0, (width - w).coerceAtLeast(0))
        val newTop = (snappedCy - h / 2f).roundToInt().coerceIn(0, (height - h).coerceAtLeast(0))
        return newLeft to newTop
    }

    fun onTouchWhileEditing(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        if (isPinchScaling || event.pointerCount > 1) {
            return true
        }

        val pointerIndex = event.actionIndex
        val fingerPositionX = event.getX(pointerIndex).toInt()
        val fingerPositionY = event.getY(pointerIndex).toInt()
        val orientation =
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                "-Portrait"
            } else {
                ""
            }

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                editDownX = event.x
                editDownY = event.y
                editMoved = false
                buttonBeingConfigured = null
                dpadBeingConfigured = null
                joystickBeingConfigured = null

                val hitControl = findControlAt(fingerPositionX, fingerPositionY)
                if (hitControl != null) {
                    editTouchedEmptySpace = false
                    selectControl(hitControl)
                    when (hitControl) {
                        OverlayControl.DPAD -> {
                            dpadBeingConfigured = overlayDpads.firstOrNull()
                            dpadBeingConfigured?.onConfigureTouch(event)
                        }

                        OverlayControl.STICK_MAIN,
                        OverlayControl.STICK_C -> {
                            joystickBeingConfigured =
                                overlayJoysticks.firstOrNull { it.joystickId == hitControl.id }
                            joystickBeingConfigured?.onConfigureTouch(event)
                        }

                        else -> {
                            buttonBeingConfigured =
                                overlayButtons.firstOrNull { it.id == hitControl.id }
                            buttonBeingConfigured?.onConfigureTouch(event)
                        }
                    }
                } else {
                    editTouchedEmptySpace = true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (hypot(event.x - editDownX, event.y - editDownY) > 10f) {
                    editMoved = true
                }
                buttonBeingConfigured?.let { btn ->
                    btn.onConfigureTouch(event)
                    invalidate()
                    return true
                }
                dpadBeingConfigured?.let { dpad ->
                    dpad.onConfigureTouch(event)
                    invalidate()
                    return true
                }
                joystickBeingConfigured?.let { stick ->
                    stick.onConfigureTouch(event)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                buttonBeingConfigured?.let { btn ->
                    val (snappedX, snappedY) = snapTopLeftToGrid(
                        btn.bounds.left,
                        btn.bounds.top,
                        btn.width,
                        btn.height
                    )
                    btn.setPosition(snappedX, snappedY)
                    btn.setBounds(snappedX, snappedY, snappedX + btn.width, snappedY + btn.height)
                    saveControlPosition(btn.id, snappedX, snappedY, orientation)
                    buttonBeingConfigured = null
                    invalidate()
                }
                dpadBeingConfigured?.let { dpad ->
                    val (snappedX, snappedY) = snapTopLeftToGrid(
                        dpad.bounds.left,
                        dpad.bounds.top,
                        dpad.width,
                        dpad.height
                    )
                    dpad.setPosition(snappedX, snappedY)
                    dpad.setBounds(
                        snappedX,
                        snappedY,
                        snappedX + dpad.width,
                        snappedY + dpad.height
                    )
                    saveControlPosition(dpad.upId, snappedX, snappedY, orientation)
                    dpadBeingConfigured = null
                    invalidate()
                }
                joystickBeingConfigured?.let { stick ->
                    val (snappedX, snappedY) = snapTopLeftToGrid(
                        stick.bounds.left,
                        stick.bounds.top,
                        stick.bounds.width(),
                        stick.bounds.height()
                    )
                    saveControlPosition(stick.joystickId, snappedX, snappedY, orientation)
                    joystickBeingConfigured = null
                    refreshControls()
                }
                if (editTouchedEmptySpace && !editMoved) {
                    selectControl(null)
                }
                editTouchedEmptySpace = false
            }
        }
        return true
    }

    private fun addOverlayControls(orientation: String) {
        for (control in OverlayControl.entries) {
            val isEnabled = preferences.getBoolean(control.togglePrefKey, control.defaultEnabled)
            if (!isEnabled && !isInEditMode) {
                continue
            }
            when (control) {
                OverlayControl.BUTTON_A -> overlayButtons.add(
                    initializeOverlayButton(
                        context,
                        R.drawable.button_a,
                        R.drawable.button_a_pressed,
                        control,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.BUTTON_B -> overlayButtons.add(
                    initializeOverlayButton(
                        context,
                        R.drawable.button_b,
                        R.drawable.button_b_pressed,
                        control,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.BUTTON_X -> overlayButtons.add(
                    initializeOverlayButton(
                        context,
                        R.drawable.button_x,
                        R.drawable.button_x_pressed,
                        control,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.BUTTON_Y -> overlayButtons.add(
                    initializeOverlayButton(
                        context,
                        R.drawable.button_y,
                        R.drawable.button_y_pressed,
                        control,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.TRIGGER_L -> overlayButtons.add(
                    initializeOverlayButton(
                        context,
                        R.drawable.button_l,
                        R.drawable.button_l_pressed,
                        control,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.TRIGGER_R -> overlayButtons.add(
                    initializeOverlayButton(
                        context,
                        R.drawable.button_r,
                        R.drawable.button_r_pressed,
                        control,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.BUTTON_ZL -> overlayButtons.add(
                    initializeOverlayButton(
                        context,
                        R.drawable.button_zl,
                        R.drawable.button_zl_pressed,
                        control,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.BUTTON_ZR -> overlayButtons.add(
                    initializeOverlayButton(
                        context,
                        R.drawable.button_zr,
                        R.drawable.button_zr_pressed,
                        control,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.BUTTON_START -> overlayButtons.add(
                    initializeOverlayButton(
                        context,
                        R.drawable.button_start,
                        R.drawable.button_start_pressed,
                        control,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.BUTTON_SELECT -> overlayButtons.add(
                    initializeOverlayButton(
                        context,
                        R.drawable.button_select,
                        R.drawable.button_select_pressed,
                        control,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.DPAD -> overlayDpads.add(
                    initializeOverlayDpad(
                        context,
                        R.drawable.dpad,
                        R.drawable.dpad_pressed_one_direction,
                        R.drawable.dpad_pressed_two_directions,
                        NativeLibrary.ButtonType.DPAD_UP,
                        NativeLibrary.ButtonType.DPAD_DOWN,
                        NativeLibrary.ButtonType.DPAD_LEFT,
                        NativeLibrary.ButtonType.DPAD_RIGHT,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.STICK_MAIN -> overlayJoysticks.add(
                    initializeOverlayJoystick(
                        context,
                        R.drawable.stick_main_range,
                        R.drawable.stick_main,
                        R.drawable.stick_main_pressed,
                        NativeLibrary.ButtonType.STICK_LEFT,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.STICK_C -> overlayJoysticks.add(
                    initializeOverlayJoystick(
                        context,
                        R.drawable.stick_c_range,
                        R.drawable.stick_c,
                        R.drawable.stick_c_pressed,
                        NativeLibrary.ButtonType.STICK_C,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.BUTTON_HOME -> overlayButtons.add(
                    initializeOverlayButton(
                        context,
                        R.drawable.button_home,
                        R.drawable.button_home_pressed,
                        control,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.BUTTON_SWAP -> overlayButtons.add(
                    initializeOverlayButton(
                        context,
                        R.drawable.button_swap,
                        R.drawable.button_swap_pressed,
                        control,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.BUTTON_COMBO -> overlayButtons.add(
                    initializeOverlayButton(
                        context,
                        R.drawable.button_combo,
                        R.drawable.button_combo_pressed,
                        control,
                        orientation,
                        isEnabled
                    )
                )

                OverlayControl.BUTTON_TURBO,
                OverlayControl.BUTTON_TURBO_2,
                OverlayControl.BUTTON_TURBO_3,
                OverlayControl.BUTTON_QUICKSAVE,
                OverlayControl.BUTTON_QUICKLOAD,
                OverlayControl.BUTTON_PAUSE,
                OverlayControl.BUTTON_SCREENSHOT -> overlayButtons.add(
                    initializeOverlayButton(
                        context,
                        R.drawable.button_turbo,
                        R.drawable.button_turbo_pressed,
                        control,
                        orientation,
                        isEnabled
                    )
                )
            }
        }
    }

    fun refreshControls() {
        // Remove all the overlay buttons from the HashSet.
        overlayButtons.clear()
        overlayDpads.clear()
        overlayJoysticks.clear()
        val orientation =
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                "-Portrait"
            } else {
                ""
            }

        // In edit mode, always show controls so the user can configure them even if overlay was hidden
        if (EmulationMenuSettings.showOverlay || isInEditMode) {
            addOverlayControls(orientation)
        }
        invalidate()
    }

    private fun saveControlPosition(sharedPrefsId: Int, x: Int, y: Int, orientation: String) {
        preferences.edit()
            .putFloat("$sharedPrefsId$orientation-X", x.toFloat())
            .putFloat("$sharedPrefsId$orientation-Y", y.toFloat())
            .apply()
    }

    fun setIsInEditMode(editMode: Boolean) {
        if (this.isInEditMode == editMode) return
        this.isInEditMode = editMode
        if (!editMode) {
            selectedControl = null
            buttonBeingConfigured = null
            dpadBeingConfigured = null
            joystickBeingConfigured = null
        }
        refreshControls()
    }

    private fun defaultOverlay() {
        if (!preferences.getBoolean("OverlayInit", false)) {
            val aButtonPosition = preferences.getFloat(
                NativeLibrary.ButtonType.BUTTON_A.toString() + "-X",
                0f
            )
            if (aButtonPosition == 0f) {
                defaultOverlayLandscape()
            }

            val aButtonPositionPortrait = preferences.getFloat(
                NativeLibrary.ButtonType.BUTTON_A.toString() + "-Portrait" + "-X",
                0f
            )
            if (aButtonPositionPortrait == 0f) {
                defaultOverlayPortrait()
            }
        }

        preferences.edit()
            .putBoolean("OverlayInit", true)
            .apply()
    }

    fun resetButtonPlacement() {
        val isLandscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            defaultOverlayLandscape()
        } else {
            defaultOverlayPortrait()
        }
        refreshControls()
    }

    private fun getSafeScreenDimensions(forLandscape: Boolean): Pair<Float, Float> {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            (context as Activity).windowManager.defaultDisplay
        }
        var displayWidth: Float
        var displayHeight: Float
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayMetrics = (context as Activity).windowManager.maximumWindowMetrics.bounds
            displayWidth = displayMetrics.width().toFloat()
            displayHeight = displayMetrics.height().toFloat()
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getMetrics(displayMetrics)
            displayWidth = displayMetrics.widthPixels.toFloat()
            displayHeight = displayMetrics.heightPixels.toFloat()
        }
        val cutout = ViewCompat.getRootWindowInsets(this)?.displayCutout
        val marginsX = (cutout?.safeInsetLeft?.plus(cutout.safeInsetRight)) ?: 0
        val marginsY = (cutout?.safeInsetTop?.plus(cutout.safeInsetBottom)) ?: 0
        var maxX = displayWidth - marginsX
        var maxY = displayHeight - marginsY
        if (forLandscape) {
            if (maxY > maxX) {
                val tmp = maxX
                maxX = maxY
                maxY = tmp
            }
        } else {
            if (maxY < maxX) {
                val tmp = maxX
                maxX = maxY
                maxY = tmp
            }
        }
        return maxX to maxY
    }

    private fun defaultOverlayLandscape() {
        val (maxX, maxY) = getSafeScreenDimensions(forLandscape = true)
        val editor = preferences.edit()
        for (control in OverlayControl.entries) {
            editor.putFloat(
                control.xPrefKey(""),
                resources.getInteger(control.landscapeXRes).toFloat() / 1000f * maxX
            )
            editor.putFloat(
                control.yPrefKey(""),
                resources.getInteger(control.landscapeYRes).toFloat() / 1000f * maxY
            )
        }
        editor.apply()
    }

    private fun defaultOverlayPortrait() {
        val (maxX, maxY) = getSafeScreenDimensions(forLandscape = false)
        val portrait = "-Portrait"
        val editor = preferences.edit()
        for (control in OverlayControl.entries) {
            editor.putFloat(
                control.xPrefKey(portrait),
                resources.getInteger(control.portraitXRes).toFloat() / 1000f * maxX
            )
            editor.putFloat(
                control.yPrefKey(portrait),
                resources.getInteger(control.portraitYRes).toFloat() / 1000f * maxY
            )
        }
        editor.apply()
    }

    override fun isInEditMode(): Boolean = isInEditMode

    companion object {
        private const val GRID_COLS = 24f

        private val preferences
            get() = PreferenceManager.getDefaultSharedPreferences(CitraApplication.appContext)

        private fun ensureControlPositionInitialized(
            context: Context,
            control: OverlayControl,
            orientation: String
        ) {
            val xKey = control.xPrefKey(orientation)
            val yKey = control.yPrefKey(orientation)
            if (preferences.contains(xKey) && preferences.contains(yKey)) {
                return
            }
            val dm = context.resources.displayMetrics
            val isPortrait = orientation.isNotEmpty()
            val maxX = if (isPortrait) {
                min(dm.widthPixels, dm.heightPixels).toFloat()
            } else {
                kotlin.math.max(dm.widthPixels, dm.heightPixels).toFloat()
            }
            val maxY = if (isPortrait) {
                kotlin.math.max(dm.widthPixels, dm.heightPixels).toFloat()
            } else {
                min(dm.widthPixels, dm.heightPixels).toFloat()
            }
            val xRes = if (isPortrait) control.portraitXRes else control.landscapeXRes
            val yRes = if (isPortrait) control.portraitYRes else control.landscapeYRes
            val defX = context.resources.getInteger(xRes).toFloat() / 1000f * maxX
            val defY = context.resources.getInteger(yRes).toFloat() / 1000f * maxY
            preferences.edit()
                .putFloat(xKey, defX)
                .putFloat(yKey, defY)
                .apply()
        }

        /**
         * Resizes a [Bitmap] by a given scale factor
         */
        private fun getBitmap(context: Context, drawableId: Int, scale: Float): Bitmap {
            try {
                val bitmap = BitmapFactory.decodeResource(context.resources, drawableId)
                return resizeBitmap(context, bitmap, scale)
            } catch (_: NullPointerException) {
            }

            val vectorDrawable = ContextCompat.getDrawable(context, drawableId) as VectorDrawable

            val bitmap = Bitmap.createBitmap(
                (vectorDrawable.intrinsicWidth * scale).toInt().coerceAtLeast(1),
                (vectorDrawable.intrinsicHeight * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )

            val scaledBitmap = resizeBitmap(context, bitmap, scale)
            val canvas = Canvas(scaledBitmap)
            vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
            vectorDrawable.draw(canvas)
            return scaledBitmap
        }

        /**
         * Resizes a [Bitmap] by a given scale factor
         */
        private fun resizeBitmap(context: Context, bitmap: Bitmap, scale: Float): Bitmap {
            val dm = context.resources.displayMetrics
            val minDimension = min(dm.widthPixels, dm.heightPixels)
            val targetSize = (minDimension * scale).toInt().coerceAtLeast(8)
            return Bitmap.createScaledBitmap(
                bitmap,
                targetSize,
                targetSize,
                true
            )
        }

        private fun initializeOverlayButton(
            context: Context,
            defaultResId: Int,
            pressedResId: Int,
            control: OverlayControl,
            orientation: String,
            isEnabled: Boolean
        ): InputOverlayDrawableButton {
            val res = context.resources
            ensureControlPositionInitialized(context, control, orientation)

            var scale: Float = control.baseScale
            scale *= (preferences.getInt("controlScale", 50) + 50).toFloat()
            scale /= 100f

            scale *= (preferences.getInt(control.scalePrefKey, 50) + 50).toFloat()
            scale /= 100f

            val opacity: Int = preferences.getInt("controlOpacity", 50) * 255 / 100

            val defaultStateBitmap = getBitmap(context, defaultResId, scale)
            val pressedStateBitmap = getBitmap(context, pressedResId, scale)
            val overlayDrawable =
                InputOverlayDrawableButton(
                    res,
                    defaultStateBitmap,
                    pressedStateBitmap,
                    control.id,
                    opacity,
                    isEnabled
                )

            val xKey = control.xPrefKey(orientation)
            val yKey = control.yPrefKey(orientation)
            val drawableX = preferences.getFloat(xKey, 0f).toInt()
            val drawableY = preferences.getFloat(yKey, 0f).toInt()
            val width = overlayDrawable.width
            val height = overlayDrawable.height

            overlayDrawable.setBounds(drawableX, drawableY, drawableX + width, drawableY + height)
            overlayDrawable.setPosition(drawableX, drawableY)
            return overlayDrawable
        }

        private fun initializeOverlayDpad(
            context: Context,
            defaultResId: Int,
            pressedOneDirectionResId: Int,
            pressedTwoDirectionsResId: Int,
            buttonUp: Int,
            buttonDown: Int,
            buttonLeft: Int,
            buttonRight: Int,
            orientation: String,
            isEnabled: Boolean
        ): InputOverlayDrawableDpad {
            val res = context.resources
            ensureControlPositionInitialized(context, OverlayControl.DPAD, orientation)

            var scale = OverlayControl.DPAD.baseScale
            scale *= (preferences.getInt("controlScale", 50) + 50).toFloat()
            scale /= 100f

            scale *= (
                preferences.getInt(
                    OverlayControl.DPAD.scalePrefKey,
                    50
                ) + 50
                ).toFloat()

            scale /= 100f

            val opacity: Int = preferences.getInt("controlOpacity", 50) * 255 / 100

            val defaultStateBitmap = getBitmap(context, defaultResId, scale)
            val pressedOneDirectionStateBitmap = getBitmap(context, pressedOneDirectionResId, scale)
            val pressedTwoDirectionsStateBitmap =
                getBitmap(context, pressedTwoDirectionsResId, scale)
            val overlayDrawable = InputOverlayDrawableDpad(
                res,
                defaultStateBitmap,
                pressedOneDirectionStateBitmap,
                pressedTwoDirectionsStateBitmap,
                buttonUp,
                buttonDown,
                buttonLeft,
                buttonRight,
                opacity,
                isEnabled
            )

            val drawableX = preferences.getFloat("$buttonUp$orientation-X", 0f).toInt()
            val drawableY = preferences.getFloat("$buttonUp$orientation-Y", 0f).toInt()
            val width = overlayDrawable.width
            val height = overlayDrawable.height

            overlayDrawable.setBounds(drawableX, drawableY, drawableX + width, drawableY + height)
            overlayDrawable.setPosition(drawableX, drawableY)
            return overlayDrawable
        }

        private fun initializeOverlayJoystick(
            context: Context,
            resOuter: Int,
            defaultResInner: Int,
            pressedResInner: Int,
            joystick: Int,
            orientation: String,
            isEnabled: Boolean
        ): InputOverlayDrawableJoystick {
            val res = context.resources
            val control = OverlayControl.fromId(joystick) ?: OverlayControl.STICK_MAIN
            ensureControlPositionInitialized(context, control, orientation)

            var scale = control.baseScale
            scale *= (preferences.getInt("controlScale", 50) + 50).toFloat()
            scale /= 100f

            scale *= (preferences.getInt("controlScale-$joystick", 50) + 50).toFloat()
            scale /= 100f

            val opacity: Int = preferences.getInt("controlOpacity", 50) * 255 / 100

            val bitmapOuter = getBitmap(context, resOuter, scale)
            val bitmapInnerDefault = getBitmap(context, defaultResInner, scale)
            val bitmapInnerPressed = getBitmap(context, pressedResInner, scale)

            val drawableX = preferences.getFloat("$joystick$orientation-X", 0f).toInt()
            val drawableY = preferences.getFloat("$joystick$orientation-Y", 0f).toInt()

            var outerScale = 1f
            if (joystick == NativeLibrary.ButtonType.STICK_C) {
                outerScale = 2f
            }

            val outerSize = bitmapOuter.width
            val outerRect = Rect(
                drawableX,
                drawableY,
                drawableX + (outerSize / outerScale).toInt(),
                drawableY + (outerSize / outerScale).toInt()
            )
            val innerRect =
                Rect(0, 0, (outerSize / outerScale).toInt(), (outerSize / outerScale).toInt())

            val overlayDrawable = InputOverlayDrawableJoystick(
                res,
                bitmapOuter,
                bitmapInnerDefault,
                bitmapInnerPressed,
                outerRect,
                innerRect,
                joystick,
                opacity,
                isEnabled
            )

            overlayDrawable.setPosition(drawableX, drawableY)
            return overlayDrawable
        }
    }
}
