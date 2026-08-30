package com.kabutarbaazi.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.kabutarbaazi.app.locale.AppLocale
import com.kabutarbaazi.app.ui.navigation.Routes
import com.kabutarbaazi.app.ui.navigation.TopDestination
import com.kabutarbaazi.app.ui.screens.admin.ModerationQueueScreen
import com.kabutarbaazi.app.ui.screens.chat.ChatScreen
import com.kabutarbaazi.app.ui.screens.chat.ConversationsScreen
import com.kabutarbaazi.app.ui.screens.community.CommunitiesScreen
import com.kabutarbaazi.app.ui.screens.community.CommunityFeedScreen
import com.kabutarbaazi.app.ui.screens.market.CreateListingScreen
import com.kabutarbaazi.app.ui.screens.market.ListingDetailScreen
import com.kabutarbaazi.app.ui.screens.market.MarketScreen
import com.kabutarbaazi.app.ui.screens.market.SavedListingsScreen
import com.kabutarbaazi.app.ui.screens.profile.AccountScreen
import com.kabutarbaazi.app.ui.screens.profile.ProfileScreen
import com.kabutarbaazi.app.ui.screens.profile.SettingsScreen
import com.kabutarbaazi.app.ui.screens.reels.CreateReelScreen
import com.kabutarbaazi.app.ui.screens.reels.ReelsScreen
import com.kabutarbaazi.app.ui.theme.LocalReducedMotion

/**
 * The signed-in shell: four tabs, a contextual FAB, and a NavHost for everything reached from
 * them. There is deliberately no fifth "+" tab, because a tab that is not a destination breaks
 * back-stack restoration and leaves NavigationBarItem with no sensible selected state.
 */
