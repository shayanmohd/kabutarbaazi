package com.kabutarbaazi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kabutarbaazi.app.ui.theme.CardShape
import com.kabutarbaazi.app.ui.theme.InnerShape
import com.kabutarbaazi.app.ui.theme.PriceStyle
import com.kabutarbaazi.app.ui.theme.ReelScrim
import com.kabutarbaazi.domain.format.PriceFormat
import com.kabutarbaazi.domain.model.Listing

/**
 * One pigeon in the feed. Photo first: buyers scan images, not text.
 * Price uses tabular figures so amounts line up down the column.
 */
@Composable
fun ListingCard(
    listing: Listing,
    saved: Boolean,
    onClick: () -> Unit,
    onToggleSave: () -> Unit,
    breedName: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(CardShape)
            .clickable(onClick = onClick)
            .padding(bottom = 10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(InnerShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val cover = listing.coverUrl
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = listing.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Outlined.Photo,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                )
            }

            if (listing.media.any { it.isVideo }) {
                Icon(
                    Icons.Outlined.PlayCircle,
                    contentDescription = "Has a video",
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).size(20.dp),
                )
            }

            // A bare white icon disappears on a pale photo. The scrim guarantees contrast
            // whatever the image behind it looks like.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ReelScrim.copy(alpha = 0.35f))
                    .clickable(onClick = onToggleSave),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (saved) "Remove from saved" else "Save this ad",
                    tint = if (saved) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(19.dp),
                )
            }

            if (listing.isSold) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        "SOLD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        // Fixed-height text block.
        //
        // Without it the grid goes ragged: Nastaliq sits on a ~1.9x line height, so an Urdu
        // title occupies visibly more vertical space than the same title in Latin and the two
        // columns stop lining up. Reserving the space keeps rows aligned in every script.
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 82.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                PriceFormat.format(listing.priceMinor, listing.currency),
                style = PriceStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                listing.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(breedName, listing.city).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
