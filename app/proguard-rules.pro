# Add project specific ProGuard rules here.
# By default minifyEnabled is false for both build types in app/build.gradle, so these
# rules aren't currently applied — kept here ready for if/when you turn minification on
# for a real release build.

# Keep the JavaScript interface classes and their @JavascriptInterface methods: WebView
# calls into them by reflection, so an obfuscated/stripped build would otherwise break
# the file-download and print bridges silently.
-keepclassmembers class com.dichiarazioniconformita.app.AndroidDownloadBridge {
    public *;
}
-keepclassmembers class com.dichiarazioniconformita.app.AndroidPrintBridge {
    public *;
}
