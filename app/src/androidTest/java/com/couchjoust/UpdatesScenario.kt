package com.couchjoust

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.KeyEvent
import java.io.File
import kotlinx.coroutines.runBlocking

class UpdatesScenario(private val instrumentation: Instrumentation) {
    fun run(arguments: Bundle) = with(instrumentation) {
        if (arguments.getString("updates") == "verify-install") {
            check(targetContext.packageManager.getPackageInfo(targetContext.packageName, 0).versionName == "2.0.0-alpha.3")
            check(targetContext.getSharedPreferences("joust", 0).getString("updater_install_probe", null) == "preserved")
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS signed self-update installed and existing app data preserved\n") })
            return
        }
        val monitor = addMonitor(UpdatesActivity::class.java.name, null, false)
        targetContext.startActivity(Intent(targetContext, UpdatesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val activity = waitForMonitorWithTimeout(monitor, 10_000) as UpdatesActivity
        removeMonitor(monitor)
        fun await(condition: () -> Boolean) {
            val deadline = System.nanoTime() + 90_000_000_000L
            var ready = false
            while (!ready && System.nanoTime() < deadline) {
                runOnMainSync { ready = condition() }; Thread.sleep(50)
            }
            check(ready) { "Updates scenario timed out: ${activity.status.text}" }
        }
        runOnMainSync {
            check(!activity.downloadButton.isEnabled)
            activity.checkButton.performClick()
        }
        await { activity.checkButton.isEnabled }
        runOnMainSync {
            check(!activity.downloadButton.isEnabled) // Development build is ahead of the public releases.
            check(activity.status.text.toString() == "You are up to date.")
        }
        val releases = runBlocking { GitHubUpdates().releases() }
        val published = releases.maxBy { it.version }
        val newer = published.copy(tag = "v99.0.0-alpha.1", notes = "A newer version changelog\n- Installer test")
        runOnMainSync {
            activity.showReleases(listOf(newer, newer.copy(tag="v98.0.0", notes="Earlier changes")))
            check(activity.downloadButton.isEnabled)
            check(activity.notes.text.contains("A newer version changelog"))
            check(activity.notes.text.contains("Earlier changes"))
        }
        // Actual public GitHub APK download; the newer version above only exercises UI selection.
        val downloaded = runBlocking {
            GitHubUpdates().download(published, File(activity.filesDir, "updates")) { percent -> check(percent in 0..100) }
        }
        check(downloaded.length() == published.size)
        val info = activity.packageManager.getPackageArchiveInfo(downloaded.path, 0)!!
        check(info.packageName == "com.couchjoust")
        check(info.versionName == published.tag.removePrefix("v"))
        val install = activity.installIntent(downloaded)
        check(install.data!!.scheme == "content")
        check(install.type == "application/vnd.android.package-archive")
        check(install.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        activity.contentResolver.openInputStream(install.data!!)!!.use { check(it.read() == 0x50) }
        sendStatus(0, Bundle().apply { putString("stream", "PASS GitHub check, current-version state, newer changelogs, APK download and scoped installer URI\n") })
        uiAutomation.takeScreenshot().let { image ->
            File(targetContext.getExternalFilesDir(null), "updates.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            image.recycle()
        }
        if (arguments.getString("updates") == "install") {
            val fixture = File(targetContext.getExternalFilesDir(null), "update-fixture.apk")
            fixture.copyTo(downloaded, overwrite = true)
            targetContext.getSharedPreferences("joust",0).edit().putString("updater_install_probe","preserved").commit()
            runOnMainSync { activity.downloadedApk = downloaded; activity.downloadButton.performClick() }
            if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
                Thread.sleep(1000)
                // The application opens the real per-app Settings activity. Grant its permission for this automated test.
                uiAutomation.executeShellCommand("appops set com.couchjoust REQUEST_INSTALL_PACKAGES allow").use { fd ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
                }
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            }
            sendStatus(0, Bundle().apply { putString("stream", "INSTALLER_READY: confirm the update; instrumentation ends when Android replaces this app\n") })
            Thread.sleep(90_000)
            error("Installer was not confirmed by the external device test")
        } else {
            runOnMainSync { activity.finish() }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS updates scenario\n") })
        }
    }
}
