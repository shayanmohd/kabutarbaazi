package com.kabutarbaazi.app.data

import com.kabutarbaazi.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

/**
 * The one Supabase client for the process.
 *
 * Only the anon key ever reaches the app. It is a public client key guarded by row-level
 * security, not a secret. The service_role key must never appear here or anywhere in the APK:
 * it bypasses RLS entirely and lives only in Edge Function secrets.
 */
object SupabaseFactory {

    val json = Json {
        ignoreUnknownKeys = true   // tolerate columns the app has not been taught about yet
        explicitNulls = false      // omit nulls so server defaults apply on insert
        coerceInputValues = true
    }

    fun create(): SupabaseClient {
        check(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "SUPABASE_URL is empty. Fill it in local.properties (see docs/BUILDING.md)."
        }
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            defaultSerializer = KotlinXSerializer(json)
            install(Auth) {
                // Sessions are long-lived on purpose: there is no password recovery in v1, so
                // signing a user out unnecessarily can lock them out permanently.
                autoLoadFromStorage = true
                alwaysAutoRefresh = true
            }
            install(Postgrest)
            install(Realtime)
            install(Functions)
        }
    }
}
