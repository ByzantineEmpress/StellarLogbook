package com.stellarlogbook.app.data.pack

import android.content.Context
import java.io.File

/**
 * Locates the optional Uncharted Pack in app-specific storage.
 * Download UI lands with the Explore tab; the pack is optional — everything
 * else in the app works without it.
 */
class PackManager(context: Context) {

    private val appContext = context.applicationContext
    private val dir: File get() = File(appContext.filesDir, "pack")

    fun isInstalled(): Boolean = packFile().exists()

    fun packFile(): File = File(dir, PACK_NAME).apply { parentFile?.mkdirs() }

    /** All installed slice files, sorted by name (s0..sN). */
    fun installedSlices(): List<File> =
        dir.listFiles { f -> f.name.startsWith("stellar-pack-s") && f.name.endsWith(".bin") }
            ?.sortedBy { it.name }
            ?: emptyList()

    fun open(): GalaxyPack? = packFile().takeIf { it.exists() }?.let { GalaxyPack(it) }

    companion object {
        const val PACK_NAME = "stellar-pack.bin"
    }
}
