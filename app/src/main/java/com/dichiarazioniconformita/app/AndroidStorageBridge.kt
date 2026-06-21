package com.dichiarazioniconformita.app

import android.content.Context
import android.webkit.JavascriptInterface
import java.io.File

class AndroidStorageBridge(context: Context) {

    private val dir: File = File(context.filesDir, "app_storage").apply {
        if (!exists()) mkdirs()
    }

    private fun fileFor(key: String): File {
        val safeName = key.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        return File(dir, "$safeName.json")
    }

    @JavascriptInterface
    fun getItem(key: String): String? {
        return try {
            val f = fileFor(key)
            if (f.exists()) f.readText(Charsets.UTF_8) else null
        } catch (e: Exception) {
            null
        }
    }

    @JavascriptInterface
    fun setItem(key: String, value: String): Boolean {
        return try {
            fileFor(key).writeText(value, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun removeItem(key: String): Boolean {
        return try {
            val f = fileFor(key)
            if (f.exists()) f.delete() else true
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun getTotalSizeBytes(): Long {
        return try {
            dir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
