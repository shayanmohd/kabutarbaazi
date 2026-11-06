# R8 rules for the KabutarBaazi release build (full mode, AGP 8.10).
#
# Keep line numbers so release crash traces map back to source through mapping.txt, which AGP
# packs into the AAB for Play's deobfuscation.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Everything else this app needs ships inside the libraries and is merged automatically; check
# app/build/outputs/mapping/release/configuration.txt before adding a duplicate here.
#
# - kotlinx-serialization-core (META-INF/com.android.tools/r8/*.pro): keeps the Companion field
#   and serializer() of every @Serializable class, including the full-mode rule. supabase-kt
#   decodes through serializer(typeOf<T>()), which finds Companion.serializer() by reflection,
#   so these rules are what keep every Supabase row, RPC result and Edge Function body working.
#   That includes the private row classes nested in the repositories (FollowRow, PresignRequest).
# - ktor-utils (META-INF/proguard/ktor.pro): keeps the OkHttp engine container that supabase-kt's
#   engine-less HttpClient() finds through ServiceLoader, plus Ktor's atomicfu fields.
# - okhttp, kotlinx-datetime, kotlinx-coroutines, kotlin-reflect: their own bundled rules.
# - media3, coil, firebase, androidx: their own consumer rules.
# - supabase-kt ships no rules and needs none: it does no reflection of its own.
#
# App code needs nothing extra: ViewModels come from viewModelFactory initializers, routes are
# plain strings, and serial names are compile-time constants, so renaming a class or field
# never changes what goes over the wire or what is already stored on the device.
