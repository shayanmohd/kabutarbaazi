package com.kabutarbaazi.app.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kabutarbaazi.app.locale.AppLocale
import com.kabutarbaazi.app.ui.components.ListingCard
import com.kabutarbaazi.app.ui.components.ReportSheet
import com.kabutarbaazi.app.ui.screens.market.breedName
import com.kabutarbaazi.app.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: String,
    language: String,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.factory(userId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var reportOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.profile?.displayName ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isMe) {
                        IconButton(onClick = { reportOpen = true }) {
                            Icon(Icons.Outlined.Flag, contentDescription = "Report this person")
                        }
                        IconButton(onClick = { viewModel.block(onBack) }) {
                            Icon(Icons.Outlined.Block, contentDescription = "Block this person")
                        }
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            val p = state.profile
            if (p != null) {
                Column(Modifier.padding(16.dp)) {
                    Text("@${p.username}", style = MaterialTheme.typography.titleLarge)
                    p.bio?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("${p.followerCount} followers", style = MaterialTheme.typography.labelLarge)
                        Text("${p.followingCount} following", style = MaterialTheme.typography.labelLarge)
                    }
                    if (!state.isMe) {
                        OutlinedButton(
                            onClick = viewModel::toggleFollow,
                            shape = PillShape,
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text(if (state.following) "Following" else "Follow")
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.listings, key = { it.id }) { l ->
                    ListingCard(
                        listing = l,
                        saved = false,
                        onClick = { onOpenListing(l.id) },
                        onToggleSave = {},
                        breedName = breedName(l.breedId, l.breedOther, language),
                    )
                }
            }
        }
    }

    if (reportOpen) {
        ReportSheet(
            title = "Report this person",
            onDismiss = { reportOpen = false },
            onSubmit = { r, n -> reportOpen = false; viewModel.report(r, n) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isAdmin: Boolean,
    onBack: () -> Unit,
    onOpenModeration: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenLegal: (String) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var languageOpen by remember { mutableStateOf(false) }
    var blockedOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) { if (state.deleted) onSignedOut() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState())) {
            state.profile?.let { p ->
                Column(Modifier.padding(16.dp)) {
                    Text(p.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "@${p.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            SettingsRow(Icons.Outlined.Language, "Language", AppLocale.current().uppercase()) {
                languageOpen = true
            }
            SettingsRow(Icons.Outlined.Block, "Blocked people", "${state.blocked.size}") {
                blockedOpen = true
            }
            if (isAdmin) {
                SettingsRow(Icons.Outlined.Gavel, "Moderation queue", null, onClick = onOpenModeration)
            }
            SettingsRow(Icons.Outlined.Policy, "Community rules", null) { onOpenLegal("rules") }
            SettingsRow(Icons.Outlined.Policy, "Privacy policy", null) { onOpenLegal("privacy") }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(Icons.AutoMirrored.Outlined.Logout, "Sign out", null) { confirmSignOut = true }
            SettingsRow(
                Icons.Outlined.DeleteForever,
                "Delete my account",
                null,
                destructive = true,
            ) { confirmDelete = true }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (languageOpen) {
        AlertDialog(
            onDismissRequest = { languageOpen = false },
            title = { Text("Language") },
            text = {
                Column {
                    AppLocale.SUPPORTED.forEach { lang ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLanguage(lang.tag)
                                    AppLocale.set(lang.tag)
                                    languageOpen = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = AppLocale.current() == lang.tag, onClick = null)
                            Spacer(Modifier.padding(4.dp))
                            Text("${lang.native}  (${lang.english})")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { languageOpen = false }) { Text("Close") } },
        )
    }

    if (blockedOpen) {
        AlertDialog(
            onDismissRequest = { blockedOpen = false },
            title = { Text("Blocked people") },
            text = {
                if (state.blocked.isEmpty()) {
                    Text("You have not blocked anyone.")
                } else {
                    LazyColumn {
                        items(state.blocked, key = { it.id }) { p ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("@${p.username}", Modifier.weight(1f))
                                TextButton(onClick = { viewModel.unblock(p.id) }) { Text("Unblock") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { blockedOpen = false }) { Text("Close") } },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            // Stated plainly: there is no password recovery in v1, so signing out on a
            // forgotten password is a one-way door.
            text = { Text("Password reset is not available yet. Make sure you remember your password before signing out.") },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; viewModel.signOut(); onSignedOut() }) {
                    Text("Sign out")
                }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your account?") },
            text = {
                Text(
                    "This removes your profile, all your ads, reels, posts, comments and photos. " +
                        "It also deletes your conversations for the other person. This cannot be undone.",
                )
            },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; viewModel.deleteAccount() },
                    enabled = !state.deleting,
                ) { Text("Delete permanently") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String?,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            label,
            Modifier.weight(1f).padding(start = 14.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (destructive) FontWeight.Medium else null,
        )
        value?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
