package com.stellarlogbook.app.data.update

import android.content.Context
import com.stellarlogbook.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Self-update via GitHub Releases. The app checks the latest release for a
 * `version.json` asset, compares versionCode, downloads the APK into cache,
 * and hands it to the system installer (user confirms).
 */
class UpdateManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {

    private val appContext = context.applicationContext
    private val updatesDir: File get() = File(appContext.cacheDir, "updates").apply { mkdirs() }

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkAsset: String,
        val downloadUrl: String,
        val notes: String?
    )

    data class Progress(val doneBytes: Long, val totalBytes: Long)

    /** Returns update info when a newer version than the installed app exists. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val release = fetchLatestRelease() ?: return@withContext null
            val meta = release.assets.firstOrNull { it.name == "version.json" }
                ?: return@withContext null // older releases have no version.json
            val versionUrl = meta.browserDownloadUrl
                ?: return@withContext null
            val versionJson = fetchText(versionUrl)
            val version = json.decodeFromString<VersionJson>(versionJson)
            val apk = release.assets.firstOrNull { it.name == version.apkAsset }
                ?: return@withContext null
            val url = apk.browserDownloadUrl ?: return@withContext null
            if (version.versionCode <= BuildConfig.VERSION_CODE) return@withContext null
            UpdateInfo(version.versionCode, version.versionName, version.apkAsset, url, release.body)
        } catch (_: Exception) {
            null
        }
    }

    /** Downloads the APK into cacheDir/updates and returns the local file. */
    suspend fun download(info: UpdateInfo, onProgress: (Progress) -> Unit = {}): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val target = File(updatesDir, info.apkAsset)
                val request = Request.Builder().url(info.downloadUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException("HTTP ${response.code}"))
                    }
                    val body = response.body
                        ?: return@withContext Result.failure(IllegalStateException("Empty response"))
                    val total = body.contentLength()
                    val tmp = File(updatesDir, info.apkAsset + ".part")
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                if (done % (2 shl 20) == 0L) onProgress(Progress(done, total))
                            }
                        }
                    }
                    tmp.renameTo(target)
                    onProgress(Progress(target.length(), total))
                }
                Result.success(target)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun fetchLatestRelease(): ReleaseJson? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/ByzantineEmpress/StellarLogbook/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "StellarLogbook/${BuildConfig.VERSION_NAME}")
            .build()
        val text = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string() ?: return null
        }
        return json.decodeFromString<ReleaseJson>(text)
    }

    private fun fetchText(url: String): String {
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
            response.body?.string() ?: throw IllegalStateException("Empty response")
        }
    }

    @Serializable
    private data class VersionJson(
        val versionCode: Int,
        val versionName: String,
        val apkAsset: String
    )

    @Serializable
    private data class ReleaseJson(
        @SerialName("tag_name") val tagName: String? = null,
        val body: String? = null,
        val assets: List<AssetJson> = emptyList()
    )

    @Serializable
    private data class AssetJson(
        val name: String? = null,
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null
    )
}
