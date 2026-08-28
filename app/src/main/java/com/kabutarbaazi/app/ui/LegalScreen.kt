package com.kabutarbaazi.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(doc: String, onBack: () -> Unit) {
    val title = if (doc == "privacy") "Privacy policy" else "Community rules"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            if (doc == "privacy") {
                Section("What we collect", PRIVACY_COLLECT)
                Section("Your phone number", PRIVACY_PHONE)
                Section("Photos and videos", PRIVACY_MEDIA)
                Section("Deleting your account", PRIVACY_DELETE)
            } else {
                Section("Treat the birds well", RULE_ANIMALS)
                Section("Be honest in your ads", RULE_HONEST)
                Section("Keep it respectful", RULE_RESPECT)
                Section("Report anything that breaks these rules", RULE_REPORT)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Section(heading: String, body: String) {
    Text(heading, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

private const val PRIVACY_COLLECT =
    "Your username, display name, phone number, area, and anything you post: ads, photos, " +
        "videos, posts, comments and messages. We also store a notification token for your device."

private const val PRIVACY_PHONE =
    "Your phone number is hidden by default. It is shown to a signed-in buyer only when they " +
        "tap the WhatsApp button on one of your ads, and we log those reveals to catch abuse. " +
        "It is never shown on your profile and never sold."

private const val PRIVACY_MEDIA =
    "Photos are re-encoded before upload, which removes EXIF data including GPS location. " +
        "Outdoor pigeon photos often carry coordinates, and we do not want to publish where you live."

private const val PRIVACY_DELETE =
    "Settings has Delete my account. It removes your profile, ads, reels, posts, comments, " +
        "messages and every photo and video you uploaded. Reports filed against an account are " +
        "kept for 12 months so deleting an account cannot erase evidence of abuse."

private const val RULE_ANIMALS =
    "No cruelty, no fighting, and no content showing a bird being harmed. Only domestic pigeons " +
        "may be listed. Wild, protected or endangered species are not allowed."

private const val RULE_HONEST =
    "Describe the bird as it actually is. No fake photos, no pretending to be someone else, " +
        "and never ask a buyer for an advance payment or an OTP."

private const val RULE_RESPECT =
    "No abuse, threats, harassment or hateful content. No nudity or sexual content. Anyone can " +
        "block another user, and blocking hides everything in both directions."

private const val RULE_REPORT =
    "Every ad, reel, post, comment, message and profile has a Report option. Reports are reviewed, " +
        "and content reported by enough separate people is hidden automatically."
