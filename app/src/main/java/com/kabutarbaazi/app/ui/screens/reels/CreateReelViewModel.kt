package com.kabutarbaazi.app.ui.screens.reels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.kabutarbaazi.app.KabutarBaaziApp
import com.kabutarbaazi.app.data.MediaUploadRepository
import com.kabutarbaazi.app.data.ReelRepository
import com.kabutarbaazi.app.data.SessionRepository
import com.kabutarbaazi.app.media.ImageCompressor
import com.kabutarbaazi.app.media.VideoCompressor
import com.kabutarbaazi.app.media.VideoThumbnailer
import com.kabutarbaazi.domain.media.MediaConstraints
import com.kabutarbaazi.domain.media.MediaKind
import com.kabutarbaazi.domain.media.MediaRejection
import com.kabutarbaazi.domain.model.NewReel
import com.kabutarbaazi.domain.moderation.ModerationRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class CreateReelUiState(
    val videoUri: Uri? = null,
    val durationMs: Int = 0,
    val caption: String = "",
    val regionCode: String? = null,
    val stage: String? = null,
    val progress: Float = 0f,
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean get() = !submitting && videoUri != null
}

@UnstableApi
class CreateReelViewModel(
    private val reels: ReelRepository,
    private val media: MediaUploadRepository,
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateReelUiState())
    val state: StateFlow<CreateReelUiState> = _state.asStateFlow()

    fun onCaption(v: String) = _state.update { it.copy(caption = v, error = null) }
    fun onRegion(v: String) = _state.update { it.copy(regionCode = v) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun onVideoPicked(context: Context, uri: Uri) {
        _state.update { it.copy(stage = "Checking video...", error = null) }
        viewModelScope.launch {
            runCatching { VideoThumbnailer.probe(context, uri) }
                .onSuccess { info ->
                    val rejection = MediaConstraints.check(
                        MediaKind.ReelVideo,
                        bytes = 1,          // real size is only known after compression
                        durationMs = info.durationMs,
                    )
                    if (rejection == MediaRejection.TooLong) {
                        _state.update {
                            it.copy(
                                stage = null,
                                error = "That video is longer than 90 seconds. Please pick a shorter one.",
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(videoUri = uri, durationMs = info.durationMs, stage = null)
                        }
                    }
                }
                .onFailure {
                    _state.update { it.copy(stage = null, error = "Could not read that video.") }
                }
        }
    }

    fun submit(context: Context, onCreated: () -> Unit) {
        val s = _state.value
        val uri = s.videoUri ?: return
        val authorId = session.currentUserId() ?: return

        if (ModerationRules.screen(s.caption).severity == ModerationRules.Severity.Block) {
            _state.update { it.copy(error = "Please remove abusive language from the caption.") }
            return
        }

        _state.update { it.copy(submitting = true, error = null, progress = 0f) }
        viewModelScope.launch {
            runCatching {
                val uploadId = UUID.randomUUID().toString()
                val info = VideoThumbnailer.probe(context, uri)

                _state.update { it.copy(stage = "Compressing video...") }
                val compressed = VideoCompressor.compress(
                    context = context,
                    source = uri,
                    sourceWidth = info.width,
                    sourceHeight = info.height,
                    outputName = "reel_$uploadId.mp4",
                    onProgress = { p -> _state.update { st -> st.copy(progress = p * 0.5f) } },
                )

                MediaConstraints.check(
                    MediaKind.ReelVideo, compressed.file.length(), compressed.durationMs,
                )?.let { error("media_rejected:$it") }

                _state.update { it.copy(stage = "Making thumbnail...") }
                val thumbFile = VideoThumbnailer.thumbnail(context, uri, "reel_${uploadId}_thumb.webp")

                _state.update { it.copy(stage = "Uploading...") }
                val videoUrl = media.upload(
                    file = compressed.file,
                    kind = MediaKind.ReelVideo,
                    uploadId = uploadId,
                    onProgress = { p -> _state.update { st -> st.copy(progress = 0.5f + p * 0.45f) } },
                )
                val thumbUrl = media.upload(
                    file = thumbFile,
                    kind = MediaKind.Thumbnail,
                    uploadId = uploadId,
                )
                compressed.file.delete()
                thumbFile.delete()

                reels.create(
                    NewReel(
                        authorId = authorId,
                        caption = s.caption.trim().ifBlank { null },
                        videoUrl = videoUrl,
                        thumbUrl = thumbUrl,
                        // Dimensions come from the export result, not the source: portrait video
                        // carries rotation metadata and guessing makes every thumbnail sideways.
                        width = compressed.width,
                        height = compressed.height,
                        durationMs = compressed.durationMs,
                        regionCode = s.regionCode,
                    ),
                )
            }.onSuccess {
                _state.update { it.copy(submitting = false, progress = 1f, stage = null) }
                onCreated()
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        submitting = false, stage = null,
                        error = when {
                            "TooLarge" in e.message.orEmpty() ->
                                "That video is still too big after compressing. Try a shorter clip."
                            "rate_limited" in e.message.orEmpty() ->
                                "Too many uploads just now. Please wait a few minutes."
                            else -> "Could not upload the reel. Please try again."
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
                CreateReelViewModel(app.container.reels, app.container.media, app.container.session)
            }
        }
    }
}
