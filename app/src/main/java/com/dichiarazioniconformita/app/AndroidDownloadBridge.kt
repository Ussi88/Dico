package com.dichiarazioniconformita.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

class AndroidDownloadBridge(
    private val context: Context,
    private val onChooseFolderRequested: () -> Unit
) {

    private val subfolder = "App Dichiarazioni di Conformità"
    private val prefs by lazy { context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE) }

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

    @JavascriptInterface
    fun chooseBackupFolder() {
        Handler(Looper.getMainLooper()).post { onChooseFolderRequested() }
    }

    @JavascriptInterface
    fun getCustomBackupFolderName(): String? {
        val uriString = prefs.getString(PREF_FOLDER_URI, null) ?: return null
        return try {
            DocumentFile.fromTreeUri(context, Uri.parse(uriString))?.name
        } catch (e: Exception) {
            null
        }
    }

    @JavascriptInterface
    fun saveToCustomFolder(base64DataUrl: String, filename: String, mimeType: String): Boolean {
        val uriString = prefs.getString(PREF_FOLDER_URI, null) ?: return false
        return try {
            val dir = DocumentFile.fromTreeUri(context, Uri.parse(uriString)) ?: return false
            val safeName = sanitizeFilename(filename)
            dir.findFile(safeName)?.delete()
            val newFile = dir.createFile(mimeType.ifBlank { "application/json" }, safeName)
                ?: return false
            val base64Payload =
                if (base64DataUrl.contains(",")) base64DataUrl.substringAfter(",") else base64DataUrl
            val bytes = Base64.decode(base64Payload, Base64.DEFAULT)
            context.contentResolver.openOutputStream(newFile.uri)?.use { it.write(bytes) }
                ?: return false
            true
        } catch (e: Exception) {
            false
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

    companion object {
        const val PREF_FOLDER_URI = "backup_folder_uri"
    }
}
