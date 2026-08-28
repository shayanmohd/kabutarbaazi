package com.kabutarbaazi.app.ui.screens.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kabutarbaazi.app.R
import com.kabutarbaazi.app.ui.components.EmptyState
import com.kabutarbaazi.app.ui.components.ErrorBanner
import com.kabutarbaazi.app.ui.components.ListingCard
import com.kabutarbaazi.app.ui.components.ListingCardSkeleton
import com.kabutarbaazi.app.ui.theme.PillShape
import com.kabutarbaazi.domain.model.Breeds
import com.kabutarbaazi.domain.model.Regions

@Composable
fun MarketScreen(
    onOpenListing: (String) -> Unit,
    onPostAd: () -> Unit,
    language: String,
    viewModel: MarketViewModel = viewModel(factory = MarketViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // Fetch the next page before the user reaches the bottom, so scrolling never stalls.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.listings.size - 4
        }
    }
    androidx.compose.runtime.LaunchedEffect(shouldLoadMore, state.listings.size) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQuery,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = PillShape,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { viewModel.refresh() },
            ),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            item {
                FilterChip(
                    selected = state.regionFilter == null && state.breedFilter == null,
                    onClick = { viewModel.clearFilters() },
                    label = { Text(stringResource(R.string.filter_all)) },
                    shape = PillShape,
                )
            }
            items(Regions.ALL) { region ->
                FilterChip(
                    selected = state.regionFilter == region.code,
                    onClick = {
                        viewModel.applyFilters(
                            if (state.regionFilter == region.code) null else region.code,
                            state.breedFilter,
                        )
                    },
                    label = { Text(regionName(region.code, language)) },
                    shape = PillShape,
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }

        state.error?.let { ErrorBanner(it, onRetry = viewModel::refresh) }

        when {
            state.loading -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(6) { ListingCardSkeleton() }
            }

            state.isEmpty -> EmptyState(
                icon = Icons.Outlined.Storefront,
                title = if (state.hasFilters) "Koi ad nahi mila" else stringResource(R.string.empty_bazaar_title),
                body = if (state.hasFilters) {
                    "Try a different area or breed."
                } else {
                    stringResource(R.string.empty_bazaar_body)
                },
                actionLabel = if (state.hasFilters) "Clear filters" else stringResource(R.string.post_an_ad),
                onAction = if (state.hasFilters) viewModel::clearFilters else onPostAd,
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                // Extra bottom padding so the floating action button never covers a card.
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.listings, key = { it.id }) { listing ->
                    ListingCard(
                        listing = listing,
                        saved = listing.id in state.savedIds,
                        onClick = { onOpenListing(listing.id) },
                        onToggleSave = { viewModel.toggleSave(listing.id) },
                        breedName = breedName(listing.breedId, listing.breedOther, language),
                    )
                }
                if (state.loadingMore) {
                    items(2) { ListingCardSkeleton() }
                }
            }
        }
    }
}

internal fun regionName(code: String?, language: String): String {
    val r = Regions.BY_CODE[code] ?: return ""
    return when (language) { "hi" -> r.nameHi; "ur" -> r.nameUr; else -> r.nameEn }
}

/**
 * Breed ids come from the database, but the display names live in the bundled catalogue so a
 * card can render before any network call returns.
 */
internal fun breedName(breedId: Int?, breedOther: String?, language: String): String? {
    if (breedId == null) return breedOther
    val b = Breeds.ALL.getOrNull(breedId - 1) ?: return breedOther
    if (b.slug == "other") return breedOther ?: b.nameEn
    return when (language) { "hi" -> b.nameHi; "ur" -> b.nameUr; else -> b.nameEn }
}
