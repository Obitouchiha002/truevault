# ==================================================================================================
# TrueVault release keep rules
#
# Everything here exists for a concrete reason. Nothing is a blanket "-keep class **".
# ==================================================================================================

# --- Crash readability ----------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Strip logging from release builds ------------------------------------------------------------
# TrueVault routes all logging through SecureLog, which is a no-op in release. These rules remove any
# stray android.util.Log call that a dependency may still make on our behalf.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# --- kotlinx.serialization ------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class ** {
    public static ** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static kotlinx.serialization.KSerializer serializer(...);
}

# --- Room -----------------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger --------------------------------------------------------------------------------
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep class dagger.hilt.internal.aggregatedroot.codegen.** { *; }

# --- BouncyCastle ---------------------------------------------------------------------------------
# Only the low-level Argon2id/digest classes are used; the JCE provider registration is not.
-dontwarn javax.naming.**
-dontwarn org.bouncycastle.jsse.**
-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters$Builder { *; }

# --- Compose --------------------------------------------------------------------------------------
-dontwarn androidx.compose.**