@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KabutarRoot(
    isAdmin: Boolean,
    currentUserId: String,
    onSignOut: () -> Unit,
) {
    val nav = rememberNavController()
    var selected by remember { mutableStateOf(TopDestination.Bazaar) }
    val reducedMotion = LocalReducedMotion.current
    val language = AppLocale.current()

    // Full-bleed screens draw their own chrome over the video.
    var overlayRoute by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            if (overlayRoute == null && selected != TopDestination.Reels) {
                TopAppBar(
                    title = { Text(stringResource(selected.labelRes)) },
                    actions = {
                        if (selected == TopDestination.Bazaar) {
                            IconButton(onClick = { overlayRoute = Routes.SAVED }) {
                                Icon(Icons.Outlined.Bookmark, contentDescription = "Saved ads")
                            }
                        }
                        IconButton(onClick = { overlayRoute = Routes.profile(currentUserId) }) {
                            Icon(Icons.Outlined.AccountCircle, contentDescription = "My profile")
                        }
                        IconButton(onClick = { overlayRoute = Routes.SETTINGS }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (overlayRoute == null) {
                NavigationBar {
                    TopDestination.entries.forEach { d ->
                        NavigationBarItem(
                            selected = selected == d,
                            onClick = { selected = d },
                            icon = { Icon(d.icon, contentDescription = stringResource(d.labelRes)) },
                            label = { Text(stringResource(d.labelRes)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // Reels deliberately has no FAB. Its own like/comment/more rail is anchored bottom-end
            // too, so a scaffold FAB lands directly on top of the comment button. Reels carries its
            // upload action in its top bar instead.
            if (overlayRoute == null && selected == TopDestination.Bazaar) {
                FloatingActionButton(
                    onClick = { overlayRoute = Routes.LISTING_NEW },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Post an ad")
                }
            }
        },
    ) { inner ->
        // Reels draw edge to edge under their own chrome; every other tab respects the scaffold insets.
        val contentPadding = if (selected == TopDestination.Reels && overlayRoute == null) {
            androidx.compose.foundation.layout.PaddingValues(0.dp)
        } else {
            inner
        }
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            val route = overlayRoute
            if (route == null) {
                // Crossfade rather than a slide: a slide must pick a direction, and the correct
                // direction inverts under Urdu.
                Crossfade(
                    targetState = selected,
                    animationSpec = tween(if (reducedMotion) 0 else 150),
                    label = "tab",
                ) { d ->
                    when (d) {
                        TopDestination.Bazaar -> MarketScreen(
                            onOpenListing = { overlayRoute = Routes.listing(it) },
                            onPostAd = { overlayRoute = Routes.LISTING_NEW },
                            language = language,
                        )
                        TopDestination.Reels -> ReelsScreen(
                            currentUserId = currentUserId,
                            onUpload = { overlayRoute = Routes.REEL_NEW },
                            onOpenProfile = { overlayRoute = Routes.profile(it) },
                        )
                        TopDestination.Groups -> CommunitiesScreen(
                            language = language,
                            onOpenCommunity = { overlayRoute = Routes.community(it) },
                        )
                        TopDestination.Chats -> ConversationsScreen(
                            currentUserId = currentUserId,
                            onOpenChat = { overlayRoute = Routes.chat(it) },
                        )
                    }
                }
            } else {
                Overlay(
                    route = route,
                    language = language,
                    currentUserId = currentUserId,
                    isAdmin = isAdmin,
                    onClose = { overlayRoute = null },
                    onNavigate = { overlayRoute = it },
                    onSignedOut = onSignOut,
                )
            }
        }
    }
}

@UnstableApi
@Composable
private fun Overlay(
    route: String,
    language: String,
    currentUserId: String,
    isAdmin: Boolean,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
    onSignedOut: () -> Unit,
) {
    when {
        route == Routes.LISTING_NEW -> CreateListingScreen(
            language = language,
            onBack = onClose,
            onCreated = { onNavigate(Routes.listing(it)) },
        )
        route == Routes.REEL_NEW -> CreateReelScreen(onBack = onClose, onCreated = onClose)
        route == Routes.SAVED -> SavedListingsScreen(
            language = language,
            onBack = onClose,
            onOpenListing = { onNavigate(Routes.listing(it)) },
        )
        route == Routes.SETTINGS -> SettingsScreen(
            isAdmin = isAdmin,
            onBack = onClose,
            onOpenModeration = { onNavigate(Routes.MODERATION) },
            onOpenAccount = { onNavigate(Routes.DELETE_ACCOUNT) },
            onSignedOut = onSignedOut,
            onOpenLegal = { onNavigate("legal/$it") },
        )
        route == Routes.DELETE_ACCOUNT -> AccountScreen(
            onBack = { onNavigate(Routes.SETTINGS) },
            onSignedOut = onSignedOut,
        )
        route == Routes.MODERATION -> ModerationQueueScreen(onBack = { onNavigate(Routes.SETTINGS) })
        route.startsWith("listing/") -> ListingDetailScreen(
            listingId = route.removePrefix("listing/"),
            language = language,
            onBack = onClose,
            onOpenChat = { onNavigate(Routes.chat(it)) },
            onOpenProfile = { onNavigate(Routes.profile(it)) },
        )
        route.startsWith("community/") -> CommunityFeedScreen(
            communityId = route.removePrefix("community/"),
            language = language,
            currentUserId = currentUserId,
            onBack = onClose,
            onOpenProfile = { onNavigate(Routes.profile(it)) },
        )
        route.startsWith("chat/") -> ChatScreen(
            conversationId = route.removePrefix("chat/"),
            currentUserId = currentUserId,
            counterpartName = "Chat",
            counterpartId = "",
            onBack = onClose,
        )
        route.startsWith("profile/") -> ProfileScreen(
            userId = route.removePrefix("profile/"),
            language = language,
            onBack = onClose,
            onOpenListing = { onNavigate(Routes.listing(it)) },
        )
        route.startsWith("legal/") -> LegalScreen(doc = route.removePrefix("legal/"), onBack = onClose)
        else -> onClose()
    }
}

