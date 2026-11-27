# Shrink and optimise without renaming the launcher's classes and methods. The game plugin
# compiles separately against this interface, and readable stack traces aid sideloaded TVs.
-dontobfuscate
# Preserve the host/plugin binary contract even when no implementation exists in the host APK.
-keep interface com.gentv.tv.GameRuntimeEntry { *; }

# Umeng's SDK loads components reflectively after the user's separate consent.
-keep class com.umeng.** { *; }
-keep class org.repackage.** { *; }

# GeckoView's optional WebAuthn path is excluded from this keyboard-only game.
-dontwarn com.google.android.gms.fido.**
-dontwarn com.google.android.gms.tasks.**
# SnakeYAML references java.beans for desktop-only introspection; Android has
# no java.beans runtime and the game does not use that code path.
-dontwarn java.beans.**
-keep public class com.gentv.tv.R$* { public static final int *; }
