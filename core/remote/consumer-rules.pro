# kotlinx-serialization needs the generated serializers of the response models kept: R8 sees
# nothing calling them, because they are reached reflectively from the @Serializable companion.
-keepclassmembers class com.truevault.core.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.truevault.core.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}
