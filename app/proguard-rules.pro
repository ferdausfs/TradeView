# Keep the JS bridge entry points reachable from WebView JavaScript.
-keepclassmembers class com.ferdausfs.tradeview.bridge.** {
    @android.webkit.JavascriptInterface <methods>;
}
