package com.couchjoust

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import computer.iroh.IrohAndroid

class NetworkActivity : ComponentActivity(), InputManager.InputDeviceListener {
    lateinit var match: NetworkMatch
    private lateinit var audio: GameAudio
    private lateinit var arena: Arena
    private lateinit var status: TextView
    private lateinit var setup: LinearLayout
    private lateinit var readyButton: Button
    private lateinit var startButton: Button
    private lateinit var pauseButton: Button
    private lateinit var leaveButton: Button
    private lateinit var devices: InputManager
    private var pending: (() -> Unit)? = null
    private var previousState = ""
    private var keys = 0
    private var axis = 0
    private var lastInput = 0
    private var audioTick = 0L
    private var soundMatch = 0
    private var controller: Int? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        IrohAndroid.installAndroidContext(applicationContext)
        audio = GameAudio(this)
        audio.enabled = getSharedPreferences("joust", 0).getBoolean("sound", true)
        match = NetworkMatch({ name, active -> audio.sequencer.request(name, active) }) { refresh() }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32, 12, 32, 12); setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(this).apply { text = "TWO-TV JOUST"; textSize = 22f; setTextColor(Color.WHITE) })
        status = TextView(this).apply { textSize = 18f; setTextColor(Color.WHITE) }; root.addView(status)
        setup = LinearLayout(this)
        fun button(name: String, id: String, action: () -> Unit) = Button(this).apply {
            text = name; tag = id; setOnClickListener { action() }
        }
        val phrase = EditText(this).apply {
            tag = "pairing-code"; hint = "number-word-word"; setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            contentDescription = "Match code from host TV"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
        }
        val host = button("Host match", "host") { begin { match.host() } }
        setup.addView(host)
        setup.addView(phrase, LinearLayout.LayoutParams(0, -2, 1f))
        setup.addView(button("Join match", "join") { begin { match.join(phrase.text.toString()) } })
        root.addView(setup)
        val actions = LinearLayout(this)
        readyButton = button("Ready", "ready") { match.setReady() }
        startButton = button("Start match", "start") { match.start() }
        pauseButton = button("Pause", "pause") { if (match.state == "PAUSED") match.resume() else match.pause() }
        leaveButton = button("Leave / return", "leave") { match.close(); finish() }
        listOf(readyButton, startButton, pauseButton, leaveButton).forEach { actions.addView(it) }
        root.addView(actions)
        arena = Arena(this)
        root.addView(arena, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(this).apply {
            text = "One player per TV. Left/right: move. Center/A: flap. Back/Start: pause. Internet required for pairing."
            textSize = 14f; setTextColor(Color.WHITE)
        })
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (match.state == "PLAY") match.pause() else { match.close(); finish() } }
        })
        devices = getSystemService(InputManager::class.java); devices.registerInputDeviceListener(this, null)
        refresh(); host.requestFocus()
    }
    private fun begin(action: () -> Unit) {
        setup.visibility = View.GONE
        if (Build.VERSION.SDK_INT >= 37 && checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) {
            pending = action; requestPermissions(arrayOf("android.permission.ACCESS_LOCAL_NETWORK"), 1)
        } else action()
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 1) { enforceCallingOrSelfPermission("android.permission.ACCESS_LOCAL_NETWORK", null); pending!!(); pending = null }
    }
    private fun refresh() {
        if (soundMatch != match.matches) { audio.sequencer.reset(); soundMatch = match.matches; audioTick = 0 }
        if (match.game.tick != audioTick) { audio.sequencer.tick(); audioTick = match.game.tick }
        status.text = match.status + if (match.code.isNotEmpty()) "\n${match.code}" else ""
        val lobby = match.state == "LOBBY" || match.state == "RESULTS"
        if (lobby) status.append("\nP1 ${if (match.ready[0]) "ready" else "not ready"}   P2 ${if (match.ready[1]) "ready" else "not ready"}")
        readyButton.visibility = if (lobby) View.VISIBLE else View.GONE
        readyButton.text = if (match.ready[match.localPlayer]) "Not ready" else if (match.state == "RESULTS") "Ready for rematch" else "Ready"
        startButton.visibility = if (lobby && match.isHost) View.VISIBLE else View.GONE
        startButton.isEnabled = match.ready.all { it }
        startButton.text = if (match.state == "RESULTS") "Start rematch" else "Start match"
        pauseButton.visibility = if (match.state in listOf("PLAY", "PAUSED")) View.VISIBLE else View.GONE
        pauseButton.text = if (match.state == "PAUSED") "Resume match" else "Pause"
        if (previousState != match.state) {
            keys = 0; axis = 0; lastInput = 0
            when (match.state) {
                "PLAY" -> { arena.requestFocus(); audio.resume() }
                "PAUSED" -> { pauseButton.requestFocus(); audio.pause() }
                "LOBBY", "RESULTS" -> { readyButton.requestFocus(); audio.pause() }
                "LEFT" -> { leaveButton.requestFocus(); audio.pause() }
            }
            previousState = match.state
        }
        arena.invalidate()
    }
    private fun sendInput() {
        val bits = keys or axis
        if (bits != lastInput) { lastInput = bits; match.input(bits) }
    }
    private fun key(event: KeyEvent): Boolean {
        if (match.state == "PLAY") {
            val bit = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> 1
                KeyEvent.KEYCODE_DPAD_RIGHT -> 2
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_SPACE -> 4
                else -> 0
            }
            if (bit != 0) {
                controller = event.deviceId
                keys = if (event.action == KeyEvent.ACTION_DOWN) keys or bit else keys and bit.inv()
                sendInput(); return true
            }
            if (event.keyCode in intArrayOf(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_B)) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) match.pause()
                return true
            }
        }
        return false
    }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (match.state == "PLAY" && event.source and InputDevice.SOURCE_JOYSTICK != 0 && event.action == MotionEvent.ACTION_MOVE) {
            controller = event.deviceId
            val x = event.getAxisValue(MotionEvent.AXIS_X); val hat = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            axis = (if (x < -0.5f || hat < -0.5f) 1 else 0) or (if (x > 0.5f || hat > 0.5f) 2 else 0)
            sendInput(); return true
        }
        return super.onGenericMotionEvent(event)
    }
    override fun onInputDeviceAdded(id: Int) {}
    override fun onInputDeviceChanged(id: Int) {}
    override fun onInputDeviceRemoved(id: Int) { if (id == controller) match.pause() }
    override fun onWindowFocusChanged(focus: Boolean) { super.onWindowFocusChanged(focus); if (!focus && ::match.isInitialized) match.pause() }
    override fun onStop() { if (pending == null) { match.close(); finish() }; super.onStop() }
    override fun onDestroy() { devices.unregisterInputDeviceListener(this); match.close(); audio.close(); super.onDestroy() }
    private inner class Arena(context: Context) : View(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean = key(event) || super.dispatchKeyEvent(event)
        val art = Artwork(context)
        init { isFocusable = true; isFocusableInTouchMode = true }
        override fun onDraw(canvas: Canvas) {
            if (match.game.active) {
                art.arena(match.game)
                art.blit(canvas, width, height, getSharedPreferences("joust", 0).getFloat("overscan", 1f))
            }
        }
    }
}
