package com.kabutarbaazi.app.ui.screens.reels

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.kabutarbaazi.app.KabutarBaaziApp
import com.kabutarbaazi.app.player.ReelPlayerPool
import com.kabutarbaazi.app.ui.components.CommentRow
import com.kabutarbaazi.app.ui.components.CommentsSheet
import com.kabutarbaazi.app.ui.components.EmptyState
import com.kabutarbaazi.app.ui.components.ReportSheet
import com.kabutarbaazi.app.ui.theme.PillShape
import com.kabutarbaazi.app.ui.theme.ReelOnVideo
import com.kabutarbaazi.app.ui.theme.ReelScrim
import com.kabutarbaazi.domain.feed.ReelWindow
import com.kabutarbaazi.domain.model.ReportTargetType

@UnstableApi
@Composable
fun ReelsScreen(
    currentUserId: String,
    onUpload: () -> Unit,
    onOpenProfile: (String) -> Unit,
    viewModel: ReelsViewModel = viewModel(factory = ReelsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val container = (context.applicationContext as KabutarBaaziApp).container

    // The pool lives here, not in the ViewModel. A player held across configuration changes
    // leaks its Surface, and that is the classic reels memory bug.
    val pool = remember { ReelPlayerPool(context, container.reelCache) }
    DisposableEffect(Unit) { onDispose { pool.release() } }

    val pagerState = rememberPagerState { state.reels.size }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> pool.pauseAll()
                Lifecycle.Event.ON_RESUME -> if (state.reels.isNotEmpty()) pool.playOnly(pagerState.settledPage)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // settledPage, NOT currentPage. currentPage updates mid-fling, so a fast flick through ten
    // pages would fire ten bind-and-play cycles and whichever won the race is what you hear.
    LaunchedEffect(pagerState.settledPage, state.reels.size) {
        if (state.reels.isEmpty()) return@LaunchedEffect
        val window = ReelWindow.forSettled(pagerState.settledPage, state.reels.size)
        window.prepare.forEach { i ->
            state.reels.getOrNull(i)?.let { pool.bind(i, it.id, it.videoUrl) }
        }
        state.reels.getOrNull(window.play)?.let { pool.bind(window.play, it.id, it.videoUrl) }
        pool.playOnly(window.play)
        pool.setMuted(state.muted, window.play)
        viewModel.onSettled(pagerState.settledPage)
    }
    LaunchedEffect(state.muted) { pool.setMuted(state.muted, pagerState.settledPage) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = ReelOnVideo)
            }

            state.reels.isEmpty() -> EmptyState(
                icon = Icons.Outlined.PlayCircleOutline,
                title = if (state.tab == ReelsTab.Following) "Kuch nahi mila" else "Abhi koi reel nahi hai",
                body = if (state.tab == ReelsTab.Following) {
                    "Follow some keepers and their reels will show up here."
                } else {
                    "Apne kabutaron ki video daalein. Sabse pehli reel aap ki ho."
                },
                actionLabel = "Upload a reel",
                onAction = onUpload,
            )

            else -> VerticalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val reel = state.reels[page]
                // key() stops a recomposition from rebinding the player every frame.
                key(reel.id) {
                    ReelPage(
                        reel = reel,
                        pool = pool,
                        index = page,
                        isSettled = page == pagerState.settledPage,
                        liked = reel.id in state.likedIds,
                        following = reel.authorId in state.followingIds,
                        isOwn = reel.authorId == currentUserId,
                        muted = state.muted,
                        onToggleMute = viewModel::toggleMute,
                        onLike = { viewModel.toggleLike(reel.id) },
                        onFollow = { viewModel.toggleFollow(reel.authorId) },
                        onComments = { viewModel.openComments(reel.id) },
                        onOpenProfile = { onOpenProfile(reel.authorId) },
                        onReport = { r, n -> viewModel.report(ReportTargetType.Reel, reel.id, r, n) },
                        onBlock = { viewModel.blockAuthor(reel.authorId) },
                        onDelete = { viewModel.deleteOwn(reel.id) },
                    )
                }
            }
        }

        // Tabs float over the video rather than taking a bar of their own.
        Row(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ReelsTab.entries.forEach { tab ->
                Text(
                    if (tab == ReelsTab.ForYou) "For you" else "Following",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.tab == tab) ReelOnVideo else ReelOnVideo.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { viewModel.switchTab(tab) }
                        .padding(vertical = 4.dp),
                )
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
                viewModel.report(ReportTargetType.ReelComment, id, reason, note)
            },
        )
    }
}

