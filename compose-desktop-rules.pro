-keep class com.sun.jna.** { *; }
-keep interface com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-keepclassmembers class * implements com.sun.jna.** { *; }
-keep class uk.co.caprica.vlcj.** { *; }
-keep class io.github.miuzarte.fhradio.** { *; }
-keep class top.yukonga.miuix.** { *; }

-dontnote "module-info"
-dontnote "META-INF**"

-dontwarn com.sun.jna.**
-dontwarn uk.co.caprica.vlcj.**
-dontwarn java.lang.invoke.**
-dontwarn kotlinx.coroutines.debug.**

-dontobfuscate
