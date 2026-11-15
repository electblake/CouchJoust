package com.couchjoust

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class AppRelease(val tag: String, val notes: String, val apkUrl: String, val size: Long, val isPrerelease: Boolean) {
    val version = ReleaseVersion.parse(tag)
}

/** Public repository only. No credentials, background checks, alternate feeds or retries. */
class GitHubUpdates {
    suspend fun releases(): List<AppRelease> = withContext(Dispatchers.IO) {
        val result = mutableListOf<AppRelease>()
        var page = 1
        do {
            val connection = connection("https://api.github.com/repos/electblake/CouchJoust/releases?per_page=100&page=$page")
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val entries = JSONArray(json)
            result += parseReleases(entries)
            page++
        } while (entries.length() == 100)
        result
    }

    suspend fun download(release: AppRelease, directory: File, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val file = File(directory, "update.apk")
        val connection = connection(release.apkUrl)
        var received = 0L
        var lastPercent = -1
        connection.inputStream.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var count = input.read(buffer)
                while (count != -1) {
                    coroutineContext.ensureActive()
                    output.write(buffer, 0, count)
                    received += count
                    val percent = (received * 100 / release.size).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        withContext(Dispatchers.Main) { progress(percent) }
                    }
                    count = input.read(buffer)
                }
            }
        }
        connection.disconnect()
        file
    }

    private fun connection(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        setRequestProperty("User-Agent", "CouchJoust-Android-Updater")
        connectTimeout = 15_000
        readTimeout = 30_000
    }

    companion object {
        fun parseReleases(entries: JSONArray): List<AppRelease> = (0 until entries.length()).map { entries.getJSONObject(it) }
            .filter { !it.getBoolean("draft") }.map { release ->
                val tag = release.getString("tag_name")
                val assets = release.getJSONArray("assets")
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .single { it.getString("name") == "CouchJoust-${tag.removePrefix("v")}.apk" }
                AppRelease(tag, release.getString("body"), apk.getString("browser_download_url"), apk.getLong("size"), release.getBoolean("prerelease"))
            }

        fun newer(releases: List<AppRelease>, installed: ReleaseVersion): List<AppRelease> = releases
            .filter { it.version > installed && (installed.prerelease.isNotEmpty() || (!it.isPrerelease && it.version.prerelease.isEmpty())) }
            .sortedByDescending { it.version }
    }
}
