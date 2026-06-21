package com.dichiarazioniconformita.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Single-Activity wrapper around the "Dichiarazioni di Conformità" web app, bundled as a
 * static HTML/CSS/JS file at assets/index.html. Everything the app does — forms, the floor
 * plan editor, PDF handling, local data storage — runs inside the WebView exactly as it
 * does in a normal mobile browser. This class only adds the bits a plain browser tab can't
 * provide on its own: a stable, app-scoped place for localStorage to live (so saved data
 * survives between app launches, no matter how the file was opened), a working file picker
 * for attachment/photo uploads, and bridges for reliable file downloads and printing (see
 * AndroidDownloadBridge / AndroidPrintBridge).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        var resultUris: Array<Uri>? = null
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            when {
                data?.clipData != null -> {
                    val clip = data.clipData!!
                    resultUris = Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                }
                data?.dataString != null -> {
                    resultUris = arrayOf(Uri.parse(data.dataString))
                }
                pendingCameraUri != null -> {
                    resultUris = arrayOf(pendingCameraUri as Uri)
                }
            }
        }
        pendingFileCallback?.onReceiveValue(resultUris)
        pendingFileCallback = null
        pendingCameraUri = null
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Nothing to do either way: the chooser is shown again without/with the camera
           option next time onShowFileChooser fires. */ }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(webView)

        configureWebView(webView)

        webView.addJavascriptInterface(AndroidDownloadBridge(this), "AndroidDownloadBridge")
        webView.addJavascriptInterface(AndroidPrintBridge(this), "AndroidPrintBridge")

        webView.loadUrl("file:///android_asset/index.html")

        // Let the in-app "back" navigation (e.g. closing a form, going back a screen in the
        // app's own router) take priority over closing the whole Activity.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(webView: WebView) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        // This is the key setting for this app: it's what makes localStorage (where every
        // company/client/declaration/draft/floor-plan is saved) persist reliably between
        // launches, scoped to this app's own storage rather than to whatever exact file
        // path a browser happened to open.
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.setSupportZoom(false)

        webView.webViewClient = object : WebViewClient() {
            // Keep all navigation inside this single WebView — the app is a one-page,
            // hash/state-router style SPA, it never needs to leave file:///android_asset/.
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = filePathCallback

                val acceptTypes = fileChooserParams.acceptTypes
                    ?.filter { it.isNotBlank() }
                    ?.toTypedArray()

                val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    if (!acceptTypes.isNullOrEmpty()) {
                        putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes)
                    }
                    putExtra(
                        Intent.EXTRA_ALLOW_MULTIPLE,
                        fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                    )
                }

                val initialIntents = mutableListOf<Intent>()
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    createCameraCaptureIntent()?.let { initialIntents.add(it) }
                } else {
                    // Ask for next time; this picker still works fine without it (no
                    // camera entry, gallery/file picking is unaffected).
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }

                val chooser = Intent.createChooser(contentIntent, "Scegli file").apply {
                    if (initialIntents.isNotEmpty()) {
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents.toTypedArray())
                    }
                }

                return try {
                    fileChooserLauncher.launch(chooser)
                    true
                } catch (e: Exception) {
                    pendingFileCallback = null
                    false
                }
            }
        }
    }

    private fun createCameraCaptureIntent(): Intent? {
        return try {
            val photoFile = File.createTempFile("capture_", ".jpg", cacheDir)
            val photoUri = FileProvider.getUriForFile(
                this, "$packageName.fileprovider", photoFile
            )
            pendingCameraUri = photoUri
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        } catch (e: Exception) {
            null
        }
    }
}
