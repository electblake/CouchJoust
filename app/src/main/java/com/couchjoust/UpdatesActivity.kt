package com.couchjoust

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class UpdatesActivity : ComponentActivity() {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private val repository = GitHubUpdates()
    private lateinit var current: TextView
    internal lateinit var status: TextView
        private set
    internal lateinit var notes: TextView
        private set
    internal lateinit var checkButton: Button
        private set
    internal lateinit var downloadButton: Button
        private set
    private lateinit var progress: ProgressBar
    private lateinit var installed: ReleaseVersion
    private var available: AppRelease? = null
    internal var downloadedApk: File? = null

    private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= 26 && packageManager.canRequestPackageInstalls()) openInstaller()
        else status.setText(R.string.updates_permission_cancelled)
    }
    private val installer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        showInstalledVersion()
        status.setText(R.string.updates_installer_closed)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
            setBackgroundColor(Color.rgb(12, 17, 26))
        }
        fun label(resource: Int, size: Float) = TextView(this).apply {
            setText(resource); textSize = size; setTextColor(Color.WHITE)
        }
        root.addView(label(R.string.updates_title, 26f))
        current = label(R.string.updates_title, 22f)
        root.addView(current)
        showInstalledVersion()
        root.addView(label(R.string.updates_source, 16f))
        root.addView(label(if (installed.prerelease.isEmpty()) R.string.updates_stable_channel else R.string.updates_preview_channel, 16f))
        val buttons = LinearLayout(this)
        checkButton = Button(this).apply {
            setText(R.string.updates_check)
            setOnClickListener { checkForUpdates() }
        }
        downloadButton = Button(this).apply {
            setText(R.string.updates_download); isEnabled = false
            setOnClickListener { if (downloadedApk == null) download() else requestInstall() }
        }
        buttons.addView(checkButton)
        buttons.addView(downloadButton)
        buttons.addView(Button(this).apply { setText(R.string.updates_back); setOnClickListener { finish() } })
        root.addView(buttons)
        status = label(R.string.updates_initial, 18f); root.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.GONE
        }
        root.addView(progress)
        root.addView(label(R.string.updates_notes, 20f))
        notes = TextView(this).apply { textSize = 18f; setTextColor(Color.WHITE); setPadding(0, 12, 0, 12) }
        val scroll = ScrollView(this).apply { isFocusable = true; addView(notes) }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        checkButton.requestFocus()
    }

    private fun showInstalledVersion() {
        val info = packageManager.getPackageInfo(packageName, 0)
        installed = ReleaseVersion.parse(info.versionName!!)
        current.text = getString(R.string.updates_current, info.versionName, PackageInfoCompat.getLongVersionCode(info))
    }

    private fun checkForUpdates() {
        checkButton.isEnabled = false
        downloadButton.isEnabled = false
        downloadedApk = null
        available = null
        notes.text = ""
        status.setText(R.string.updates_checking)
        scope.launch { showReleases(repository.releases()) }
    }

    internal fun showReleases(releases: List<AppRelease>) {
        val newer = GitHubUpdates.newer(releases, installed)
        checkButton.isEnabled = true
        downloadButton.setText(R.string.updates_download)
        downloadedApk = null
        if (newer.isEmpty()) {
            available = null
            downloadButton.isEnabled = false
            notes.text = ""
            status.setText(R.string.updates_current_status)
            checkButton.requestFocus()
        } else {
            available = newer.first()
            notes.text = newer.joinToString("\n\n────────────────────\n\n") { "${it.tag}\n\n${it.notes}" }
            status.text = getString(R.string.updates_available, available!!.tag, available!!.size / 1_000_000.0)
            downloadButton.isEnabled = true
            downloadButton.requestFocus()
        }
    }

    private fun download() {
        val release = available!!
        checkButton.isEnabled = false
        downloadButton.isEnabled = false
        progress.progress = 0; progress.visibility = View.VISIBLE
        status.text = getString(R.string.updates_downloading, release.tag, 0)
        scope.launch {
            downloadedApk = repository.download(release, File(filesDir, "updates")) { percent ->
                progress.progress = percent
                status.text = getString(R.string.updates_downloading, release.tag, percent)
            }
            progress.visibility = View.GONE
            status.setText(R.string.updates_ready)
            checkButton.isEnabled = true
            downloadButton.setText(R.string.updates_install)
            downloadButton.isEnabled = true
            downloadButton.requestFocus()
            requestInstall()
        }
    }

    internal fun requestInstall() {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            status.setText(R.string.updates_permission)
            permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
        } else openInstaller()
    }

    internal fun installIntent(file: File): Intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
        setDataAndType(FileProvider.getUriForFile(this@UpdatesActivity, "$packageName.updates", file), "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_RETURN_RESULT, true)
    }

    private fun openInstaller() {
        status.setText(R.string.updates_installer_open)
        installer.launch(installIntent(downloadedApk!!))
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
