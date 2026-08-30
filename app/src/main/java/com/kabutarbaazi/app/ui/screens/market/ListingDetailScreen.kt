package com.kabutarbaazi.app.ui.screens.market

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kabutarbaazi.app.ui.components.ErrorBanner
import com.kabutarbaazi.app.ui.components.LtrBox
import com.kabutarbaazi.app.ui.components.ReportSheet
import com.kabutarbaazi.app.ui.theme.PillShape
import com.kabutarbaazi.app.ui.theme.PriceStyle
import com.kabutarbaazi.domain.format.PriceFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    listingId: String,
    language: String,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: ListingDetailViewModel = viewModel(factory = ListingDetailViewModel.factory(listingId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }

    // Launching WhatsApp is a user-initiated intent, not a background transfer.
    LaunchedEffect(state.whatsAppUrl) {
        state.whatsAppUrl?.let { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            }.onFailure { snackbar.showSnackbar("WhatsApp is not installed on this phone.") }
            viewModel.consumeWhatsAppUrl()
        }
    }
    LaunchedEffect(state.actionDone, state.contactError) {
        (state.actionDone ?: state.contactError)?.let {
            snackbar.showSnackbar(it)
            viewModel.clearAction()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleSave) {
                        Icon(
                            if (state.saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (state.saved) "Saved" else "Save",
                            tint = if (state.saved) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(menuOpen, { menuOpen = false }) {
                            if (state.isOwn) {
                                DropdownMenuItem(
                                    text = { Text("Mark as sold") },
                                    onClick = { menuOpen = false; viewModel.markSold() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete this ad") },
                                    onClick = { menuOpen = false; viewModel.delete(onBack) },
                                )
                            } else {
                                // Report and block are two taps from any content, which is what
                                // Play's UGC policy asks for.
                                DropdownMenuItem(
                                    text = { Text("Report this ad") },
                                    leadingIcon = { Icon(Icons.Outlined.Flag, null) },
                                    onClick = { menuOpen = false; reportOpen = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Block this seller") },
                                    leadingIcon = { Icon(Icons.Outlined.Block, null) },
                                    onClick = { menuOpen = false; viewModel.blockSeller() },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            val l = state.listing
            if (l != null && !state.isOwn && !l.isSold) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.openChat(onOpenChat) },
                        shape = PillShape,
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Chat, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Message")
                    }
                    Button(
                        onClick = {
                            viewModel.revealWhatsApp("Salaam, ye kabutar abhi available hai? (${l.title})")
                        },
                        enabled = !state.revealing,
                        shape = PillShape,
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) {
                        if (state.revealing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("WhatsApp")
                        }
                    }
                }
            }
        },
    ) { inner ->
        val l = state.listing
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState()),
        ) {
            state.error?.let { ErrorBanner(it, onRetry = viewModel::load) }
            if (l == null) return@Column

            if (l.media.isNotEmpty()) {
                // Forced LTR: photo order is spatial, not linguistic. Under Urdu a
                // HorizontalPager mirrors and photo 1 lands on the right, which reads as a bug.
                LtrBox {
                    val pager = rememberPagerState { l.media.size }
                    Box {
                        HorizontalPager(
                            state = pager,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        ) { page ->
                            val m = l.media.sortedBy { it.position }[page]
                            AsyncImage(
                                model = m.displayUrl,
                                contentDescription = l.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Row(
                            Modifier.align(Alignment.BottomCenter).padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            repeat(l.media.size) { i ->
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (i == pager.currentPage) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            Column(Modifier.padding(16.dp)) {
                Text(PriceFormat.format(l.priceMinor, l.currency), style = PriceStyle.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize))
                if (l.isNegotiable) {
                    Text(
                        "Negotiable",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(l.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))

                val meta = listOfNotNull(
                    breedName(l.breedId, l.breedOther, language),
                    if (l.quantity > 1) "${l.quantity} birds" else null,
                    l.city,
                    regionName(l.regionCode, language).takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                l.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
                }

                l.seller?.let { seller ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(seller.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "@${seller.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { onOpenProfile(seller.id) }, shape = PillShape) {
                            Text("View profile")
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (reportOpen) {
        ReportSheet(
            title = "Report this ad",
            onDismiss = { reportOpen = false },
            onSubmit = { reason, note ->
                reportOpen = false
                viewModel.report(reason, note)
            },
        )
    }
}
