# v1.0.0: added alongside minifyEnabled true on the release build type.

# OpenCV's Java classes are a JNI bridge to native .so code — the native
# side calls back into these classes/methods by exact name. R8 renaming
# or stripping anything here (which it otherwise would, since nothing in
# the Kotlin/Java code calls most of these methods directly — the native
# side does) would break scanning/grading with a native crash or
# UnsatisfiedLinkError, not a clean compile-time error. This is a
# well-known requirement for any app bundling OpenCV, not specific to
# how this app happens to use it.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# CameraX ships its own consumer ProGuard rules bundled in its AARs,
# which apply automatically — no extra rules needed here for it.

# This app hand-writes all JSON (TestStorage, ScannedSheetStorage,
# BackupDataActivity/RestoreDataActivity, etc.) rather than using
# reflection-based serialization (no Gson/Moshi/kotlinx.serialization) —
# so R8 renaming private fields on this app's own model/data classes is
# safe by construction; nothing reads them by reflected field name.
# Nothing else app-specific needed here for that reason.

# Keep enough exception/line-number info that a stack trace from a real
# device is still readable after shrinking, rather than a wall of a1/a2/a3.
-keepattributes SourceFile, LineNumberTable, Exceptions, Signature, *Annotation*
-renamesourcefileattribute SourceFile
