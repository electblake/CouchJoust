package com.couchjoust

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import computer.iroh.IrohAndroid

/** Remote-only pairing and transport diagnostic. Deliberately separate from the game simulation. */
class ConnectionActivity : Activity() {
    lateinit var probe: ConnectionProbe
    private lateinit var status: TextView
    private lateinit var metrics: TextView
    private lateinit var arena: ProbeArena
    private lateinit var setup: LinearLayout
    private var pendingConnection: (() -> Unit)? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        IrohAndroid.installAndroidContext(applicationContext)
        probe = ConnectionProbe { refresh() }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            setBackgroundColor(Color.rgb(12, 17, 26))
        }
        fun label(text: String, size: Float) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(Color.WHITE)
        }
        root.addView(label("TWO-TV CONNECTION PROTOTYPE", 24f))
        root.addView(label("Pair TVs and test live controls. Networked Joust gameplay is not included yet.", 16f))
        status = label(probe.status, 22f); root.addView(status)
        setup = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val phrase = EditText(this).apply {
            tag = "pairing-code"
            hint = "number-word-word"
            contentDescription = "Pairing code from host TV"
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setSingleLine()
        }
        val host = Button(this).apply {
            tag = "host"
            text = "Host"
            setOnClickListener { beginConnection { probe.host() } }
        }
        val join = Button(this).apply {
            tag = "join"
            text = "Join"
            setOnClickListener { beginConnection { probe.join(phrase.text.toString()) } }
        }
        setup.addView(host)
        setup.addView(phrase, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        setup.addView(join)
        root.addView(setup)
        metrics = label("Internet required for phrase pairing. No account or port forwarding.", 16f)
        root.addView(metrics)
        arena = ProbeArena(this)
        root.addView(arena, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(label("Connected: D-pad moves your yellow marker; Center/A tests flap. Other TV is blue. Back leaves.", 16f))
        root.addView(Button(this).apply { text = "Leave connection test"; setOnClickListener { finish() } })
        setContentView(root)
        host.requestFocus()
    }

    private fun beginConnection(action: () -> Unit) {
        setup.visibility = View.GONE
        if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) {
            pendingConnection = action
            requestPermissions(arrayOf("android.permission.ACCESS_LOCAL_NETWORK"), 1)
        } else action()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            // Android enforces the required permission; denied access is not replaced by relay-only operation.
            enforceCallingOrSelfPermission("android.permission.ACCESS_LOCAL_NETWORK", null)
            pendingConnection!!()
            pendingConnection = null
        }
    }

    private fun refresh() {
        status.text = probe.status + if (probe.code.isNotEmpty()) "\n${probe.code}" else ""
        if (probe.connected) {
            arena.requestFocus()
            metrics.text = "${probe.paths}\nSent ${probe.sent} / received ${probe.received}   Stream echo ${probe.pingMs} ms\n" +
                "Flaps: you ${probe.localFlaps} / other TV ${probe.remoteFlaps}"
            arena.invalidate()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (probe.connected && event.keyCode in intArrayOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> probe.localX = (probe.localX - 0.03f).coerceAtLeast(0f)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> probe.localX = (probe.localX + 0.03f).coerceAtMost(1f)
                    KeyEvent.KEYCODE_DPAD_UP -> probe.localY = (probe.localY - 0.05f).coerceAtLeast(0f)
                    KeyEvent.KEYCODE_DPAD_DOWN -> probe.localY = (probe.localY + 0.05f).coerceAtMost(1f)
                    else -> if (event.repeatCount == 0) probe.localFlaps++
                }
                refresh()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        // A full-screen TV permission prompt is part of joining, not departure from a session.
        if (pendingConnection == null) { probe.close(); finish() }
        super.onStop()
    }

    private inner class ProbeArena(context: Context) : View(context) {
        init { isFocusable = true; isFocusableInTouchMode = true }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            paint.color = Color.DKGRAY
            canvas.drawRect(16f, 16f, width - 16f, height - 16f, paint)
            paint.color = Color.CYAN
            canvas.drawCircle(32 + probe.remoteX * (width - 64), 32 + probe.remoteY * (height - 64), 18f, paint)
            paint.color = Color.YELLOW
            canvas.drawCircle(32 + probe.localX * (width - 64), 32 + probe.localY * (height - 64), 12f, paint)
        }
    }
}
