package com.kabutarbaazi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kabutarbaazi.app.ui.theme.InnerShape

/**
 * Shown when local.properties has no Supabase credentials. A fresh clone would otherwise crash
 * on first launch with a stack trace that says nothing about what is actually missing.
 */
@Composable
fun SetupRequiredScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(64.dp))
        Text("Backend not configured", style = MaterialTheme.typography.headlineMedium)
        Text(
            "The app builds, but it has no Supabase project to talk to yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text("Add these to local.properties:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "SUPABASE_URL=https://<project>.supabase.co\n" +
                "SUPABASE_ANON_KEY=<anon key>\n" +
                "MEDIA_BASE_URL=https://kb-media.kamapathy.app",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .clip(InnerShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Then apply supabase/migrations in order and rebuild. " +
                "Full steps are in docs/BUILDING.md.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
