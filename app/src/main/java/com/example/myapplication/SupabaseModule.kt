package com.example.myapplication
import android.util.Log
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import com.example.myapplication.BuildConfig

private const val TAG_SB = "SupabaseModule"

object SupabaseModule {

    val client: io.github.jan.supabase.SupabaseClient

    init {
        val url = BuildConfig.SUPABASE_URL
        val keyPresent = BuildConfig.SUPABASE_KEY.isNotBlank()
        Log.d(TAG_SB, "init — url='$url' key_present=$keyPresent")
        if (url.isBlank()) {
            Log.e(TAG_SB, "SUPABASE_URL is empty! Check local.properties and buildConfigField in build.gradle.kts")
        }
        if (!keyPresent) {
            Log.e(TAG_SB, "SUPABASE_KEY is empty! Check local.properties and buildConfigField in build.gradle.kts")
        }
        client = try {
            createSupabaseClient(
                supabaseUrl = url,
                supabaseKey = BuildConfig.SUPABASE_KEY
            ) {
                install(Postgrest)
            }.also { Log.d(TAG_SB, "createSupabaseClient succeeded") }
        } catch (e: Exception) {
            Log.e(TAG_SB, "createSupabaseClient FAILED: ${e::class.simpleName} — ${e.message}", e)
            throw e
        }
    }
}