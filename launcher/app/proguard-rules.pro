# Shrink and optimise, but keep class and method names: the size win comes from dropping unused
# AndroidX code, not from renaming, and readable stack traces matter more in a sideloaded app
# where crash reports arrive as screenshots.
-dontobfuscate

# Umeng U-App loads parts of its SDK reflectively after the user's separate consent.
-keep class com.umeng.** { *; }
-keep class org.repackage.** { *; }
-keep public class com.gentv.tv.R$* { public static final int *; }
