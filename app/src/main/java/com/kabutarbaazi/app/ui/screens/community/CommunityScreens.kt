package com.kabutarbaazi.app.ui.screens.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kabutarbaazi.app.ui.components.EmptyState
import com.kabutarbaazi.app.ui.components.ErrorBanner
import com.kabutarbaazi.app.ui.components.ReportSheet
import com.kabutarbaazi.app.ui.components.CommentRow
import com.kabutarbaazi.app.ui.components.CommentsSheet
import com.kabutarbaazi.app.ui.theme.PillShape
import com.kabutarbaazi.domain.model.ReportTargetType

@Composable
fun CommunitiesScreen(
    language: String,
    onOpenCommunity: (String) -> Unit,
    viewModel: CommunitiesViewModel = viewModel(factory = CommunitiesViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        state.error?.let { ErrorBanner(it, onRetry = viewModel::refresh) }
        if (state.communities.isEmpty() && !state.loading) {
            EmptyState(
                icon = Icons.Outlined.Groups,
                title = "No groups yet",
                body = "Groups are organised by area. They will appear here.",
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.communities, key = { it.id }) { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCommunity(c.id) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name(language), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${c.memberCount} members · ${c.postCount} posts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val joined = c.id in state.joinedIds
                    OutlinedButton(
                        onClick = { viewModel.toggleJoin(c.id) },
                        shape = PillShape,
                        modifier = Modifier.height(34.dp),
                    ) {
                        Text(if (joined) "Joined" else "Join", style = MaterialTheme.typography.labelMedium)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityFeedScreen(
    communityId: String,
    language: String,
    currentUserId: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: CommunityFeedViewModel = viewModel(factory = CommunityFeedViewModel.factory(communityId)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var reportTarget by remember { mutableStateOf<Pair<ReportTargetType, String>?>(null) }

    LaunchedEffect(state.toast) {
        state.toast?.let { snackbar.showSnackbar(it); viewModel.clearToast() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.community?.name(language) ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).imePadding()) {
            state.error?.let { ErrorBanner(it, onRetry = viewModel::clearToast) }

            if (!state.isMember && !state.loading) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Join this group to post and comment.",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = viewModel::join, shape = PillShape) { Text("Join") }
                }
            }

            if (state.posts.isEmpty() && !state.loading) {
                Box(Modifier.weight(1f)) {
                    EmptyState(
                        icon = Icons.Outlined.Groups,
                        title = "Abhi koi post nahi hai",
                        body = "Pehli baat aap shuru karein. Apne ilaake ke kabutarbaazon se baat karein.",
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(state.posts, key = { it.id }) { post ->
                        var menuOpen by remember { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "@${post.author?.username ?: "kabutarbaaz"}",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onOpenProfile(post.authorId) },
                                )
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                                    }
                                    DropdownMenu(menuOpen, { menuOpen = false }) {
                                        if (post.authorId == currentUserId) {
                                            DropdownMenuItem(
                                                text = { Text("Delete") },
                                                onClick = { menuOpen = false; viewModel.deletePost(post.id) },
                                            )
                                        } else {
                                            DropdownMenuItem(
                                                text = { Text("Report this post") },
                                                onClick = {
                                                    menuOpen = false
                                                    reportTarget = ReportTargetType.Post to post.id
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Block this person") },
                                                onClick = { menuOpen = false; viewModel.blockAuthor(post.authorId) },
                                            )
                                        }
                                    }
                                }
                            }
                            Text(post.body, style = MaterialTheme.typography.bodyLarge)
                            Row(
                                Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val liked = post.id in state.likedIds
                                Row(
                                    Modifier.clickable { viewModel.toggleLike(post.id) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = "Like",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (liked) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.size(5.dp))
                                    Text("${post.likeCount}", style = MaterialTheme.typography.labelMedium)
                                }
                                Row(
                                    Modifier.clickable { viewModel.openComments(post.id) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.Comment,
                                        contentDescription = "Comments",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.size(5.dp))
                                    Text("${post.commentCount}", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            if (state.isMember) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = state.draft,
                        onValueChange = viewModel::onDraft,
                        placeholder = { Text("Kuch likhein...") },
                        shape = PillShape,
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = viewModel::post,
                        enabled = state.draft.isNotBlank() && !state.posting,
                        shape = PillShape,
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("Post") }
                }
            }
        }
    }

    state.commentsFor?.let {
        CommentsSheet(
            comments = state.comments.map { c ->
                CommentRow(c.id, c.author?.username ?: "kabutarbaaz", c.body)
            },
            onDismiss = viewModel::closeComments,
            onSend = viewModel::addComment,
            onReportComment = { id, reason, note ->
                viewModel.report(ReportTargetType.PostComment, id, reason, note)
            },
        )
    }

    reportTarget?.let { (type, id) ->
        ReportSheet(
            title = "Report this post",
            onDismiss = { reportTarget = null },
            onSubmit = { r, n -> reportTarget = null; viewModel.report(type, id, r, n) },
        )
    }
}
