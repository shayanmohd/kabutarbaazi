package com.kabutarbaazi.app.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Per-app language.
 *
 * AppCompatDelegate persists the choice itself (via its auto-registered metadata holder service),
 * so no DataStore key is needed. It only works because MainActivity extends AppCompatActivity:
 * below API 33 the backport applies the locale to AppCompat activities only.
 *
 * The tag is also mirrored onto profiles.locale, purely so notify-message can localise push
 * text server-side. Below API 33 the per-app locale never reaches a background service, so
 * localising a tray notification client-side would be wrong for exactly the Hindi and Urdu
 * users this app is built for.
 */
object AppLocale {

    val SUPPORTED = listOf(
        Language("en", "English", "English"),
        Language("hi", "हिंदी", "Hindi"),
        Language("ur", "اردو", "Urdu"),
    )

    data class Language(val tag: String, val native: String, val english: String)

    fun current(): String =
        AppCompatDelegate.getApplicationLocales()[0]?.language?.takeIf { it.isNotBlank() } ?: "en"

    /** Applying a locale recreates the activity. That is expected. */
    fun set(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
