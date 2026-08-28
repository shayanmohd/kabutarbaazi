package com.kabutarbaazi.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Dynamic colour is deliberately not used. It would hand the accent over to the user's wallpaper
// and break the single-accent rule that holds the app together visually.
private val LightColors = lightColorScheme(
    primary = TerracottaL,
    onPrimary = OnTerracottaL,
    primaryContainer = TerracottaSoftL,
    onPrimaryContainer = OnTerracottaSoftL,
    secondary = TerracottaL,
    onSecondary = OnTerracottaL,
    secondaryContainer = SelectedTintL,
    onSecondaryContainer = OnSelectedTintL,
    tertiary = TerracottaL,
    onTertiary = OnTerracottaL,
    tertiaryContainer = SelectedTintL,
    onTertiaryContainer = OnSelectedTintL,
    surfaceContainerLowest = SurfaceHighL,
    surfaceContainerLow = SurfaceHighL,
    surfaceContainer = SurfaceLowL,
    surfaceContainerHigh = SurfaceLowL,
    surfaceContainerHighest = PaperSunk,
    background = Paper,
    onBackground = InkLight,
    surface = PaperRaised,
    onSurface = InkLight,
    surfaceVariant = PaperSunk,
    onSurfaceVariant = InkMutedL,
    outline = OutlineLight,
    outlineVariant = PaperSunk,
    error = ErrorL,
    onError = PaperRaised,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF5C1A15),
    scrim = ReelScrim,
)

private val DarkColors = darkColorScheme(
    primary = TerracottaD,
    onPrimary = OnTerracottaD,
    primaryContainer = TerracottaSoftD,
    onPrimaryContainer = OnTerracottaSoftD,
    secondary = TerracottaD,
    onSecondary = OnTerracottaD,
    secondaryContainer = SelectedTintD,
    onSecondaryContainer = OnSelectedTintD,
    tertiary = TerracottaD,
    onTertiary = OnTerracottaD,
    tertiaryContainer = SelectedTintD,
    onTertiaryContainer = OnSelectedTintD,
    surfaceContainerLowest = SurfaceLowD,
    surfaceContainerLow = SurfaceLowD,
    surfaceContainer = SurfaceHighD,
    surfaceContainerHigh = SurfaceHighD,
    surfaceContainerHighest = SlateSunk,
    background = Slate,
    onBackground = InkDark,
    surface = SlateRaised,
    onSurface = InkDark,
    surfaceVariant = SlateSunk,
    onSurfaceVariant = InkMutedD,
    outline = OutlineDark,
    outlineVariant = SlateSunk,
    error = ErrorD,
    onError = Slate,
    errorContainer = Color(0xFF3B1512),
    onErrorContainer = Color(0xFFF9DEDC),
    scrim = ReelScrim,
)

/**
 * True when the device has animations turned off (accessibility, or a battery saver on a cheap
 * phone). Every animation in the app collapses to instant when this is set. Android's equivalent
 * of prefers-reduced-motion.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Semantic success colour, which Material's scheme has no slot for. */
val LocalSuccessColor = staticCompositionLocalOf { SuccessL }

@Composable
fun KabutarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    // Urdu is the only bundled locale that needs the Nastaliq family. Resolving from the
    // configuration means the in-app language switch changes type as well as strings.
    val script = remember(configuration) {
        val lang = configuration.locales[0]?.language
        if (lang == "ur") AppScript.Nastaliq else AppScript.LatinDevanagari
    }

    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    CompositionLocalProvider(
        LocalReducedMotion provides reducedMotion,
        LocalSuccessColor provides if (darkTheme) SuccessD else SuccessL,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = kabutarTypography(script),
            shapes = KabutarShapes,
            content = content,
        )
    }
}
