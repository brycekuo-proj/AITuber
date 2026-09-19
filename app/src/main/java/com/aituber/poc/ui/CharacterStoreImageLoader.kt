package com.aituber.poc.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

internal object CharacterStoreImageLoader {
    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(url: String, target: ImageView) {
        if (url.isBlank()) return
        target.tag = url
        cache.get(url)?.let { bitmap ->
            target.setImageBitmap(bitmap)
            return
        }

        executor.execute {
            val bitmap = runCatching { download(url) }.getOrNull() ?: return@execute
            cache.put(url, bitmap)
            mainHandler.post {
                if (target.tag == url) {
                    target.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun download(url: String): Bitmap? {
        if (!url.startsWith("https://")) return null
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AITuber/CharacterStore")
        }
        return try {
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use(BitmapFactory::decodeStream)
        } finally {
            connection.disconnect()
        }
    }
}
