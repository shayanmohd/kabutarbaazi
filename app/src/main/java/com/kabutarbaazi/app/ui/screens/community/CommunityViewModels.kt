package com.kabutarbaazi.app.ui.screens.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kabutarbaazi.app.KabutarBaaziApp
import com.kabutarbaazi.app.data.CommunityRepository
import com.kabutarbaazi.app.data.ModerationRepository
import com.kabutarbaazi.app.data.SessionRepository
import com.kabutarbaazi.domain.model.Community
import com.kabutarbaazi.domain.model.CommunityPost
import com.kabutarbaazi.domain.model.PostComment
import com.kabutarbaazi.domain.model.ReportReason
import com.kabutarbaazi.domain.model.ReportTargetType
import com.kabutarbaazi.domain.moderation.ModerationRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommunitiesUiState(
    val communities: List<Community> = emptyList(),
    val joinedIds: Set<String> = emptySet(),
    val loading: Boolean = true,
    val error: String? = null,
)

class CommunitiesViewModel(
    private val communities: CommunityRepository,
    private val session: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CommunitiesUiState())
    val state: StateFlow<CommunitiesUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val all = communities.all()
                val joined = session.currentUserId()?.let { communities.joinedIds(it) }.orEmpty()
                all to joined
            }.onSuccess { (all, joined) ->
                _state.update { it.copy(communities = all, joinedIds = joined, loading = false) }
            }.onFailure {
                _state.update { it.copy(loading = false, error = "Could not load groups.") }
            }
        }
    }

    fun toggleJoin(communityId: String) {
        val me = session.currentUserId() ?: return
        val was = communityId in _state.value.joinedIds
        _state.update {
            it.copy(
                joinedIds = if (was) it.joinedIds - communityId else it.joinedIds + communityId,
                communities = it.communities.map { c ->
                    if (c.id == communityId) c.copy(memberCount = c.memberCount + if (was) -1 else 1) else c
                },
            )
        }
        viewModelScope.launch {
            runCatching { communities.setJoined(me, communityId, !was) }.onFailure {
                _state.update { s ->
                    s.copy(joinedIds = if (was) s.joinedIds + communityId else s.joinedIds - communityId)
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KabutarBaaziApp
                CommunitiesViewModel(app.container.communities, app.container.session)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

data class CommunityFeedUiState(
    val community: Community? = null,
    val posts: List<CommunityPost> = emptyList(),
    val likedIds: Set<String> = emptySet(),
    val isMember: Boolean = false,
    val loading: Boolean = true,
    val posting: Boolean = false,
    val draft: String = "",
    val error: String? = null,
    val toast: String? = null,
    val comments: List<PostComment> = emptyList(),
    val commentsFor: String? = null,
)

class CommunityFeedViewModel(
    private val communityId: String,
    private val communities: CommunityRepository,
    private val session: SessionRepository,
    private val moderation: ModerationRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CommunityFeedUiState())
    val state: StateFlow<CommunityFeedUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val me = session.currentUserId()
                val community = communities.all().firstOrNull { it.id == communityId }
                val posts = communities.feed(communityId)
                val joined = me?.let { communities.joinedIds(it) }.orEmpty()
                val liked = me?.let { communities.likedPostIds(it) }.orEmpty()
                listOf(community, posts, joined, liked)
            }.onSuccess { parts ->
                @Suppress("UNCHECKED_CAST")
                _state.update {
                    it.copy(
                        community = parts[0] as Community?,
                        posts = parts[1] as List<CommunityPost>,
                        isMember = communityId in (parts[2] as Set<String>),
                        likedIds = parts[3] as Set<String>,
                        loading = false,
                    )
                }
            }.onFailure {
                _state.update { it.copy(loading = false, error = "Could not load this group.") }
            }
        }
    }

    fun onDraft(v: String) = _state.update { it.copy(draft = v, error = null) }

    fun join() {
        val me = session.currentUserId() ?: return
        viewModelScope.launch {
            runCatching { communities.setJoined(me, communityId, true) }
                .onSuccess { _state.update { it.copy(isMember = true) } }
        }
    }

    fun post() {
        val me = session.currentUserId() ?: return
        val body = _state.value.draft.trim()
        if (body.isBlank() || _state.value.posting) return

        if (ModerationRules.screen(body).severity == ModerationRules.Severity.Block) {
            _state.update { it.copy(error = "Please remove abusive language.") }
            return
        }

        _state.update { it.copy(posting = true) }
        viewModelScope.launch {
            runCatching { communities.createPost(communityId, me, body) }
                .onSuccess { _state.update { it.copy(posting = false, draft = "") }; refresh() }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            posting = false,
                            error = if ("row-level security" in e.message.orEmpty()) {
                                "Join this group before posting."
                            } else "Could not post."
                        )
                    }
                }
        }
    }

    fun toggleLike(postId: String) {
        val me = session.currentUserId() ?: return
        val was = postId in _state.value.likedIds
        _state.update {
            it.copy(
                likedIds = if (was) it.likedIds - postId else it.likedIds + postId,
                posts = it.posts.map { p ->
                    if (p.id == postId) p.copy(likeCount = p.likeCount + if (was) -1 else 1) else p
                },
            )
        }
        viewModelScope.launch {
            runCatching { communities.setPostLiked(me, postId, !was) }
        }
    }

    fun openComments(postId: String) {
        _state.update { it.copy(commentsFor = postId, comments = emptyList()) }
        viewModelScope.launch {
            runCatching { communities.comments(postId) }
                .onSuccess { c -> _state.update { it.copy(comments = c) } }
        }
    }

    fun closeComments() = _state.update { it.copy(commentsFor = null, comments = emptyList()) }

    fun addComment(body: String) {
        val me = session.currentUserId() ?: return
        val postId = _state.value.commentsFor ?: return
        viewModelScope.launch {
            runCatching { communities.addComment(postId, me, body) }.onSuccess { openComments(postId) }
        }
    }

    fun report(type: ReportTargetType, id: String, reason: ReportReason, note: String?) {
        viewModelScope.launch {
            runCatching { moderation.report(type, id, reason, note) }
                .onSuccess { _state.update { it.copy(toast = "Report sent. Thank you.") } }
        }
    }

    fun blockAuthor(authorId: String) = viewModelScope.launch {
        val me = session.currentUserId() ?: return@launch
        runCatching { moderation.block(me, authorId) }.onSuccess {
            _state.update {
                it.copy(posts = it.posts.filterNot { p -> p.authorId == authorId }, toast = "Blocked.")
            }
        }
    }

    fun deletePost(postId: String) = viewModelScope.launch {
        runCatching { communities.deletePost(postId) }
            .onSuccess { _state.update { it.copy(posts = it.posts.filterNot { p -> p.id == postId }) } }
    }

    fun clearToast() = _state.update { it.copy(toast = null, error = null) }

    companion object {
        fun factory(communityId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KabutarBaaziApp
                CommunityFeedViewModel(
                    communityId, app.container.communities,
                    app.container.session, app.container.moderation,
                )
            }
        }
    }
}