@UnstableApi
@Composable
private fun ReelPage(
    reel: com.kabutarbaazi.domain.model.Reel,
    pool: ReelPlayerPool,
    index: Int,
    isSettled: Boolean,
    liked: Boolean,
    following: Boolean,
    isOwn: Boolean,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onLike: () -> Unit,
    onFollow: () -> Unit,
    onComments: () -> Unit,
    onOpenProfile: () -> Unit,
    onReport: (com.kabutarbaazi.domain.model.ReportReason, String?) -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit,
) {
    var firstFrameRendered by remember(reel.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        // The thumbnail sits UNDER the video and is hidden only once a real frame arrives,
        // which removes the black flash between pages.
        if (!firstFrameRendered) {
            AsyncImage(
                model = reel.thumbUrl,
                contentDescription = reel.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view ->
                val player = pool.playerFor(index)
                if (view.player !== player) view.player = player
            },
            onRelease = { view ->
                // Detaching is what actually silences a page that has scrolled away.
                view.player = null
            },
            modifier = Modifier.fillMaxSize(),
        )

        DisposableEffect(reel.id, isSettled) {
            val player = pool.playerFor(index)
            val listener = object : Player.Listener {
                override fun onRenderedFirstFrame() { firstFrameRendered = true }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }

        // Tap anywhere to mute. Discoverable, and the whole surface is the target.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleMute,
                ),
        )

        if (muted) {
            Icon(
                Icons.Filled.VolumeOff,
                contentDescription = "Muted",
                tint = ReelOnVideo,
                modifier = Modifier.align(Alignment.Center).size(52.dp),
            )
        }

        // Right rail
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val likeScale by animateFloatAsState(
                targetValue = if (liked) 1.15f else 1f,
                animationSpec = spring(dampingRatio = 0.35f, stiffness = 400f),
                label = "likeScale",
            )
            RailAction(
                icon = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = reel.likeCount.toString(),
                tint = if (liked) MaterialTheme.colorScheme.primary else ReelOnVideo,
                modifier = Modifier.scale(likeScale),
                onClick = onLike,
            )
            RailAction(
                icon = Icons.AutoMirrored.Outlined.Comment,
                label = reel.commentCount.toString(),
                onClick = onComments,
            )
            Box {
                RailAction(icon = Icons.Outlined.MoreVert, label = "", onClick = { menuOpen = true })
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    if (isOwn) {
                        DropdownMenuItem(
                            text = { Text("Delete this reel") },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Report this reel") },
                            onClick = { menuOpen = false; reportOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Block this person") },
                            onClick = { menuOpen = false; onBlock() },
                        )
                    }
                }
            }
        }

        // Bottom-left author + caption over a scrim so white text stays readable on any video.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.75f)
                .background(ReelScrim.copy(alpha = 0.45f))
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "@${reel.author?.username ?: "kabutarbaaz"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = ReelOnVideo,
                    modifier = Modifier.clickable(onClick = onOpenProfile),
                )
                if (!isOwn) {
                    Spacer(Modifier.size(10.dp))
                    OutlinedButton(
                        onClick = onFollow,
                        shape = PillShape,
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text(
                            if (following) "Following" else "Follow",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            reel.caption?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReelOnVideo,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    if (reportOpen) {
        ReportSheet(
            title = "Report this reel",
            onDismiss = { reportOpen = false },
            onSubmit = { r, n -> reportOpen = false; onReport(r, n) },
        )
    }
}

@Composable
private fun RailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = ReelOnVideo,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
        }
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ReelOnVideo)
        }
    }
}
