package com.kabutarbaazi.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// One radius system, documented and applied everywhere:
//   containers and sheets   16dp
//   inner elements, inputs, chips, images inside cards   12dp
//   buttons and filter chips   full pill
// Mixing radii without a rule is the fastest way to make an app look unfinished.
val KabutarShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

val PillShape = RoundedCornerShape(percent = 50)
val CardShape = RoundedCornerShape(16.dp)
val InnerShape = RoundedCornerShape(12.dp)
