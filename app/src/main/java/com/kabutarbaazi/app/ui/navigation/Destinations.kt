package com.kabutarbaazi.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Storefront
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.kabutarbaazi.app.R

/**
 * Four tabs, and a contextual FAB rather than a fifth "+" tab. A tab that is not a destination
 * breaks back-stack restoration and leaves NavigationBarItem with no sensible selected state.
 * Profile lives behind the avatar in the top bar.
 */
enum class TopDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Bazaar("bazaar", R.string.tab_bazaar, Icons.Outlined.Storefront),
    Reels("reels", R.string.tab_reels, Icons.Outlined.PlayCircleOutline),
    Groups("communities", R.string.tab_groups, Icons.Outlined.Groups),
    Chats("chats", R.string.tab_chats, Icons.Outlined.ChatBubbleOutline),
}

object Routes {
    const val LISTING_DETAIL = "listing/{listingId}"
    const val LISTING_NEW = "listing/new"
    const val SAVED = "saved"
    const val REEL_NEW = "reel/new"
    const val COMMUNITY = "community/{communityId}"
    const val POST_NEW = "community/{communityId}/post/new"
    const val CHAT = "chat/{conversationId}"
    const val PROFILE = "profile/{userId}"
    const val SETTINGS = "settings"
    const val BLOCKED = "settings/blocked"
    const val DELETE_ACCOUNT = "settings/delete"
    const val MODERATION = "admin/moderation"

    fun listing(id: String) = "listing/$id"
    fun community(id: String) = "community/$id"
    fun postNew(communityId: String) = "community/$communityId/post/new"
    fun chat(id: String) = "chat/$id"
    fun profile(id: String) = "profile/$id"
}
