package com.wildtrail.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.SurfaceType
import com.wildtrail.app.util.formatHikeDate

@Composable
fun HikeCard(
    hike: HikeLog,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLiked: Boolean = false,
    onLikeClick: (() -> Unit)? = null,
    onCreatorClick: ((String) -> Unit)? = null,
    currentUserUid: String? = null,
    currentUserProfilePictureUrl: String? = null,
) {
    val isOwnHike = currentUserUid != null && currentUserUid == hike.creatorFirebaseUid
    val avatarUrl = if (isOwnHike && currentUserProfilePictureUrl != null) {
        currentUserProfilePictureUrl
    } else {
        hike.creatorProfilePictureUrl
    }
    val displayName = when {
        isOwnHike -> "You"
        hike.creatorUsername.isNotBlank() -> hike.creatorUsername
        else -> "Hiker"
    }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = onCreatorClick != null) {
                        onCreatorClick?.invoke(hike.creatorFirebaseUid)
                    },
            ) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(28.dp).clip(CircleShape),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LikeControl(isLiked = isLiked, count = hike.likesCount, onClick = onLikeClick)
            }

            Spacer(Modifier.height(8.dp))

            if (hike.coverPhotoUrl != null) {
                AsyncImage(
                    model = hike.coverPhotoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(184.dp)
                        .clip(RoundedCornerShape(18.dp)),
                )
                Spacer(Modifier.height(12.dp))
            }

            Text(text = hike.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Recorded ${formatHikeDate(hike.endedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            hike.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (hike.routeCoordinates.size >= 2) {
                Spacer(Modifier.height(10.dp))
                RouteThumbnail(
                    points = hike.routeCoordinates,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Stat(Icons.Filled.Place, "%.1f km".format(hike.lengthKm))
                Stat(Icons.Filled.Timer, formatDuration(hike.durationSeconds))
                Stat(Icons.Filled.TrendingUp, "${hike.elevationGainMeters} m")
            }

            Spacer(Modifier.height(8.dp))

            if (hike.reviewCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarRow(rating = hike.averageRating)
                    Text(
                        "  %.1f / 5  (%d)".format(hike.averageRating, hike.reviewCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Text(
                    "No reviews yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CharChip("Diff ${hike.difficultyLevel}/5", Modifier.weight(1f))
                CharChip("Mud ${hike.mudRisk}/5", Modifier.weight(1f))
                CharChip("Path ${hike.pathClarity}/5", Modifier.weight(1f))
                CharChip(hike.surfaceType.shortLabel(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LikeControl(isLiked: Boolean, count: Int, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(50)
    val container = if (isLiked) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isLiked) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = shape,
        color = container,
        modifier = if (onClick != null) {
            Modifier.clip(shape).clickable(onClick = onClick)
        } else {
            Modifier
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (isLiked) "Unlike" else "Like",
                tint = if (isLiked) MaterialTheme.colorScheme.primary else contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "$count",
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun CharChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

private fun SurfaceType.shortLabel(): String = when (this) {
    SurfaceType.MOUNTAIN -> "🏔 Mountain"
    SurfaceType.FOREST -> "🌲 Forest"
    SurfaceType.COASTAL -> "🌊 Coastal"
    SurfaceType.URBAN -> "🏙 Urban"
    SurfaceType.DESERT -> "🏜 Desert"
    SurfaceType.OTHER -> "Other"
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
