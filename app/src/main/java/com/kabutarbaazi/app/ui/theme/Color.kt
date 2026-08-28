package com.kabutarbaazi.app.ui.theme

import androidx.compose.ui.graphics.Color

// Palette: cool slate base with a single terracotta accent.
//
// Deliberately NOT the saffron / maroon / gold combination that every "Indian app" reaches for,
// and not the purple-glow-on-slate default either. The colours are taken from what a kabutarbaaz
// actually looks at: pigeon plumage grey, rooftop terracotta, the rust bar on a wing.
//
// One accent, used identically on every screen. No pure black, no pure white, so surfaces keep
// depth in both modes.

// Neutrals, light
val Paper        = Color(0xFFF6F7F8)
val PaperRaised  = Color(0xFFFFFFFF)
val PaperSunk    = Color(0xFFE9ECEF)
val InkLight     = Color(0xFF16191D)
val InkMutedL    = Color(0xFF5A626B)
val OutlineLight = Color(0xFFD2D7DC)

// Neutrals, dark
val Slate        = Color(0xFF101418)
val SlateRaised  = Color(0xFF181D22)
val SlateSunk    = Color(0xFF232A31)
val InkDark      = Color(0xFFECEFF2)
val InkMutedD    = Color(0xFF98A1AA)
val OutlineDark  = Color(0xFF39424B)

// Accent: terracotta. The only accent in the app.
val TerracottaL      = Color(0xFFC0552F)
val TerracottaD      = Color(0xFFE4703F)
val OnTerracottaL    = Color(0xFFFFFFFF)
val OnTerracottaD    = Color(0xFF221008)
val TerracottaSoftL  = Color(0xFFF6E3DB)
val TerracottaSoftD  = Color(0xFF3A2018)

// Semantic. Used only for state, never decoration.
val SuccessL = Color(0xFF2E7D5B)
val SuccessD = Color(0xFF4CAF8A)
val ErrorL   = Color(0xFFC3352B)
val ErrorD   = Color(0xFFF2685E)

// Reels chrome always sits on video, so it has its own fixed scrim regardless of theme.
val ReelScrim    = Color(0xCC000000)
val ReelOnVideo  = Color(0xFFFFFFFF)

// Text on the soft accent container. Kept next to the accent it depends on.
val OnTerracottaSoftL = Color(0xFF4A1D0E)
val OnTerracottaSoftD = Color(0xFFF7D8CB)

// Selected states: navigation bar indicator, filter chips, tonal surfaces.
//
// These MUST be set explicitly. Material 3 derives them from its baseline palette when left
// unspecified, and that baseline is violet: the nav indicator and selected chips come out
// lavender, which breaks the single-accent rule the whole palette rests on. Caught on a
// device screenshot, not in code review.
val SelectedTintL   = Color(0xFFF3E1D9)
val OnSelectedTintL = Color(0xFF6B2C13)
val SelectedTintD   = Color(0xFF3A2018)
val OnSelectedTintD = Color(0xFFF7D8CB)

// Tonal surface steps, so elevated containers stay slate rather than drifting violet.
val SurfaceLowL  = Color(0xFFF1F3F4)
val SurfaceHighL = Color(0xFFFFFFFF)
val SurfaceLowD  = Color(0xFF14181C)
val SurfaceHighD = Color(0xFF1E242A)
