# Shrink and optimise, but keep class and method names: the size win comes from dropping unused
# AndroidX code, not from renaming, and readable stack traces matter more in a sideloaded app
# where crash reports arrive as screenshots.
-dontobfuscate

# GeckoView uses native JNI entry points and reflection beyond references visible to R8.
# The former standalone game APK was not minified; retain its Java bridge intact.
-keep class org.mozilla.** { *; }
-keep class com.gentv.game.** { *; }

# Umeng's SDK loads components reflectively after the user's separate consent.
-keep class com.umeng.** { *; }
-keep class org.repackage.** { *; }

# GeckoView's optional WebAuthn path is excluded from this keyboard-only game.
-dontwarn com.google.android.gms.fido.**
-dontwarn com.google.android.gms.tasks.**
# SnakeYAML references java.beans for desktop-only introspection; Android has
# no java.beans runtime and the game does not use that code path.
-dontwarn java.beans.**
