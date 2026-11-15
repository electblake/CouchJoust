package com.couchjoust

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import java.io.File

class NetworkScenario(private val instrumentation: Instrumentation) {
    fun run(arguments: Bundle) = with(instrumentation) {
        val host = arguments.getString("networkRole") == "host"
        val monitor = addMonitor(NetworkActivity::class.java.name, null, false)
        targetContext.startActivity(Intent(targetContext, NetworkActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val activity = waitForMonitorWithTimeout(monitor, 10_000) as NetworkActivity
        removeMonitor(monitor)
        val match = activity.match
        val records = StringBuilder()
        val observed = IntArray(2)
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + 240_000_000_000L
            var done = false
            while (!done && System.nanoTime() < deadline) { runOnMainSync { done = condition() }; Thread.sleep(20) }
            check(done) { "Timed out: ${match.state} ${match.status} tick=${match.game.tick}" }
        }
        fun click(tag: String) = runOnMainSync { activity.window.decorView.findViewWithTag<Button>(tag).performClick() }
        runOnMainSync {
            match.onTick = { game ->
                game.controls.forEachIndexed { i, input ->
                    val sample = input.sample()
                    observed[i] = observed[i] or (if (sample.left) 1 else 0) or (if (sample.right) 2 else 0) or (if (sample.flap) 4 else 0)
                }
                val terrain = game.terrain
                records.append("${match.matches}|${game.snapshot()};T:${terrain.solidX.contentHashCode()},${terrain.landingX.contentHashCode()},${terrain.lavaY},${terrain.burnLeft},${terrain.burnRight},${terrain.occupied.contentHashCode()};W:${game.wavesEntered},${game.gameOver},${game.overTimer}\n")
                if (game.tick % 3000L == 0L) sendStatus(0, Bundle().apply { putString("stream", "RUN match ${match.matches} tick ${game.tick} lives ${game.players.map { it.lives }}\n") })
            }
            if (!host) activity.window.decorView.findViewWithTag<EditText>("pairing-code").setText(arguments.getString("code")!!)
        }
        click(if (host) "host" else "join")
        if (host) { await { match.code.isNotEmpty() }; sendStatus(0, Bundle().apply { putString("pairing_code", match.code) }) }
        await { match.state == "LOBBY" }
        runOnMainSync { check(!match.ready[match.localPlayer]); check(!activity.window.decorView.findViewWithTag<Button>("start").isEnabled) }
        click("ready"); await { match.ready.all { it } || match.state == "PLAY" }
        if (host) click("start")
        await { match.state == "PLAY" && activity.hasWindowFocus() }
        fun key(code: Int, down: Boolean) = sendKeySync(KeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, code))
        key(if (host) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT, true)
        repeat(12) { key(KeyEvent.KEYCODE_DPAD_CENTER, true); Thread.sleep(60); key(KeyEvent.KEYCODE_DPAD_CENTER, false); Thread.sleep(60) }
        key(if (host) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT, false)
        await { match.game.tick >= 180 }
        if (!host) click("pause")
        await { match.state == "PAUSED" }
        val stopped = match.game.tick
        Thread.sleep(300)
        runOnMainSync { check(match.game.tick == stopped); check(match.game.paused) }
        if (host) { Thread.sleep(32_000); click("pause") }
        await { match.state == "PLAY" }
        await { match.game.tick >= 600 }
        key(KeyEvent.KEYCODE_DPAD_RIGHT, true)
        val image = uiAutomation.takeScreenshot()
        File(targetContext.getExternalFilesDir(null), "network-${if (host) "host" else "guest"}.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        // Accelerate only the test host's clock after exercising real-time controls/pause.
        if (host) runOnMainSync { match.tickDelayMs = 1 }
        await { match.state == "RESULTS" }
        runOnMainSync {
            check(match.game.gameOver); check(!match.ready[match.localPlayer])
            check(observed[0] and 5 == 5 && observed[1] and 6 == 6) { "Both players' movement/flap inputs must reach both simulations: ${observed.toList()}" }
        }
        sendStatus(0, Bundle().apply { putString("stream", "PASS shared results at tick ${match.game.tick}: ${match.status}\n") })
        click("ready"); await { match.ready.all { it } || match.matches == 2 }
        if (host) click("start")
        await { match.matches == 2 && match.game.tick >= 120 }
        runOnMainSync { check(match.game.players.all { it.score == 0L }); check(match.game.waveNumber == 1) }
        if (!host) runOnMainSync { match.close() }
        await { match.state == "LEFT" }
        File(targetContext.getExternalFilesDir(null), "network-${if (host) "host" else "guest"}.txt").writeText(records.toString())
        Thread.sleep(500)
        runOnMainSync { activity.finish() }
        finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS network match: pairing, readiness, controls, pause/resume, game over, rematch and peer leave\n") })
    }
}
