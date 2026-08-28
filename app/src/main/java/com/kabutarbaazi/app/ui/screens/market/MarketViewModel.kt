package com.kabutarbaazi.app.ui.screens.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kabutarbaazi.app.KabutarBaaziApp
import com.kabutarbaazi.app.data.ListingRepository
import com.kabutarbaazi.app.data.SessionRepository
import com.kabutarbaazi.domain.model.Listing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MarketUiState(
    val listings: List<Listing> = emptyList(),
    val savedIds: Set<String> = emptySet(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val query: String = "",
    val regionFilter: String? = null,
    val breedFilter: Int? = null,
) {
    val isEmpty: Boolean get() = !loading && listings.isEmpty() && error == null
    val hasFilters: Boolean get() = regionFilter != null || breedFilter != null || query.isNotBlank()
}

class MarketViewModel(
    private val listings: ListingRepository,
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MarketUiState())
    val state: StateFlow<MarketUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val s = _state.value
            runCatching {
                val page = listings.feed(
                    regionCode = s.regionFilter,
                    breedId = s.breedFilter,
                    query = s.query.takeIf { q -> q.isNotBlank() },
                )
                val saved = session.currentUserId()?.let { listings.savedIds(it) } ?: emptySet()
                page to saved
            }.onSuccess { (page, saved) ->
                _state.update {
                    it.copy(listings = page, savedIds = saved, loading = false, hasMore = page.size >= 20)
                }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.friendly()) }
            }
        }
    }

    /**
     * Keyset pagination on bumped_at. OFFSET would duplicate rows as sellers bump ads between
     * pages, which on an active marketplace happens constantly.
     */
    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || !s.hasMore || s.listings.isEmpty()) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching {
                listings.feed(
                    before = s.listings.last().bumpedAt,
                    regionCode = s.regionFilter,
                    breedId = s.breedFilter,
                    query = s.query.takeIf { q -> q.isNotBlank() },
                )
            }.onSuccess { page ->
                _state.update {
                    it.copy(
                        listings = it.listings + page,
                        loadingMore = false,
                        hasMore = page.size >= 20,
                    )
                }
            }.onFailure {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun onQuery(q: String) { _state.update { it.copy(query = q) } }
    fun applyFilters(region: String?, breed: Int?) {
        _state.update { it.copy(regionFilter = region, breedFilter = breed) }
        refresh()
    }
    fun clearFilters() = applyFilters(null, null)

    fun toggleSave(listingId: String) {
        val userId = session.currentUserId() ?: return
        val wasSaved = listingId in _state.value.savedIds
        // Optimistic: the bookmark must respond instantly on a slow connection.
        _state.update {
            it.copy(savedIds = if (wasSaved) it.savedIds - listingId else it.savedIds + listingId)
        }
        viewModelScope.launch {
            runCatching { listings.setSaved(userId, listingId, !wasSaved) }
                .onFailure {
                    _state.update { s ->
                        s.copy(savedIds = if (wasSaved) s.savedIds + listingId else s.savedIds - listingId)
                    }
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KabutarBaaziApp
                MarketViewModel(app.container.listings, app.container.session)
            }
        }
    }
}

internal fun Throwable.friendly(): String {
    val raw = message.orEmpty().lowercase()
    return when {
        "network" in raw || "unresolved" in raw || "timeout" in raw || "connect" in raw ->
            "No internet connection."
        else -> "Could not load. Please try again."
    }
}
