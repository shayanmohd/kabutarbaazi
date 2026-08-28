package com.kabutarbaazi.app.ui.screens.terms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kabutarbaazi.app.ui.theme.PillShape

/**
 * Blocking, unskippable rules screen.
 *
 * Google Play's user-generated content policy requires that a user accepts the app's rules
 * before they can create or upload anything. Accepting sets profiles.terms_accepted_at, and
 * every content insert policy in the database checks that column, so this is enforced in
 * Postgres rather than only in the UI. A modified client cannot skip it.
 */
@Composable
fun TermsGateScreen(onAccept: () -> Unit, accepting: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(56.dp))
        Text("Community rules", style = MaterialTheme.typography.headlineMedium)
        Text(
            "KabutarBaazi is for pigeon keepers. These rules keep it that way.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(28.dp))

        Rule(
            Icons.Outlined.Pets,
            "Treat the birds well",
            "No cruelty, no fighting, no content that shows a bird being harmed. Only domestic " +
                "pigeons may be listed. Wild, protected or endangered species are not allowed.",
        )
        Rule(
            Icons.Outlined.VerifiedUser,
            "Be honest in your ads",
            "Describe the bird as it actually is: breed, age and condition. No fake photos, no " +
                "pretending to be someone else, and never ask a buyer for an advance payment or an OTP.",
        )
        Rule(
            Icons.Outlined.Block,
            "Keep it respectful",
            "No abuse, threats, harassment, or hateful content. No nudity or sexual content. " +
                "Anyone can block another user, and blocking hides everything in both directions.",
        )
        Rule(
            Icons.Outlined.Flag,
            "Report anything that breaks these rules",
            "Every ad, reel, post, comment, message and profile has a Report option. Reports are " +
                "reviewed, and content reported by enough people is hidden automatically.",
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "Breaking these rules can get your content removed and your account suspended.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onAccept,
            enabled = !accepting,
            shape = PillShape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("I agree", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Rule(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
