# ---- General ----
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-renamesourcefileattribute SourceFile

# ---- Kotlin metadata ----
-keep class kotlin.Metadata { *; }

# ---- PDFBox for Android (com.tom_roush.*) ----
-dontwarn com.tom_roush.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.apache.pdfbox.** { *; }

# ---- android-pdf-viewer (mhiew fork) ----
-keep class com.github.mhiew.** { *; }
-dontwarn com.github.mhiew.**

# ---- CameraX ----
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ---- Biometric ----
-keep class androidx.biometric.** { *; }

# ---- App's own data classes used by JSON serialization ----
-keep class com.example.pdfmaker.PdfCatalog$* { *; }
-keep class com.example.pdfmaker.DocSpec { *; }
-keep class com.example.pdfmaker.Template { *; }
-keep class com.example.pdfmaker.FillMemory$* { *; }
-keep class com.example.pdfmaker.UsageStats$Entry { *; }
-keep class com.example.pdfmaker.PdfFactory$* { *; }

# ---- Parcelable creators ----
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
