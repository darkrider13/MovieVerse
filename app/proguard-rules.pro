# MovieVerse release rules.
# Shrinking + optimisation stay ON (that is what makes the app fast); names are NOT scrambled, so error logs stay readable.
-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
# optional classes that jsoup / okhttp / coil mention but the app does not use
-dontwarn **
