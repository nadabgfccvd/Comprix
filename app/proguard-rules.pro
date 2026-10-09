# Regras R8/ProGuard do Comprix (build de release).

# Room: mantem as implementacoes geradas.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ML Kit (OCR + codigo de barras) - modelos bundled.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Compose
-dontwarn androidx.compose.**

# Modelos de dominio usados em backup/restauracao por nome de campo.
-keep class br.com.comprix.domain.modelo.** { *; }
