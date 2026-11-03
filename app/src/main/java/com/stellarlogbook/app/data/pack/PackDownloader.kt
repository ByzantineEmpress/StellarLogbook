package com.stellarlogbook.app.data.pack

import android.content.Context
import com.stellarlogbook.app.data.remote.PackManifestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Downloads the Uncharted Pack (manifest + slices) from a GitHub Release.
 * The phone only fetches the slice whose x-range contains the current position
 * (~50-90 MB); Wi-Fi-only enforcement happens in the UI.
 */
class PackDownloader(
    context: Context,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {

    private val appContext = context.applicationContext
    private val dir: File get() = File(appContext.filesDir, "pack")

    data class Progress(val doneBytes: Long, val totalBytes: Long, val currentFile: String)

    suspend fun downloadSliceFor(x: Float, onProgress: (Progress) -> Unit = {}): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val manifest = fetchManifest()
                val slice = manifest.slices.firstOrNull { s ->
                    val minX = s.minX ?: return@firstOrNull false
                    val maxX = s.maxX ?: return@firstOrNull false
                    x >= minX && x <= maxX
                } ?: return@withContext Result.failure(IllegalStateException("No pack slice covers this region"))
                val file = slice.file ?: return@withContext Result.failure(IllegalStateException("Manifest slice missing file name"))

                val target = File(dir, file).apply { parentFile?.mkdirs() }
                val url = baseUrl + file
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException("HTTP ${response.code} downloading pack"))
                    }
                    val body = response.body
                        ?: return@withContext Result.failure(IllegalStateException("Empty response"))
                    val total = body.contentLength()
                    val tmp = File(dir, "$file.part")
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                if (done % (2 shl 20) == 0L) onProgress(Progress(done, total, file))
                            }
                        }
                    }
                    tmp.renameTo(target)
                    onProgress(Progress(target.length(), total, file))
                }
                // keep the manifest for version checks
                File(dir, "stellar-pack.manifest.json")
                    .writeText(json.encodeToString(PackManifestDto.serializer(), manifest))
                Result.success(target)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun fetchManifest(): PackManifestDto = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl + "stellar-pack.manifest.json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} fetching manifest")
            val text = response.body?.string() ?: throw IllegalStateException("Empty manifest")
            json.decodeFromString(PackManifestDto.serializer(), text)
        }
    }

    companion object {
        /** Pack assets live on the GitHub Release tagged pack-v2026.11.03. */
        const val DEFAULT_BASE_URL =
            "https://github.com/ByzantineEmpress/StellarLogbook/releases/download/pack-v2026.11.03/"
    }
}
