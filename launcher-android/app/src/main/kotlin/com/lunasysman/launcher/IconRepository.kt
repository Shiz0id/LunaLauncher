package com.lunasysman.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.lunasysman.launcher.apps.android.AndroidAppScanner
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class IconRepository(
    private val context: Context,
    private val scanner: AndroidAppScanner,
) {
    private val diskDir: File by lazy {
        File(context.filesDir, "icons").also { it.mkdirs() }
    }

    private val memoryCache: LruCache<String, Bitmap> =
        object : LruCache<String, Bitmap>(maxMemoryCacheKb()) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }

    suspend fun icon(iconKey: String): ImageBitmap? {
        val cached = memoryCache.get(iconKey)
        if (cached != null) return cached.asImageBitmap()

        val file = diskFileForKey(iconKey)
        val fromDisk =
            withContext(Dispatchers.IO) {
                if (!file.exists()) return@withContext null
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) file.setLastModified(System.currentTimeMillis())
                bmp
            }

        if (fromDisk != null) {
            memoryCache.put(iconKey, fromDisk)
            return fromDisk.asImageBitmap()
        }

        val loaded =
            withContext(Dispatchers.IO) {
                scanner.loadIconBitmapOrNull(iconKey)
            } ?: return null

        memoryCache.put(iconKey, loaded)
        withContext(Dispatchers.IO) {
            writePngAtomically(file, loaded)
            evictDiskIfNeeded(maxBytes = 64L * 1024L * 1024L, maxFiles = 1200)
        }
        return loaded.asImageBitmap()
    }

    suspend fun prefetch(iconKeys: List<String>, maxCount: Int = 32) {
        val keys = iconKeys.asSequence().distinct().take(maxCount).toList()
        for (key in keys) {
            try {
                icon(key)
            } catch (e: Exception) {
                Log.w("LunaLauncher", "Icon prefetch failed for $key", e)
            }
            delay(8)
        }
    }

    fun clearMemory() {
        memoryCache.evictAll()
    }

    private fun diskFileForKey(iconKey: String): File =
        File(diskDir, sha256Hex(iconKey) + ".png")

    private fun evictDiskIfNeeded(maxBytes: Long, maxFiles: Int) {
        val files = diskDir.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= maxFiles && files.sumOf { it.length() } <= maxBytes) return

        val sortedOldestFirst = files.sortedBy { it.lastModified() }
        var sizeBytes = files.sumOf { it.length() }
        var count = files.size

        for (f in sortedOldestFirst) {
            if (count <= maxFiles && sizeBytes <= maxBytes) break
            val len = f.length()
            if (f.delete()) {
                count -= 1
                sizeBytes -= len
            }
        }
    }

    private fun maxMemoryCacheKb(): Int {
        val maxMemory = Runtime.getRuntime().maxMemory().coerceAtLeast(64L * 1024L * 1024L)
        val targetBytes = (maxMemory / 16L).coerceIn(24L * 1024L * 1024L, 64L * 1024L * 1024L)
        return (targetBytes / 1024L).toInt()
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return buildString(digest.size * 2) {
            for (b in digest) append("%02x".format(b))
        }
    }

    private fun writePngAtomically(target: File, bitmap: Bitmap) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (!tmp.renameTo(target)) {
            tmp.delete()
        }
    }
}

