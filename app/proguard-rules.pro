# Keep line numbers for readable diagnostics exports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose / Kotlin metadata is handled by the default AGP rules.
# Our engine is pure Kotlin with no reflection, so nothing extra is needed.
-dontwarn org.jetbrains.annotations.**
