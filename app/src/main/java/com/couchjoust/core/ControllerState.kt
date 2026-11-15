package com.couchjoust.core

/**
 * Platform-independent reduction of one assigned controller's held inputs.
 * Android adapter supplies KeyEvent states and MotionEvent AXIS_X/HAT_X values.
 * Do not call flap impulses from onKeyDown. Sample this snapshot in the game tick.
 * Axis threshold is a port choice; the original game has digital switches.
 */
class ControllerState {
    var leftKey = false
    var rightKey = false
    var flapKey = false
    private var stick = 0
    private var hat = 0

    fun setStick(x: Float, reportedFlat: Float) {
        val threshold = maxOf(reportedFlat.coerceIn(0f, 1f), 0.5f)
        stick = when {
            x < -threshold -> -1
            x > threshold -> 1
            else -> 0
        }
    }

    fun setHat(x: Float) {
        hat = when {
            x < -0.5f -> -1
            x > 0.5f -> 1
            else -> 0
        }
    }

    data class Sample(val left: Boolean, val right: Boolean, val flap: Boolean) {
        val direction: Int get() = GameMath.direction(left, right)
    }

    fun sample() = Sample(
        leftKey || stick < 0 || hat < 0,
        rightKey || stick > 0 || hat > 0,
        flapKey
    )

    fun clearOnPauseOrDisconnect() {
        leftKey = false
        rightKey = false
        flapKey = false
        stick = 0
        hat = 0
    }
}
