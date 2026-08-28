package com.kabutarbaazi.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.kabutarbaazi.app.R

// Anek (Ek Type, OFL 1.1) carries both Latin and Devanagari in one design, so English and Hindi
// share a single voice instead of being two fonts bolted together. It is deliberately not Baloo,
// which is the obvious Indian-app pick, and not Inter.
//
// The bundled file is instanced to wdth=100: the width axis is never used, and pinning it cut
// the file from 2.1 MB to 0.9 MB. The weight axis stays variable, which is why every weight
// below is expressed as a FontVariation rather than a separate font file.

@OptIn(ExperimentalTextApi::class)
private fun anek(weight: Int) = Font(
    R.font.anek,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun nastaliq(weight: Int) = Font(
    R.font.noto_nastaliq_urdu,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val AnekFamily = FontFamily(
    anek(400), anek(500), anek(600), anek(700),
)

// Noto Nastaliq Urdu only ships 400-700.
val NastaliqFamily = FontFamily(
    nastaliq(400), nastaliq(500), nastaliq(600), nastaliq(700),
)

/**
 * Nastaliq is calligraphic: glyphs cascade diagonally and need far more vertical room than
 * Devanagari or Latin. Setting it at the same line height as the other scripts clips descenders
 * and makes it unreadable. Urdu therefore gets a 1.9x line height and a raised minimum size,
 * and lists are built to accommodate taller rows rather than fighting them.
 */
enum class AppScript { LatinDevanagari, Nastaliq }

private fun LineHeightStyle.Companion.standard() = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

fun kabutarTypography(script: AppScript): Typography {
    val family = if (script == AppScript.Nastaliq) NastaliqFamily else AnekFamily
    // Urdu: bump every size by 2sp and stretch line height to 1.9x.
    val bump = if (script == AppScript.Nastaliq) 2 else 0
    val lh = if (script == AppScript.Nastaliq) 1.9f else 1.35f
    val lhs = LineHeightStyle.standard()

    fun style(size: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = (size + bump).sp,
        lineHeight = ((size + bump) * lh).sp,
        letterSpacing = tracking.sp,
        lineHeightStyle = lhs,
    )

    return Typography(
        // Hierarchy comes from weight and colour, not from screaming scale. 32sp is the ceiling.
        displaySmall = style(32, FontWeight.SemiBold, -0.5f),
        headlineMedium = style(24, FontWeight.SemiBold, -0.25f),
        headlineSmall = style(20, FontWeight.SemiBold),
        titleLarge = style(20, FontWeight.Medium),
        titleMedium = style(16, FontWeight.Medium),
        titleSmall = style(14, FontWeight.Medium),
        bodyLarge = style(16, FontWeight.Normal),
        bodyMedium = style(14, FontWeight.Normal),
        bodySmall = style(12, FontWeight.Normal),
        labelLarge = style(14, FontWeight.Medium),
        labelMedium = style(12, FontWeight.Medium),
        labelSmall = style(11, FontWeight.Medium),
    )
}

/**
 * Prices sit in lists where digits must line up column to column, so they use tabular figures.
 * Digits stay Latin in every locale: Hindi and Urdu speakers in India and Pakistan read prices
 * in Latin numerals, and Devanagari or Eastern Arabic digits here would read as a bug.
 */
@OptIn(ExperimentalTextApi::class)
val PriceStyle = TextStyle(
    fontFamily = AnekFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    fontFeatureSettings = "tnum",
)
