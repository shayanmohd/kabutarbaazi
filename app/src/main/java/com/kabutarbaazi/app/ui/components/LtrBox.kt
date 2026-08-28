package com.kabutarbaazi.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Forces left-to-right for content whose order is spatial rather than linguistic.
 *
 * Under Urdu, HorizontalPager mirrors, so photo 1 of a listing lands on the right and swiping
 * feels backwards. Photo order is content, not text. Same reasoning applies to a video scrubber
 * and to phone number entry, where mixing Urdu text with Latin digits otherwise renders
 * bidirectionally scrambled.
 *
 * This is the single most likely RTL bug in the app and the least likely to be noticed while
 * testing in English.
 */
@Composable
fun LtrBox(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}
