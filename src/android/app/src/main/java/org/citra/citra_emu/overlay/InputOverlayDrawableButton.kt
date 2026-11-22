// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv3 or any later version
// Refer to the LICENSE.txt file included.

package org.citra.citra_emu.overlay

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import kotlin.math.min
import org.citra.citra_emu.NativeLibrary
import org.citra.citra_emu.utils.EmulationMenuSettings
import org.citra.citra_emu.utils.TurboHelper

enum class ButtonSlidingMode(val int: Int) {
    // Disabled, buttons can only be triggered by pressing them directly.
    Disabled(0),

    // Additionally to pressing buttons directly, they can be activated and released by sliding into
    // and out of their area.
    Enabled(1),

    // The first button is kept activated until released, further buttons use the simple button
    // sliding method.
    Alternative(2)
}

/**
 * Custom [BitmapDrawable] that is capable
 * of storing its own ID and rendering ARMSX2-style action/turbo badges.
 */
class InputOverlayDrawableButton(
    res: Resources,
    defaultStateBitmap: Bitmap,
    pressedStateBitmap: Bitmap,
    val id: Int,
    val opacity: Int,
    var enabled: Boolean = true
) {
    var trackId: Int

    private var isMotionFirstButton = false
    private var longPressTriggered = false
    private var longPressRunnable: Runnable? = null

    private var previousTouchX = 0
    private var previousTouchY = 0
    private var controlPositionX = 0
    private var controlPositionY = 0
    val width: Int
    val height: Int
    private val defaultStateBitmap: BitmapDrawable
    private val pressedStateBitmap: BitmapDrawable
    private var pressedState = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val glyphPath = Path()
    private val glyphRect = RectF()

    init {
        this.defaultStateBitmap = BitmapDrawable(res, defaultStateBitmap)
        this.pressedStateBitmap = BitmapDrawable(res, pressedStateBitmap)
        trackId = -1
        width = this.defaultStateBitmap.intrinsicWidth
        height = this.defaultStateBitmap.intrinsicHeight
    }

    val isCustomActionBadge: Boolean
        get() = when (id) {
            NativeLibrary.ButtonType.BUTTON_TURBO,
            NativeLibrary.ButtonType.BUTTON_TURBO_2,
            NativeLibrary.ButtonType.BUTTON_TURBO_3,
            NativeLibrary.ButtonType.BUTTON_QUICKSAVE,
            NativeLibrary.ButtonType.BUTTON_QUICKLOAD,
            NativeLibrary.ButtonType.BUTTON_PAUSE,
            NativeLibrary.ButtonType.BUTTON_SCREENSHOT -> true

            else -> false
        }

    private val supportsLongPress: Boolean
        get() = when (id) {
            NativeLibrary.ButtonType.BUTTON_QUICKSAVE,
            NativeLibrary.ButtonType.BUTTON_QUICKLOAD,
            NativeLibrary.ButtonType.BUTTON_PAUSE -> true

            else -> false
        }

    /**
     * Updates button status based on the motion event.
     *
     * @return true if value was changed
     */
    fun updateStatus(
        event: MotionEvent,
        pointerIndex: Int,
        hasActiveButtons: Boolean,
        overlay: InputOverlay
    ): Boolean {
        if (!enabled) return false
        val buttonSliding = EmulationMenuSettings.buttonSlide
        val xPosition = event.getX(pointerIndex).toInt()
        val yPosition = event.getY(pointerIndex).toInt()
        val pointerId = event.getPointerId(pointerIndex)
        val motionEvent = event.action and MotionEvent.ACTION_MASK
        val isActionDown =
            motionEvent == MotionEvent.ACTION_DOWN || motionEvent == MotionEvent.ACTION_POINTER_DOWN
        val isActionUp =
            motionEvent == MotionEvent.ACTION_UP || motionEvent == MotionEvent.ACTION_POINTER_UP
        if (isActionDown) {
            if (!bounds.contains(xPosition, yPosition)) {
                return false
            }
            buttonDown(true, pointerId, overlay)
            return true
        }
        if (isActionUp) {
            if (trackId != pointerId) {
                return false
            }
            buttonUp(overlay, false)
            return true
        }

        val isActionMoving = motionEvent == MotionEvent.ACTION_MOVE
        if (buttonSliding != ButtonSlidingMode.Disabled.int && isActionMoving) {
            val inside = bounds.contains(xPosition, yPosition)
            if (pressedState) {
                if (inside || trackId != pointerId) {
                    return false
                }
                if (buttonSliding == ButtonSlidingMode.Alternative.int && isMotionFirstButton) {
                    return false
                }
                val preserveTrackId = (buttonSliding != ButtonSlidingMode.Disabled.int)
                buttonUp(overlay, preserveTrackId)
                return true
            } else {
                if (!inside) {
                    return false
                }
                buttonDown(!hasActiveButtons, pointerId, overlay)
                return true
            }
        }

        return false
    }

    private fun buttonDown(firstBtn: Boolean, pointerId: Int, overlay: InputOverlay) {
        pressedState = true
        isMotionFirstButton = firstBtn
        longPressTriggered = false
        trackId = pointerId
        overlay.hapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

        cancelLongPress(overlay)
        if (supportsLongPress) {
            val runnable = Runnable {
                if (pressedState && trackId == pointerId) {
                    longPressTriggered = true
                    overlay.hapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    overlay.onActionLongPress(id)
                }
            }
            longPressRunnable = runnable
            overlay.postDelayed(runnable, LONG_PRESS_TIMEOUT_MS)
        }
    }

    private fun buttonUp(overlay: InputOverlay, preserveTrackId: Boolean) {
        val wasLongPressed = longPressTriggered
        cancelLongPress(overlay)
        pressedState = false
        isMotionFirstButton = false
        longPressTriggered = false
        if (!preserveTrackId) {
            trackId = -1
        }
        overlay.hapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        if (supportsLongPress && !wasLongPressed) {
            overlay.onActionShortTap(id)
        }
    }

    private fun cancelLongPress(overlay: InputOverlay) {
        longPressRunnable?.let { overlay.removeCallbacks(it) }
        longPressRunnable = null
    }

    fun onConfigureTouch(event: MotionEvent): Boolean {
        val pointerIndex = event.actionIndex
        val fingerPositionX = event.getX(pointerIndex).toInt()
        val fingerPositionY = event.getY(pointerIndex).toInt()
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                previousTouchX = fingerPositionX
                previousTouchY = fingerPositionY
            }

            MotionEvent.ACTION_MOVE -> {
                controlPositionX += fingerPositionX - previousTouchX
                controlPositionY += fingerPositionY - previousTouchY
                setBounds(
                    controlPositionX,
                    controlPositionY,
                    width + controlPositionX,
                    height + controlPositionY
                )
                previousTouchX = fingerPositionX
                previousTouchY = fingerPositionY
            }
        }
        return true
    }

    fun setPosition(x: Int, y: Int) {
        controlPositionX = x
        controlPositionY = y
    }

    fun draw(canvas: Canvas, isPaused: Boolean = false, isEditMode: Boolean = false) {
        val effectiveOpacity = if (isEditMode && !enabled) {
            (opacity * 0.45f).toInt().coerceIn(40, 255)
        } else {
            opacity
        }

        if (isCustomActionBadge) {
            drawArmsBadge(canvas, effectiveOpacity, isPaused)
        } else {
            val bitmapDrawable: BitmapDrawable = currentStateBitmapDrawable
            bitmapDrawable.alpha = effectiveOpacity
            bitmapDrawable.draw(canvas)
        }
    }

    private fun legibleAlpha(rawOpacity: Int, floorFraction: Float = 0.38f): Int {
        val norm = (rawOpacity / 255f).coerceIn(0f, 1f)
        val floored = norm.coerceIn(floorFraction, 1f) * (norm / floorFraction).coerceIn(0f, 1f)
        return (floored * 255f).toInt().coerceIn(0, 255)
    }

    private fun drawArmsBadge(canvas: Canvas, effectiveOpacity: Int, isPaused: Boolean) {
        val b = bounds
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val radius = min(b.width(), b.height()) * 0.44f
        if (radius <= 2f) return

        val turboSlot = TurboHelper.slotForButtonId(id)
        val isTurboActive = turboSlot > 0 && TurboHelper.isSlotActive(turboSlot)
        val isPauseActive = id == NativeLibrary.ButtonType.BUTTON_PAUSE && isPaused
        val isLit = pressedState || isTurboActive || isPauseActive

        val bgAlpha = if (isLit) {
            legibleAlpha(effectiveOpacity, 0.55f)
        } else if (id == NativeLibrary.ButtonType.BUTTON_PAUSE) {
            (effectiveOpacity * 0.52f).toInt().coerceIn(0, 200)
        } else {
            (effectiveOpacity * 0.36f).toInt().coerceIn(0, 180)
        }

        fillPaint.color = if (isLit) {
            Color.argb(bgAlpha, 0x3A, 0x6E, 0xA5)
        } else {
            Color.argb(bgAlpha, 0x0A, 0x0A, 0x10)
        }
        canvas.drawCircle(cx, cy, radius, fillPaint)

        val borderAlpha = if (isLit) {
            legibleAlpha(effectiveOpacity, 0.75f)
        } else {
            (effectiveOpacity * 0.50f).toInt().coerceIn(0, 220)
        }
        borderPaint.strokeWidth = (radius * 0.07f).coerceAtLeast(2f)
        borderPaint.color = Color.argb(borderAlpha, 0xFF, 0xFF, 0xFF)
        canvas.drawCircle(cx, cy, radius, borderPaint)

        val fgAlpha = legibleAlpha(effectiveOpacity, 0.45f)
        textPaint.color = Color.argb(fgAlpha, 0xFF, 0xFF, 0xFF)

        when (id) {
            NativeLibrary.ButtonType.BUTTON_PAUSE -> {
                fillPaint.color = Color.argb(fgAlpha, 0xFF, 0xFF, 0xFF)
                if (isPaused) {
                    // Draw Play triangle ▶
                    val triHalf = radius * 0.38f
                    glyphPath.reset()
                    glyphPath.moveTo(cx - triHalf * 0.65f, cy - triHalf)
                    glyphPath.lineTo(cx + triHalf, cy)
                    glyphPath.lineTo(cx - triHalf * 0.65f, cy + triHalf)
                    glyphPath.close()
                    canvas.drawPath(glyphPath, fillPaint)
                } else {
                    // Draw ARMSX2-style two rounded pause bars ⏸
                    val barW = radius * 0.20f
                    val barH = radius * 0.70f
                    val gap = radius * 0.14f
                    val corner = barW / 2f
                    glyphRect.set(cx - gap - barW, cy - barH / 2f, cx - gap, cy + barH / 2f)
                    canvas.drawRoundRect(glyphRect, corner, corner, fillPaint)
                    glyphRect.set(cx + gap, cy - barH / 2f, cx + gap + barW, cy + barH / 2f)
                    canvas.drawRoundRect(glyphRect, corner, corner, fillPaint)
                }
            }

            NativeLibrary.ButtonType.BUTTON_TURBO,
            NativeLibrary.ButtonType.BUTTON_TURBO_2,
            NativeLibrary.ButtonType.BUTTON_TURBO_3 -> {
                val topLabel = if (turboSlot == 1) "▶▶" else "▶▶$turboSlot"
                val speedLabel = TurboHelper.getSlotSpeedLabel(turboSlot)
                textPaint.textSize = radius * 0.56f
                canvas.drawText(topLabel, cx, cy - radius * 0.04f, textPaint)
                textPaint.textSize = radius * 0.42f
                canvas.drawText(speedLabel, cx, cy + radius * 0.48f, textPaint)
            }

            NativeLibrary.ButtonType.BUTTON_QUICKSAVE -> {
                textPaint.textSize = radius * 0.52f
                val yOffset = (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText("SAVE", cx, cy - yOffset, textPaint)
            }

            NativeLibrary.ButtonType.BUTTON_QUICKLOAD -> {
                textPaint.textSize = radius * 0.52f
                val yOffset = (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText("LOAD", cx, cy - yOffset, textPaint)
            }

            NativeLibrary.ButtonType.BUTTON_SCREENSHOT -> {
                textPaint.textSize = radius * 0.52f
                val yOffset = (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText("SHOT", cx, cy - yOffset, textPaint)
            }
        }
    }

    private val currentStateBitmapDrawable: BitmapDrawable
        get() = if (pressedState) pressedStateBitmap else defaultStateBitmap

    fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        defaultStateBitmap.setBounds(left, top, right, bottom)
        pressedStateBitmap.setBounds(left, top, right, bottom)
    }

    val status: Int
        get() = if (pressedState) {
            NativeLibrary.ButtonState.PRESSED
        } else {
            NativeLibrary.ButtonState.RELEASED
        }
    val bounds: Rect
        get() = defaultStateBitmap.bounds

    companion object {
        private const val LONG_PRESS_TIMEOUT_MS = 450L
    }
}
