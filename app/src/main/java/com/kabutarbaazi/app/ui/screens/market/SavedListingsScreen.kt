package com.kabutarbaazi.app.ui.screens.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kabutarbaazi.app.KabutarBaaziApp
import com.kabutarbaazi.app.ui.components.EmptyState
import com.kabutarbaazi.app.ui.components.ListingCard
import com.kabutarbaazi.domain.model.Listing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedListingsScreen(
    language: String,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit,
) {
    val container = (LocalContext.current.applicationContext as KabutarBaaziApp).container
    var items by remember { mutableStateOf<List<Listing>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val me = container.session.currentUserId()
        items = if (me != null) runCatching { container.listings.saved(me) }.getOrDefault(emptyList()) else emptyList()
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved ads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (items.isEmpty() && !loading) {
                EmptyState(
                    icon = Icons.Outlined.BookmarkBorder,
                    title = "Kuch save nahi kiya",
                    body = "Jo kabutar pasand aaye, uske bookmark par tap karein. Yahan mil jayega.",
                )
                return@Column
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { l ->
                    ListingCard(
                        listing = l,
                        saved = true,
                        onClick = { onOpenListing(l.id) },
                        onToggleSave = {},
                        breedName = breedName(l.breedId, l.breedOther, language),
                    )
                }
            }
        }
    }
}
