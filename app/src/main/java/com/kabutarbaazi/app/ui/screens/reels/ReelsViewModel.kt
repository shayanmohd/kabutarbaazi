package com.kabutarbaazi.app.ui.screens.reels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kabutarbaazi.app.KabutarBaaziApp
import com.kabutarbaazi.app.data.ModerationRepository
import com.kabutarbaazi.app.data.ProfileRepository
import com.kabutarbaazi.app.data.ReelRepository
import com.kabutarbaazi.app.data.SessionRepository
import com.kabutarbaazi.domain.feed.ReelWindow
import com.kabutarbaazi.domain.model.Reel
import com.kabutarbaazi.domain.model.ReelComment
import com.kabutarbaazi.domain.model.ReportReason
import com.kabutarbaazi.domain.model.ReportTargetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReelsTab { ForYou, Following }

data class ReelsUiState(
    val tab: ReelsTab = ReelsTab.ForYou,
    val reels: List<Reel> = emptyList(),
    val likedIds: Set<String> = emptySet(),
    val followingIds: Set<String> = emptySet(),
    val loading: Boolean = true,
    val hasMore: Boolean = true,
    val muted: Boolean = false,
    val error: String? = null,
    val comments: List<ReelComment> = emptyList(),
    val commentsFor: String? = null,
    val toast: String? = null,
)

class ReelsViewModel(
    private val reels: ReelRepository,
    private val profiles: ProfileRepository,
    private val session: SessionRepository,
    private val moderation: ModerationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReelsUiState())
    val state: StateFlow<ReelsUiState> = _state.asStateFlow()

    init { refresh() }

    fun switchTab(tab: ReelsTab) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab, reels = emptyList(), loading = true) }
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val tab = _state.value.tab
            runCatching {
                val page = if (tab == ReelsTab.Following) reels.following() else reels.feed()
                val me = session.currentUserId()
                val liked = me?.let { reels.likedIds(it) }.orEmpty()
                val following = me?.let { profiles.followingIds(it) }.orEmpty()
                Triple(page, liked, following)
            }.onSuccess { (page, liked, following) ->
                _state.update {
                    it.copy(
                        reels = page, likedIds = liked, followingIds = following,
                        loading = false, hasMore = page.size >= 10,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = "Could not load reels.") }
            }
        }
    }

    fun onSettled(index: Int) {
        val s = _state.value
        if (ReelWindow.shouldLoadMore(index, s.reels.size, s.hasMore)) loadMore()
    }

    private fun loadMore() {
        val s = _state.value
        if (s.reels.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val before = s.reels.last().createdAt
                if (s.tab == ReelsTab.Following) reels.following(before) else reels.feed(before)
            }.onSuccess { page ->
                _state.update {
                    // Dedupe: a reel can arrive twice if one is posted between page fetches.
                    val existing = it.reels.map { r -> r.id }.toSet()
                    it.copy(
                        reels = it.reels + page.filter { r -> r.id !in existing },
                        hasMore = page.size >= 10,
                    )
                }
            }
        }
    }

    fun toggleMute() = _state.update { it.copy(muted = !it.muted) }

    fun toggleLike(reelId: String) {
        val me = session.currentUserId() ?: return
        val was = reelId in _state.value.likedIds
        _state.update {
            it.copy(
                likedIds = if (was) it.likedIds - reelId else it.likedIds + reelId,
                reels = it.reels.map { r ->
                    if (r.id == reelId) r.copy(likeCount = r.likeCount + if (was) -1 else 1) else r
                },
            )
        }
        viewModelScope.launch {
            runCatching { reels.setLiked(me, reelId, !was) }.onFailure {
                _state.update { s ->
                    s.copy(likedIds = if (was) s.likedIds + reelId else s.likedIds - reelId)
                }
            }
        }
    }

    fun toggleFollow(authorId: String) {
        val me = session.currentUserId() ?: return
        if (me == authorId) return
        val was = authorId in _state.value.followingIds
        _state.update {
            it.copy(followingIds = if (was) it.followingIds - authorId else it.followingIds + authorId)
        }
        viewModelScope.launch {
            runCatching { profiles.setFollowing(me, authorId, !was) }.onFailure {
                _state.update { s ->
                    s.copy(followingIds = if (was) s.followingIds + authorId else s.followingIds - authorId)
                }
            }
        }
    }

    fun openComments(reelId: String) {
        _state.update { it.copy(commentsFor = reelId, comments = emptyList()) }
        viewModelScope.launch {
            runCatching { reels.comments(reelId) }
                .onSuccess { c -> _state.update { it.copy(comments = c) } }
        }
    }

    fun closeComments() = _state.update { it.copy(commentsFor = null, comments = emptyList()) }

    fun addComment(body: String) {
        val me = session.currentUserId() ?: return
        val reelId = _state.value.commentsFor ?: return
        viewModelScope.launch {
            runCatching { reels.addComment(reelId, me, body) }
                .onSuccess {
                    openComments(reelId)
                    _state.update {
                        it.copy(reels = it.reels.map { r ->
                            if (r.id == reelId) r.copy(commentCount = r.commentCount + 1) else r
                        })
                    }
                }
        }
    }

    fun report(targetType: ReportTargetType, id: String, reason: ReportReason, note: String?) {
        viewModelScope.launch {
            runCatching { moderation.report(targetType, id, reason, note) }
                .onSuccess { _state.update { it.copy(toast = "Report sent. Thank you.") } }
        }
    }

    fun blockAuthor(authorId: String) = viewModelScope.launch {
        val me = session.currentUserId() ?: return@launch
        runCatching { moderation.block(me, authorId) }.onSuccess {
            _state.update {
                it.copy(
                    reels = it.reels.filterNot { r -> r.authorId == authorId },
                    toast = "Blocked. You will not see their content.",
                )
            }
        }
    }

    fun deleteOwn(reelId: String) = viewModelScope.launch {
        runCatching { reels.delete(reelId) }.onSuccess {
            _state.update { it.copy(reels = it.reels.filterNot { r -> r.id == reelId }) }
        }
    }

    fun clearToast() = _state.update { it.copy(toast = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KabutarBaaziApp
                ReelsViewModel(
                    app.container.reels, app.container.profiles,
                    app.container.session, app.container.moderation,
                )
            }
        }
    }
}
