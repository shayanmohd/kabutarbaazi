package com.kabutarbaazi.app.ui.screens.market

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kabutarbaazi.app.KabutarBaaziApp
import com.kabutarbaazi.app.data.ListingRepository
import com.kabutarbaazi.app.data.MediaUploadRepository
import com.kabutarbaazi.app.data.SessionRepository
import com.kabutarbaazi.app.media.ImageCompressor
import com.kabutarbaazi.domain.media.MediaConstraints
import com.kabutarbaazi.domain.media.MediaKind
import com.kabutarbaazi.domain.format.PriceFormat
import com.kabutarbaazi.domain.model.ListingMedia
import com.kabutarbaazi.domain.model.NewListing
import com.kabutarbaazi.domain.moderation.ModerationRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class CreateListingUiState(
    val title: String = "",
    val description: String = "",
    val price: String = "",
    val negotiable: Boolean = true,
    val quantity: String = "1",
    val breedId: Int? = null,
    val breedOther: String = "",
    val regionCode: String? = null,
    val city: String = "",
    val photos: List<Uri> = emptyList(),
    val submitting: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String? = null,
    val error: String? = null,
    val blockedReason: String? = null,
) {
    val canSubmit: Boolean
        get() = !submitting &&
            title.trim().length in 3..80 &&
            PriceFormat.parseToMinor(price) != null &&
            regionCode != null &&
            photos.isNotEmpty()
}

class CreateListingViewModel(
    private val listings: ListingRepository,
    private val media: MediaUploadRepository,
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateListingUiState())
    val state: StateFlow<CreateListingUiState> = _state.asStateFlow()

    fun onTitle(v: String) = _state.update { it.copy(title = v, blockedReason = null) }
    fun onDescription(v: String) = _state.update { it.copy(description = v, blockedReason = null) }
    fun onPrice(v: String) = _state.update { it.copy(price = v.filter(Char::isDigit)) }
    fun onNegotiable(v: Boolean) = _state.update { it.copy(negotiable = v) }
    fun onQuantity(v: String) = _state.update { it.copy(quantity = v.filter(Char::isDigit).take(3)) }
    fun onBreed(id: Int?) = _state.update { it.copy(breedId = id) }
    fun onBreedOther(v: String) = _state.update { it.copy(breedOther = v) }
    fun onRegion(v: String) = _state.update { it.copy(regionCode = v) }
    fun onCity(v: String) = _state.update { it.copy(city = v) }
    fun clearError() = _state.update { it.copy(error = null, blockedReason = null) }

    fun onPhotosPicked(uris: List<Uri>) = _state.update {
        it.copy(photos = (it.photos + uris).distinct().take(MediaConstraints.LISTING_MAX_IMAGES))
    }
    fun removePhoto(uri: Uri) = _state.update { it.copy(photos = it.photos - uri) }

    fun submit(context: Context, onCreated: (String) -> Unit) {
        val s = _state.value
        if (!s.canSubmit) return

        // Screened before anything is uploaded. Abusive text is refused outright; scam patterns
        // are allowed through but flagged for the moderation queue, because silencing an honest
        // seller discussing payment is worse than a slow review.
        val screen = ModerationRules.screen("${s.title} ${s.description}")
        if (screen.severity == ModerationRules.Severity.Block) {
            _state.update {
                it.copy(blockedReason = "Please remove abusive language before posting.")
            }
            return
        }

        val sellerId = session.currentUserId() ?: return
        _state.update { it.copy(submitting = true, error = null, progress = 0f) }

        viewModelScope.launch {
            runCatching {
                val listing = listings.create(
                    NewListing(
                        sellerId = sellerId,
                        title = s.title.trim(),
                        description = s.description.trim().ifBlank { null },
                        breedId = s.breedId,
                        breedOther = s.breedOther.trim().ifBlank { null },
                        priceMinor = PriceFormat.parseToMinor(s.price)!!,
                        currency = "INR",
                        isNegotiable = s.negotiable,
                        quantity = s.quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        regionCode = s.regionCode!!,
                        city = s.city.trim().ifBlank { null },
                    ),
                )

                // Media is uploaded after the row exists so each object can be keyed by listing,
                // and a failure here leaves a listing without photos rather than an orphan object.
                val uploadId = UUID.randomUUID().toString()
                val rows = s.photos.mapIndexed { index, uri ->
                    _state.update {
                        it.copy(progressLabel = "Photo ${index + 1} of ${s.photos.size}")
                    }
                    val file = ImageCompressor.compress(
                        context = context,
                        source = uri,
                        outputName = "listing_${uploadId}_$index.webp",
                    )
                    val url = media.upload(
                        file = file,
                        kind = MediaKind.ListingImage,
                        uploadId = uploadId,
                        index = index,
                        onProgress = { p ->
                            val overall = (index + p) / s.photos.size
                            _state.update { st -> st.copy(progress = overall) }
                        },
                    )
                    file.delete()
                    ListingMedia(
                        id = UUID.randomUUID().toString(),
                        listingId = listing.id,
                        kind = "image",
                        url = url,
                        thumbUrl = url,
                        position = index,
                    )
                }
                listings.attachMedia(rows)
                listing.id
            }.onSuccess { id ->
                _state.update { it.copy(submitting = false, progress = 1f) }
                onCreated(id)
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        submitting = false,
                        progressLabel = null,
                        error = when {
                            "too_large" in e.message.orEmpty() -> "One of the photos is too large."
                            "rate_limited" in e.message.orEmpty() -> "Too many uploads just now. Please wait a few minutes."
                            "terms_not_accepted" in e.message.orEmpty() -> "Please accept the community rules first."
                            else -> "Could not post the ad. Please try again."
                        },
                    )
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KabutarBaaziApp
                CreateListingViewModel(app.container.listings, app.container.media, app.container.session)
            }
        }
    }
}
