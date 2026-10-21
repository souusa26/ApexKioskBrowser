# Add project specific ProGuard rules here.
# Keep Javascript interface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
