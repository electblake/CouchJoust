package com.couchjoust

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent

/** Real rendezvous + real iroh across two Android processes; orchestrated by tools/connection_smoke.py. */
class ConnectionScenario(private val instrumentation: Instrumentation) {
    fun run(arguments: Bundle) = with(instrumentation) {
        val host = arguments.getString("connectionRole") == "host"
        val monitor = addMonitor(ConnectionActivity::class.java.name, null, false)
        targetContext.startActivity(Intent(targetContext, ConnectionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val activity = waitForMonitorWithTimeout(monitor, 10_000) as ConnectionActivity
        removeMonitor(monitor)
        runOnMainSync {
            val root = activity.window.decorView
            if (host) root.findViewWithTag<android.widget.Button>("host").performClick() else {
                root.findViewWithTag<android.widget.EditText>("pairing-code").setText(arguments.getString("code")!!)
                root.findViewWithTag<android.widget.Button>("join").performClick()
            }
        }
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + 90_000_000_000L
            var ready = false
            while (!ready && System.nanoTime() < deadline) {
                runOnMainSync { ready = condition() }
                Thread.sleep(50)
            }
            check(ready) { "Connection test timed out: ${activity.probe.status}" }
        }
        if (android.os.Build.VERSION.SDK_INT >= 37 && activity.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            uiAutomation.serviceInfo = uiAutomation.serviceInfo.apply {
                flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            var allow: android.view.accessibility.AccessibilityNodeInfo? = null
            await {
                allow = uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/permission_allow_button")
                    ?.firstOrNull()
                allow != null
            }
            check(allow!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            await { activity.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") == android.content.pm.PackageManager.PERMISSION_GRANTED }
            sendStatus(0, Bundle().apply { putString("stream", "PASS Android 17 local-network permission prompt\n") })
        }
        if (host) {
            await { activity.probe.code.isNotEmpty() }
            sendStatus(0, Bundle().apply { putString("pairing_code", activity.probe.code) })
        }
        await { activity.probe.connected }
        await { activity.hasWindowFocus() }
        // Actual OS input dispatch exercises the screen's controller routing.
        fun press(code: Int) {
            uiAutomation.executeShellCommand("input keyevent $code").use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
            }
        }
        press(if (host) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
        runOnMainSync {
            check(if (host) activity.probe.localX < 0.5f else activity.probe.localX > 0.5f) { "First D-pad press was not delivered" }
        }
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        await { activity.probe.received >= 300 && activity.probe.remoteFlaps == 1 && activity.probe.echoes >= 3 }
        runOnMainSync {
            check(if (host) activity.probe.remoteX > 0.5f else activity.probe.remoteX < 0.5f) {
                "Position did not cross transport: local=${activity.probe.localX}, remote=${activity.probe.remoteX}"
            }
            check(activity.probe.localFlaps == 1)
            check(activity.probe.paths.isNotEmpty())
        }
        val screenshot = uiAutomation.takeScreenshot()
        java.io.File(targetContext.getExternalFilesDir(null), "connection-${if(host) "host" else "guest"}.png")
            .outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        sendStatus(0, Bundle().apply {
            putString("stream", "PASS live ${if(host) "host" else "guest"}: received=${activity.probe.received}, paths=${activity.probe.paths}, echo=${activity.probe.pingMs}ms\n")
        })
        // Host initiates normal leave; guest verifies peer departure without an exception or retry.
        if (host) {
            Thread.sleep(3000)
            runOnMainSync { activity.probe.close() }
        }
        await { !activity.probe.connected }
        runOnMainSync { activity.finish() }
        finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS connection prototype\n") })
    }
}
