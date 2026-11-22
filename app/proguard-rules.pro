# MovieVerse release rules.
# Shrinking + optimisation stay ON (that is what makes the app fast); names are NOT scrambled, so error logs stay readable.
-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
# optional classes that jsoup / okhttp / coil mention but the app does not use
-dontwarn **

# the Trending / Library / Releases grids (WebView): the page calls these methods by name, so they must stay
-keepattributes JavascriptInterface
-keepclassmembers class com.trackerxx.movieverse.ui.library.GridBridge {
    @android.webkit.JavascriptInterface <methods>;
}
