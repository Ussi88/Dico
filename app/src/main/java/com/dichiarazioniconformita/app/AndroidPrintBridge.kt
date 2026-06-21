package com.dichiarazioniconformita.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Exposed to the page as `window.AndroidPrintBridge`. The web app normally prints a
 * declaration by opening a new browser tab with the fully-built document HTML and calling
 * window.print() on it — that combination (window.open + window.print on the popup) isn't
 * supported the same way inside a WebView. When this bridge is present, the page instead
 * hands the same ready-to-print HTML straight here, where it's loaded into a separate,
 * invisible WebView and sent to Android's native print dialog (which can target an actual
 * printer or "Save as PDF", exactly like the browser's print dialog would).
 */
class AndroidPrintBridge(private val context: Context) {

    @JavascriptInterface
    fun printHtml(html: String) {
        Handler(Looper.getMainLooper()).post {
            val printWebView = WebView(context)
            printWebView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val printManager =
                        context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                            ?: return
                    val jobName = "Dichiarazione di Conformità"
                    val adapter = view.createPrintDocumentAdapter(jobName)
                    printManager.print(jobName, adapter, PrintAttributes.Builder().build())
                }
            }
            printWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }
}
