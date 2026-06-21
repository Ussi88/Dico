package com.dichiarazioniconformita.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/**
 * Exposed to the page as `window.AndroidDownloadBridge`. The web app already builds every
 * exported file (backups, single declaration/draft exports, the generated Word document) as
 * a Blob in JS and normally triggers a browser download via a hidden `<a download>` click —
 * that trick is unreliable inside a WebView for blob: URLs, so when this bridge is present
 * the page instead base64-encodes the blob and hands it here, where it's written straight to
 * the device's Downloads folder using MediaStore (or legacy File APIs on very old Android
 * versions). See `_nativeSaveBlob()` in index.html for the JS side of this.
 */
class AndroidDownloadBridge(private val context: Context) {

    private val subfolder = "App Dichiarazioni di Conformità"

    @JavascriptInterface
    fun saveFile(base64DataUrl: String, filename: String, mimeType: String) {
        try {
            val base64Payload =
                if (base64DataUrl.contains(",")) base64DataUrl.substringAfter(",") else base64DataUrl
            val bytes = Base64.decode(base64Payload, Base64.DEFAULT)
            val safeName = sanitizeFilename(filename)
            val resolvedMime = mimeType.ifBlank { "application/octet-stream" }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(bytes, safeName, resolvedMime)
            } else {
                saveViaLegacyFile(bytes, safeName)
            }
        } catch (e: Exception) {
            toast("Errore nel salvataggio del file: ${e.message}")
        }
    }

    private fun saveViaMediaStore(bytes: ByteArray, filename: String, mimeType: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subfolder")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            toast("Impossibile salvare \"$filename\"")
            return
        }
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        toast("Salvato in Download/$subfolder/$filename")
    }

    @Suppress("DEPRECATION")
    private fun saveViaLegacyFile(bytes: ByteArray, filename: String) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            subfolder
        )
        if (!dir.exists()) dir.mkdirs()
        val outFile = File(dir, filename)
        FileOutputStream(outFile).use { it.write(bytes) }
        toast("Salvato in ${outFile.path}")
    }

    private fun sanitizeFilename(name: String): String {
        val trimmed = name.trim().ifBlank { "file_${System.currentTimeMillis()}" }
        return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun toast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
