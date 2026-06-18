/*
 * CleanSweep
 * Copyright (c) 2025 LoopOtto
 *
 * Organize-screen layout modeled after the reference card-stack UI:
 * album header, media counter, action bar, folder transfer row, and help dialog.
 */

package com.cleansweep.ui.screens.swiper

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cleansweep.R
import com.cleansweep.data.model.MediaItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val OrganizeGreen = Color(0xFF4CAF50)
private val OrganizeRed = Color(0xFFE53935)

@Composable
internal fun OrganizeTopBar(
    albumName: String,
    deletePoolCount: Int,
    onClose: () -> Unit,
    onDeletePoolClick: () -> Unit,
    deletePoolSwipeProgress: Float,
    onTrashIconCenterInWindow: (Offset) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val trashScale = 1f + 0.22f * deletePoolSwipeProgress.coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.navigate_back),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = albumName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        BadgedBox(
            badge = {
                if (deletePoolCount > 0) {
                    Badge(
                        containerColor = OrganizeRed,
                        contentColor = Color.White
                    ) {
                        Text(deletePoolCount.toString(), fontSize = 11.sp)
                    }
                }
            }
        ) {
            IconButton(
                onClick = onDeletePoolClick,
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        val center = Offset(
                            coordinates.size.width / 2f,
                            coordinates.size.height / 2f
                        )
                        onTrashIconCenterInWindow(coordinates.localToWindow(center))
                    }
                    .graphicsLayer {
                        scaleX = trashScale
                        scaleY = trashScale
                    }
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.organize_delete_pool),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
internal fun OrganizeMediaMetaLine(
    currentIndex: Int,
    totalCount: Int,
    dateTimestampMs: Long,
    modifier: Modifier = Modifier
) {
    val dateText = remember(dateTimestampMs) {
        formatMediaDate(dateTimestampMs)
    }
    Text(
        text = stringResource(
            R.string.organize_media_counter,
            currentIndex.coerceAtLeast(1),
            totalCount.coerceAtLeast(1),
            dateText
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
    )
}

@Composable
internal fun OrganizeActionBar(
    onNext: () -> Unit,
    onUndoOrHelp: () -> Unit,
    showUndo: Boolean,
    onShare: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OrganizeActionItem(
            icon = {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = OrganizeGreen,
                    modifier = Modifier.size(18.dp)
                )
            },
            label = stringResource(R.string.organize_action_next),
            labelColor = OrganizeGreen,
            inlineLabel = true,
            onClick = onNext
        )
        OrganizeActionItem(
            icon = {
                if (showUndo) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.undo_last_action),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                "?",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            label = "",
            onClick = onUndoOrHelp
        )
        OrganizeActionItem(
            icon = {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            },
            label = "",
            onClick = onShare
        )
        OrganizeActionItem(
            icon = {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = OrganizeRed,
                    modifier = Modifier.size(18.dp)
                )
            },
            label = stringResource(R.string.organize_action_clear),
            labelColor = OrganizeRed,
            inlineLabel = true,
            onClick = onClear
        )
    }
}

@Composable
private fun OrganizeActionItem(
    icon: @Composable () -> Unit,
    label: String,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    inlineLabel: Boolean = false,
    onClick: () -> Unit
) {
    val itemModifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 6.dp, vertical = if (inlineLabel) 1.dp else 3.dp)

    if (inlineLabel && label.isNotEmpty()) {
        Row(
            modifier = itemModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                fontWeight = FontWeight.Medium
            )
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = itemModifier
        ) {
            icon()
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
internal fun OrganizeFolderTransferSection(
    targetFolders: List<Pair<String, String>>,
    currentItem: MediaItem?,
    targetFavorites: Set<String>,
    onSelectFolder: (String) -> Unit,
    onCreateNewAlbum: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.organize_transfer_to_album),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(targetFolders, key = { it.first }) { (folderId, folderName) ->
                    OrganizeFolderColumn(
                        folderId = folderId,
                        folderName = folderName,
                        isFavorite = folderId in targetFavorites,
                        currentItem = currentItem,
                        onSelectFolder = onSelectFolder
                    )
                }
                item(key = "add_folder") {
                    OrganizeAddFolderColumn(onClick = onCreateNewAlbum)
                }
            }
        }
    }
}

@Composable
private fun OrganizeFolderColumn(
    folderId: String,
    folderName: String,
    isFavorite: Boolean,
    currentItem: MediaItem?,
    onSelectFolder: (String) -> Unit
) {
    val isEnabled = remember(currentItem?.id, folderId) {
        val currentItemPath = currentItem?.id ?: return@remember true
        val parentDirectory = try {
            java.io.File(currentItemPath).parent
        } catch (_: Exception) {
            null
        }
        parentDirectory != folderId
    }
    val alpha = if (isEnabled) 1f else 0.45f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = isEnabled) { onSelectFolder(folderId) }
            .padding(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = folderName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (isFavorite) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun OrganizeAddFolderColumn(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    "+",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.add_target_folder),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun OrganizeUsageDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.organize_usage_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                UsageInstructionRow(
                    icon = {
                        Icon(Icons.Default.Check, null, tint = OrganizeGreen, modifier = Modifier.size(22.dp))
                    },
                    text = stringResource(R.string.organize_usage_next)
                )
                UsageInstructionRow(
                    icon = {
                        Icon(Icons.Default.Close, null, tint = OrganizeRed, modifier = Modifier.size(22.dp))
                    },
                    text = stringResource(R.string.organize_usage_delete)
                )
                UsageInstructionRow(
                    icon = {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    text = stringResource(R.string.organize_usage_move_folder)
                )
                UsageInstructionRow(
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    text = stringResource(R.string.organize_usage_undo)
                )
                Text(
                    text = stringResource(R.string.organize_usage_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}

@Composable
private fun UsageInstructionRow(
    icon: @Composable () -> Unit,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatMediaDate(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd h:mm a", Locale.getDefault())
        formatter.format(Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()))
    } catch (_: Exception) {
        ""
    }
}