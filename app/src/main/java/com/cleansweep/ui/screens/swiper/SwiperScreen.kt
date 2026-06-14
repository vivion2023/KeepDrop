/*
 * CleanSweep
 * Copyright (c) 2025 LoopOtto
 *
 * This program is free software: you can redistribute it and/or modify
 * the Free Software Foundation, either version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.cleansweep.ui.screens.swiper

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.TextureView
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cleansweep.R
import com.cleansweep.data.model.MediaItem
import com.cleansweep.data.repository.FolderBarLayout
import com.cleansweep.data.repository.FolderNameLayout
import com.cleansweep.data.repository.SwipeDownAction
import com.cleansweep.data.repository.SwipeSensitivity
import com.cleansweep.ui.components.AppDropdownMenu
import com.cleansweep.ui.components.AppMenuDivider
import com.cleansweep.ui.components.FolderSearchDialog
import com.cleansweep.ui.components.RenameFolderDialog
import com.cleansweep.ui.theme.AppTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ButtonDefaults

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SwiperScreen(
    windowSizeClass: WindowSizeClass,
    bucketIds: List<String>,
    onNavigateUp: () -> Unit,
    onNavigateUpAndReset: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDuplicates: () -> Unit = {},
    viewModel: SwiperViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val swipeSensitivity by viewModel.swipeSensitivity.collectAsState()
    val swipeDownAction by viewModel.swipeDownAction.collectAsState()
    val folderBarLayout by viewModel.folderBarLayout.collectAsState()
    val folderNameLayout by viewModel.folderNameLayout.collectAsState()
    val skipPartialExpansion by viewModel.skipPartialExpansion.collectAsState()
    val screenshotDeletesVideo by viewModel.screenshotDeletesVideo.collectAsState()
    val addFolderFocusTarget by viewModel.addFolderFocusTarget.collectAsState()
    val addFavoriteToTargetByDefault by viewModel.addFavoriteToTargetByDefault.collectAsState()
    val hintOnExistingFolderName by viewModel.hintOnExistingFolderName.collectAsState()
    val appContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isExpandedScreen = windowSizeClass.widthSizeClass > WindowWidthSizeClass.Compact
    val folderSearchState by viewModel.folderSearchManager.state.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    BackHandler {
        if (uiState.isSortingComplete && uiState.pendingChanges.isEmpty()) {
            onNavigateUpAndReset()
        } else {
            viewModel.onNavigateUp()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collectLatest { event ->
            when (event) {
                is NavigationEvent.NavigateUp -> onNavigateUp()
            }
        }
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(appContext).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }
    LaunchedEffect(uiState.videoPlaybackSpeed) {
        exoPlayer.setPlaybackSpeed(uiState.videoPlaybackSpeed)
    }
    LaunchedEffect(uiState.isVideoMuted) {
        exoPlayer.volume = if (uiState.isVideoMuted) 0f else 1f
    }
    uiState.showRenameDialogForPath?.let { path ->
        val folderName = uiState.targetFolders.find { it.first == path }?.second
        if (folderName != null) {
            RenameFolderDialog(
                currentFolderName = folderName,
                onConfirm = { newName ->
                    viewModel.renameTargetFolder(path, newName)
                },
                onDismiss = { viewModel.dismissRenameDialog() }
            )
        }
    }
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (exoPlayer.isPlaying) {
                        viewModel.saveVideoPlaybackPosition(exoPlayer.currentPosition)
                        exoPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (uiState.currentItem?.isVideo == true) {
                        exoPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }
    LaunchedEffect(uiState.currentItem) {
        val currentItem = uiState.currentItem
        exoPlayer.playWhenReady = false
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        if (currentItem != null && currentItem.isVideo) {
            val exoMediaItem = ExoMediaItem.fromUri(currentItem.uri)
            exoPlayer.setMediaItem(exoMediaItem)
            exoPlayer.prepare()

            if (uiState.videoPlaybackPosition > 0L) {
                exoPlayer.seekTo(uiState.videoPlaybackPosition)
            }
            exoPlayer.playWhenReady = true
        }
    }
    LaunchedEffect(bucketIds) {
        viewModel.initializeMedia(bucketIds)
    }
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { message ->
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
            viewModel.toastMessageShown()
        }
    }
    if (uiState.showConfirmExitDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelExit() },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmExit() }
                ) {
                    Text(stringResource(R.string.cancel_all_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelExitAndShowSummary() }) {
                    Text(stringResource(R.string.review_changes))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") }, // Not sure
                navigationIcon = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            positioning = TooltipAnchorPosition.Above,
                            spacingBetweenTooltipAndAnchor = 4.dp
                        ),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.navigate_back)) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { viewModel.onNavigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                        }
                    }
                },
                actions = {
                    AnimatedVisibility(visible = uiState.toDelete.isNotEmpty()) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                positioning = TooltipAnchorPosition.Above,
                                spacingBetweenTooltipAndAnchor = 4.dp
                            ),
                            tooltip = { PlainTooltip { Text(stringResource(R.string.review_changes)) } },
                            state = rememberTooltipState()
                        ) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text(uiState.toDelete.size.toString())
                                    }
                                }
                            ) {
                                IconButton(onClick = viewModel::showSummarySheet) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.review_changes))
                                }
                            }
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            positioning = TooltipAnchorPosition.Above,
                            spacingBetweenTooltipAndAnchor = 4.dp
                        ),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.find_duplicates)) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = onNavigateToDuplicates) {
                            Icon(Icons.Default.ControlPointDuplicate, contentDescription = stringResource(R.string.find_duplicates))
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            positioning = TooltipAnchorPosition.Above,
                            spacingBetweenTooltipAndAnchor = 4.dp
                        ),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.settings)) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings)) }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                            if (uiState.isVideoMuted) {
                                val hasAudio = exoPlayer.currentTracks.isTypeSupported(C.TRACK_TYPE_AUDIO)
                                viewModel.toggleMute(hasAudio)
                            }
                            return@onKeyEvent true
                        }
                    }
                    false
                }
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.error != null -> ErrorMessage(message = uiState.error!!, onRetry = { viewModel.initializeMedia(bucketIds) })
                uiState.currentItem != null -> {
                    if (isExpandedScreen) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            MainContent(
                                modifier = Modifier
                                    .weight(1f)
                                    .zIndex(1f),
                                currentItem = uiState.currentItem!!,
                                previousItem = viewModel.getPreviousBrowsableItem(),
                                upcomingItems = viewModel.getUpcomingBrowsableItems(2),
                                exoPlayer = exoPlayer,
                                imageLoader = viewModel.imageLoader,
                                gifImageLoader = viewModel.gifImageLoader,
                                onSwipeLeft = viewModel::handleSwipeLeft,
                                onSwipeRight = viewModel::handleSwipeRight,
                                onSwipeDown = viewModel::handleSwipeDown,
                                onSwipeToDeletePool = viewModel::handleDelete,
                                sensitivity = swipeSensitivity,
                                swipeDownAction = swipeDownAction,
                                videoPlaybackSpeed = uiState.videoPlaybackSpeed,
                                onSetVideoPlaybackSpeed = viewModel::setPlaybackSpeed,
                                isVideoMuted = uiState.isVideoMuted,
                                onToggleMute = {
                                    val hasAudio = exoPlayer.currentTracks.isTypeSupported(C.TRACK_TYPE_AUDIO)
                                    viewModel.toggleMute(hasAudio)
                                },
                                onTap = {
                                    if (it.isVideo) {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    }
                                },
                                isPendingConversion = uiState.isCurrentItemPendingConversion,
                                screenshotDeletesVideo = screenshotDeletesVideo,
                                folderNameLayout = folderNameLayout,
                                fullScreenSwipe = uiState.fullScreenSwipe
                            )
                            Box(modifier = Modifier
                                .fillMaxHeight()
                                .width(340.dp)) {
                                BottomFolderBar(
                                    targetFolders = uiState.targetFolders,
                                    compactFoldersView = uiState.compactFoldersView,
                                    isFolderBarExpanded = uiState.isFolderBarExpanded,
                                    onSetExpanded = viewModel::setFolderBarExpanded,
                                    currentTheme = uiState.currentTheme,
                                    useLegacyFolderIcons = uiState.useLegacyFolderIcons,
                                    pendingChangesCount = uiState.pendingChanges.size,
                                    currentItem = uiState.currentItem,
                                    targetFavorites = uiState.targetFavorites,
                                    onSelectFolder = viewModel::moveToFolder,
                                    onLongPressFolder = viewModel::showFolderMenu,
                                    onCreateNewAlbum = viewModel::showAddTargetFolderDialog,
                                    onToggleExpansion = viewModel::toggleFolderBarExpansion,
                                    onKeep = viewModel::handleKeep,
                                    onDelete = viewModel::handleDelete,
                                    onShowSummary = viewModel::showSummarySheet,
                                    onUndo = viewModel::revertLastChange,
                                    onShare = viewModel::shareCurrentItem,
                                    layout = FolderBarLayout.VERTICAL,
                                    folderName = if (folderNameLayout == FolderNameLayout.BELOW) uiState.currentItem!!.bucketName else null
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            MainContent(
                                modifier = Modifier
                                    .weight(1f)
                                    .zIndex(1f),
                                currentItem = uiState.currentItem!!,
                                previousItem = viewModel.getPreviousBrowsableItem(),
                                upcomingItems = viewModel.getUpcomingBrowsableItems(2),
                                exoPlayer = exoPlayer,
                                imageLoader = viewModel.imageLoader,
                                gifImageLoader = viewModel.gifImageLoader,
                                onSwipeLeft = viewModel::handleSwipeLeft,
                                onSwipeRight = viewModel::handleSwipeRight,
                                onSwipeDown = viewModel::handleSwipeDown,
                                onSwipeToDeletePool = viewModel::handleDelete,
                                sensitivity = swipeSensitivity,
                                swipeDownAction = swipeDownAction,
                                videoPlaybackSpeed = uiState.videoPlaybackSpeed,
                                onSetVideoPlaybackSpeed = viewModel::setPlaybackSpeed,
                                isVideoMuted = uiState.isVideoMuted,
                                onToggleMute = {
                                    val hasAudio = exoPlayer.currentTracks.isTypeSupported(C.TRACK_TYPE_AUDIO)
                                    viewModel.toggleMute(hasAudio)
                                },
                                onTap = {
                                    if (it.isVideo) {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    }
                                },
                                isPendingConversion = uiState.isCurrentItemPendingConversion,
                                screenshotDeletesVideo = screenshotDeletesVideo,
                                folderNameLayout = folderNameLayout,
                                fullScreenSwipe = uiState.fullScreenSwipe
                            )
                            BottomFolderBar(
                                targetFolders = uiState.targetFolders,
                                compactFoldersView = uiState.compactFoldersView,
                                isFolderBarExpanded = uiState.isFolderBarExpanded,
                                onSetExpanded = viewModel::setFolderBarExpanded,
                                currentTheme = uiState.currentTheme,
                                useLegacyFolderIcons = uiState.useLegacyFolderIcons,
                                pendingChangesCount = uiState.pendingChanges.size,
                                currentItem = uiState.currentItem,
                                targetFavorites = uiState.targetFavorites,
                                onSelectFolder = viewModel::moveToFolder,
                                onLongPressFolder = viewModel::showFolderMenu,
                                onCreateNewAlbum = viewModel::showAddTargetFolderDialog,
                                onToggleExpansion = viewModel::toggleFolderBarExpansion,
                                onKeep = viewModel::handleKeep,
                                onDelete = viewModel::handleDelete,
                                onShowSummary = viewModel::showSummarySheet,
                                onUndo = viewModel::revertLastChange,
                                onShare = viewModel::shareCurrentItem,
                                layout = folderBarLayout,
                                folderName = if (folderNameLayout == FolderNameLayout.BELOW) uiState.currentItem!!.bucketName else null
                            )
                        }
                    }
                }
                uiState.isSortingComplete && uiState.pendingChanges.isEmpty() -> {
                    val rememberProcessedMedia by viewModel.rememberProcessedMedia.collectAsState()
                    AlreadyOrganizedDialog(
                        onSelectNewFolders = onNavigateUpAndReset,
                        showResetHistoryButton = rememberProcessedMedia,
                        onResetHistory = viewModel::resetProcessedMedia,
                        onResetSingleFolderHistory = viewModel::showForgetMediaInFolderDialog,
                        skippedCount = uiState.sessionSkippedMediaIds.size,
                        onReviewSkipped = viewModel::reviewSkippedItems,
                        onClose = { (appContext as? Activity)?.finish() }
                    )
                }
                else -> {
                    NoMoreItemsMessage(
                        pendingChanges = uiState.pendingChanges,
                        onShowSummarySheet = viewModel::showSummarySheet
                    )
                }
            }
            if (uiState.showAddTargetFolderDialog) {
                AddTargetFolderDialog(
                    folderSearchState = folderSearchState,
                    addFolderFocusTarget = addFolderFocusTarget,
                    addFavoriteToTargetByDefault = addFavoriteToTargetByDefault,
                    hintOnExistingFolderName = hintOnExistingFolderName,
                    currentItemPath = uiState.currentItem?.id,
                    targetFavorites = uiState.targetFavorites,
                    onDismissRequest = viewModel::dismissAddTargetFolderDialog,
                    onSearchQueryChange = viewModel.folderSearchManager::updateSearchQuery,
                    onPathSelected = viewModel::onPathSelected,
                    onSearchFocusChanged = viewModel::onSearchFocusChanged,
                    onResetFolderSelection = viewModel::resetFolderSelectionToDefault,
                    onConfirm = viewModel::confirmFolderSelection
                )
            }
            if (uiState.showForgetMediaSearchDialog) {
                var showConfirmDialog by remember { mutableStateOf(false) }
                val folderToForget = folderSearchState.browsePath
                FolderSearchDialog(
                    state = folderSearchState,
                    title = stringResource(R.string.forget_sorted_media_title),
                    searchLabel = stringResource(R.string.search_folder_reset_label),
                    confirmButtonText = stringResource(R.string.forget_action),
                    autoConfirmOnSelection = false,
                    onDismiss = viewModel::dismissForgetMediaSearchDialog,
                    onQueryChanged = viewModel.folderSearchManager::updateSearchQuery,
                    onFolderSelected = viewModel::onPathSelected,
                    onConfirm = {
                        if (folderToForget != null) {
                            showConfirmDialog = true
                        }
                    }
                )
                if (showConfirmDialog && folderToForget != null) {
                    AlertDialog(
                        onDismissRequest = { showConfirmDialog = false },
                        title = { Text(stringResource(R.string.forget_confirm_title)) },
                        text = { Text(stringResource(R.string.forget_confirm_body, File(folderToForget).name)) },
                        confirmButton = {
                            Button(onClick = {
                                viewModel.forgetSortedMediaInFolder(folderToForget)
                                showConfirmDialog = false
                                viewModel.dismissForgetMediaSearchDialog()
                            }) { Text(stringResource(R.string.confirm)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmDialog = false }) { Text(stringResource(R.string.cancel)) }
                        }
                    )
                }
            }
            FolderContextMenu(
                folderMenuState = uiState.folderMenuState,
                targetFavorites = uiState.targetFavorites,
                onDismiss = viewModel::dismissFolderMenu,
                onRename = viewModel::showRenameDialog,
                onToggleFavorite = viewModel::toggleTargetFavorite,
                onRemove = viewModel::removeTargetFolder
            )
        }
        if (uiState.showSummarySheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartialExpansion)
            val summaryListState = rememberLazyListState()
            val isAmoled = uiState.currentTheme == AppTheme.AMOLED
            val containerColor = if (isAmoled) {
                MaterialTheme.colorScheme.surface
            } else {
                BottomSheetDefaults.ContainerColor
            }
            val tonalElevation = if (isAmoled) 0.dp else BottomSheetDefaults.Elevation

            if (uiState.pendingChanges.isEmpty()) {
                LaunchedEffect(Unit) {
                    viewModel.dismissSummarySheet()
                }
            }

            ModalBottomSheet(
                onDismissRequest = viewModel::dismissSummarySheet,
                sheetState = sheetState,
                containerColor = containerColor,
                tonalElevation = tonalElevation,
                modifier = Modifier.fillMaxWidth(),
                contentWindowInsets = { WindowInsets(0) }
            ) {
                SummarySheet(
                    pendingChanges = uiState.pendingChanges,
                    toDelete = uiState.toDelete,
                    toKeep = uiState.toKeep,
                    toConvert = uiState.toConvert,
                    groupedMoves = uiState.groupedMoves,
                    isApplyingChanges = uiState.isApplyingChanges,
                    folderIdNameMap = uiState.folderIdToNameMap,
                    onDismiss = viewModel::dismissSummarySheet,
                    onConfirm = { if (!uiState.isApplyingChanges) viewModel.applyChanges() },
                    onResetChanges = viewModel::resetPendingChanges,
                    onRevertChange = viewModel::revertChange,
                    viewMode = uiState.summaryViewMode,
                    onToggleViewMode = viewModel::toggleSummaryViewMode,
                    applyChangesButtonLabel = stringResource(R.string.apply_changes_button),
                    cancelChangesButtonLabel = stringResource(R.string.cancel_all_changes),
                    sheetScrollState = summaryListState,
                    isMaximized = uiState.useFullScreenSummarySheet,
                    onDynamicHeightChange = { shouldBeMaximized ->
                        scope.launch {
                            if (shouldBeMaximized) {
                                if (sheetState.currentValue != SheetValue.Expanded) {
                                    sheetState.expand()
                                }
                            } else {
                                if (sheetState.currentValue == SheetValue.Expanded) {
                                    sheetState.show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun MainContent(
    modifier: Modifier = Modifier,
    currentItem: MediaItem,
    previousItem: MediaItem?,
    upcomingItems: List<MediaItem>,
    exoPlayer: ExoPlayer,
    imageLoader: ImageLoader,
    gifImageLoader: ImageLoader,
    onSwipeLeft: () -> Boolean,
    onSwipeRight: () -> Boolean,
    onSwipeDown: () -> Unit,
    onSwipeToDeletePool: () -> Unit,
    sensitivity: SwipeSensitivity,
    swipeDownAction: SwipeDownAction,
    videoPlaybackSpeed: Float,
    onSetVideoPlaybackSpeed: (Float) -> Unit,
    isVideoMuted: Boolean,
    onToggleMute: () -> Unit,
    onTap: (MediaItem) -> Unit,
    isPendingConversion: Boolean,
    screenshotDeletesVideo: Boolean,
    folderNameLayout: FolderNameLayout,
    fullScreenSwipe: Boolean
) {
    Column(modifier) {
        if (folderNameLayout == FolderNameLayout.ABOVE) {
            FolderNameHeader(currentItem.bucketName)
        }
        // Keying the MediaItemCard forces a full recomposition when the item ID changes,
        // preventing stale UI issues (e.g., image not updating after skip during swiping).
        key(currentItem.id) {
            MediaItemCard(
                item = currentItem,
                previousItem = previousItem,
                previewItems = upcomingItems,
                exoPlayer = exoPlayer,
                imageLoader = imageLoader,
                gifImageLoader = gifImageLoader,
                onSwipeLeft = onSwipeLeft,
                onSwipeRight = onSwipeRight,
                onSwipeDown = onSwipeDown,
                onSwipeToDeletePool = onSwipeToDeletePool,
                modifier = Modifier.weight(1f),
                sensitivity = sensitivity,
                swipeDownAction = swipeDownAction,
                videoPlaybackSpeed = videoPlaybackSpeed,
                onSetVideoPlaybackSpeed = onSetVideoPlaybackSpeed,
                isVideoMuted = isVideoMuted,
                onToggleMute = onToggleMute,
                onTap = onTap,
                isPendingConversion = isPendingConversion,
                screenshotDeletesVideo = screenshotDeletesVideo,
                fullScreenSwipe = fullScreenSwipe
            )
        }
    }
}

@Composable
private fun FolderNameHeader(folderName: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Text(
            text = folderName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(12.dp),
            textAlign = TextAlign.Center
        )
    }
}


@Composable
private fun FolderContextMenu(
    folderMenuState: FolderMenuState,
    targetFavorites: Set<String>,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    if (folderMenuState is FolderMenuState.Visible) {
        val isFavorite = folderMenuState.folderPath in targetFavorites
        AppDropdownMenu(
            expanded = true,
            onDismissRequest = onDismiss,
            offset = folderMenuState.pressOffset
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.rename)) },
                onClick = {
                    onRename(folderMenuState.folderPath)
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.Edit, null) }
            )
            DropdownMenuItem(
                text = { Text(if (isFavorite) stringResource(R.string.unfavorite) else stringResource(R.string.favorite)) },
                onClick = {
                    onToggleFavorite(folderMenuState.folderPath)
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.Star, null) }
            )
            AppMenuDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.remove_from_bar), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    onRemove(folderMenuState.folderPath)
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlBar(
    isExpanded: Boolean,
    showExpandButton: Boolean,
    hasPendingChanges: Boolean,
    onToggleExpansion: () -> Unit,
    onCreateNewAlbum: () -> Unit,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
    onShowSummary: () -> Unit,
    onUndo: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                positioning = TooltipAnchorPosition.Above,
                spacingBetweenTooltipAndAnchor = 4.dp
            ),
            tooltip = { PlainTooltip { Text(stringResource(R.string.add_target_folder)) } },
            state = rememberTooltipState()
        ) {
            IconButton(onClick = onCreateNewAlbum) {
                Icon(Icons.Default.CreateNewFolder, stringResource(R.string.add_target_folder))
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                    spacingBetweenTooltipAndAnchor = 4.dp
                ),
                tooltip = { PlainTooltip { Text(stringResource(R.string.share)) } },
                state = rememberTooltipState()
            ) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, stringResource(R.string.share))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                    spacingBetweenTooltipAndAnchor = 4.dp
                ),
                tooltip = { PlainTooltip { Text(stringResource(R.string.keep)) } },
                state = rememberTooltipState()
            ) {
                FilledIconButton(
                    onClick = onKeep,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Check, stringResource(R.string.keep))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                    spacingBetweenTooltipAndAnchor = 4.dp
                ),
                tooltip = { PlainTooltip { Text(stringResource(R.string.delete)) } },
                state = rememberTooltipState()
            ) {
                FilledIconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Default.Close, stringResource(R.string.delete))
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            AnimatedVisibility(visible = hasPendingChanges) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        positioning = TooltipAnchorPosition.Above,
                        spacingBetweenTooltipAndAnchor = 4.dp
                    ),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.review_changes)) } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = onShowSummary) {
                        Icon(Icons.Default.Preview, stringResource(R.string.review_changes))
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            AnimatedVisibility(visible = hasPendingChanges) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        positioning = TooltipAnchorPosition.Above,
                        spacingBetweenTooltipAndAnchor = 4.dp
                    ),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.undo_last_action)) } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = onUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo_last_action))
                    }
                }
            }
        }

        if (showExpandButton) {
            val rotationAngle by animateFloatAsState(
                targetValue = if (isExpanded) 180f else 0f,
                label = "expand_icon_rotation"
            )
            val contentDesc = if (isExpanded) stringResource(R.string.collapse) else stringResource(R.string.expand)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                    spacingBetweenTooltipAndAnchor = 4.dp
                ),
                tooltip = { PlainTooltip { Text(contentDesc) } },
                state = rememberTooltipState()
            ) {
                IconButton(onClick = onToggleExpansion) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = contentDesc,
                        modifier = Modifier.rotate(rotationAngle)
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BottomFolderBar(
    targetFolders: List<Pair<String, String>>,
    compactFoldersView: Boolean,
    isFolderBarExpanded: Boolean,
    onSetExpanded: (Boolean) -> Unit,
    currentTheme: AppTheme,
    useLegacyFolderIcons: Boolean,
    pendingChangesCount: Int,
    currentItem: MediaItem?,
    targetFavorites: Set<String>,
    onSelectFolder: (String) -> Unit,
    onLongPressFolder: (String, DpOffset) -> Unit,
    onCreateNewAlbum: () -> Unit,
    onToggleExpansion: () -> Unit,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
    onShowSummary: () -> Unit,
    onUndo: () -> Unit,
    onShare: () -> Unit,
    layout: FolderBarLayout,
    folderName: String?
) {
    BoxWithConstraints {
        val containerWidth = this.maxWidth
        val containerHeight = this.maxHeight
        val folderCount = targetFolders.size
        val chipWidth = if (compactFoldersView) 60.dp else 85.dp
        val chipHeight = if (compactFoldersView) 70.dp else 100.dp
        val chipSpacingHorizontal = 8.dp
        val chipSpacingVertical = 4.dp // From FlowRow verticalArrangement

        val gridHorizontalPadding = 16.dp * 2
        val availableWidthForGrid = containerWidth - gridHorizontalPadding
        val maxChipsPerLine = (availableWidthForGrid / (chipWidth + chipSpacingHorizontal)).toInt().coerceAtLeast(1)

        val singleRowHeight = chipHeight + (chipSpacingVertical * 2)
        val contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)

        val numChipsInOneFlowRowLine = (availableWidthForGrid / (chipWidth + chipSpacingHorizontal)).toInt().coerceAtLeast(1)
        val estimatedTotalRowsForFlowRow = (folderCount + numChipsInOneFlowRowLine - 1) / numChipsInOneFlowRowLine
        val estimatedFlowRowContentHeight = (chipHeight * estimatedTotalRowsForFlowRow) + (chipSpacingVertical * (estimatedTotalRowsForFlowRow - 1)) + (contentPadding.calculateTopPadding().value + contentPadding.calculateBottomPadding().value).dp
        val showExpandButton = folderCount > maxChipsPerLine

        LaunchedEffect(folderCount, maxChipsPerLine, layout, isFolderBarExpanded, showExpandButton) {
            if (isFolderBarExpanded && !showExpandButton) {
                onSetExpanded(false)
            }
        }

        val isAmoled = currentTheme == AppTheme.AMOLED
        val barColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        val barElevation = if (isAmoled) 0.dp else 2.dp

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = barColor,
            tonalElevation = barElevation
        ) {
            Column(
                modifier = Modifier
                    .animateContentSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                if (folderName != null) {
                    FolderNameHeader(folderName)
                }

                ControlBar(
                    isExpanded = isFolderBarExpanded,
                    showExpandButton = showExpandButton,
                    hasPendingChanges = pendingChangesCount > 0,
                    onToggleExpansion = onToggleExpansion,
                    onCreateNewAlbum = onCreateNewAlbum,
                    onKeep = onKeep,
                    onDelete = onDelete,
                    onShowSummary = onShowSummary,
                    onUndo = onUndo,
                    onShare = onShare
                )

                if (targetFolders.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val maxContainerHeight = containerHeight * 0.4f

                    when (layout) {
                        FolderBarLayout.HORIZONTAL -> {
                            if (!isFolderBarExpanded) {
                                LazyRow(
                                    modifier = Modifier
                                        .height(singleRowHeight)
                                        .fillMaxWidth(),
                                    contentPadding = contentPadding,
                                    horizontalArrangement = Arrangement.spacedBy(chipSpacingHorizontal, Alignment.CenterHorizontally)
                                ) {
                                    items(items = targetFolders, key = { it.first }) { (folderId, fName) ->
                                        FolderChipWrapper(currentItem, compactFoldersView, folderId in targetFavorites, useLegacyFolderIcons, folderId, fName, onSelectFolder, onLongPressFolder)
                                    }
                                }
                            } else {
                                val flowRowWouldOverflowMaxContainerHeight = estimatedFlowRowContentHeight > maxContainerHeight
                                if (!flowRowWouldOverflowMaxContainerHeight) {
                                    FlowRow(
                                        modifier = Modifier
                                            .heightIn(min = singleRowHeight, max = maxContainerHeight)
                                            .fillMaxWidth()
                                            .padding(contentPadding),
                                        horizontalArrangement = Arrangement.spacedBy(chipSpacingHorizontal, Alignment.CenterHorizontally),
                                        verticalArrangement = Arrangement.spacedBy(chipSpacingVertical)
                                    ) {
                                        targetFolders.forEach { (folderId, fName) ->
                                            FolderChipWrapper(currentItem, compactFoldersView, folderId in targetFavorites, useLegacyFolderIcons, folderId, fName, onSelectFolder, onLongPressFolder)
                                        }
                                    }
                                } else {
                                    val maxRowsInHorizontalGrid = (maxContainerHeight / (chipHeight + chipSpacingVertical)).toInt().coerceAtLeast(1)
                                    LazyHorizontalGrid(
                                        rows = GridCells.Fixed(maxRowsInHorizontalGrid),
                                        modifier = Modifier
                                            .heightIn(min = singleRowHeight, max = maxContainerHeight)
                                            .fillMaxWidth(),
                                        contentPadding = contentPadding,
                                        horizontalArrangement = Arrangement.spacedBy(chipSpacingHorizontal, Alignment.CenterHorizontally),
                                        verticalArrangement = Arrangement.spacedBy(chipSpacingVertical)
                                    ) {
                                        items(items = targetFolders, key = { it.first }) { (folderId, fName) ->
                                            FolderChipWrapper(currentItem, compactFoldersView, folderId in targetFavorites, useLegacyFolderIcons, folderId, fName, onSelectFolder, onLongPressFolder)
                                        }
                                    }
                                }
                            }
                        }
                        FolderBarLayout.VERTICAL -> {
                            if (!isFolderBarExpanded) {
                                Box(
                                    modifier = Modifier
                                        .height(singleRowHeight)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    FlowRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(contentPadding),
                                        horizontalArrangement = Arrangement.spacedBy(chipSpacingHorizontal, Alignment.CenterHorizontally),
                                        verticalArrangement = Arrangement.spacedBy(chipSpacingVertical)
                                    ) {
                                        targetFolders.forEach { (folderId, fName) ->
                                            FolderChipWrapper(currentItem, compactFoldersView, folderId in targetFavorites, useLegacyFolderIcons, folderId, fName, onSelectFolder, onLongPressFolder)
                                        }
                                    }
                                }
                            } else {
                                val minColumnsInExpandedGrid = maxChipsPerLine.coerceAtLeast(1)
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(minColumnsInExpandedGrid),
                                    modifier = Modifier
                                        .heightIn(min = singleRowHeight, max = maxContainerHeight)
                                        .fillMaxWidth(),
                                    contentPadding = contentPadding,
                                    horizontalArrangement = Arrangement.spacedBy(chipSpacingHorizontal, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(chipSpacingVertical)
                                ) {
                                    items(items = targetFolders, key = { it.first }) { (folderId, fName) ->
                                        FolderChipWrapper(currentItem, compactFoldersView, folderId in targetFavorites, useLegacyFolderIcons, folderId, fName, onSelectFolder, onLongPressFolder)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun FolderChipWrapper(
    currentItem: MediaItem?,
    compactFoldersView: Boolean,
    isFavorite: Boolean,
    useLegacyFolderIcons: Boolean,
    folderId: String,
    folderName: String,
    onSelectFolder: (String) -> Unit,
    onLongPressFolder: (String, DpOffset) -> Unit
) {
    val isEnabled = remember(currentItem?.id, folderId) {
        val currentItemPath = currentItem?.id ?: return@remember true
        val parentDirectory = try {
            File(currentItemPath).parent
        } catch (e: Exception) {
            null
        }
        parentDirectory != folderId
    }

    val chipHeight = if (compactFoldersView) 70.dp else 100.dp
    val chipWidth = if (compactFoldersView) 60.dp else 85.dp
    val iconSize = if (compactFoldersView) 28.dp else 40.dp
    val density = LocalDensity.current
    var globalPosition by remember { mutableStateOf(Offset.Zero) }
    FolderChip(
        modifier = Modifier.onGloballyPositioned {
            globalPosition = it.localToWindow(Offset.Zero)
        },
        name = folderName,
        isFavorite = isFavorite,
        onClick = { onSelectFolder(folderId) },
        onLongClick = { pressOffset ->
            val absolutePressOffset = globalPosition + pressOffset
            val dpOffset = with(density) {
                DpOffset(absolutePressOffset.x.toDp(), absolutePressOffset.y.toDp())
            }
            onLongPressFolder(folderId, dpOffset)
        },
        chipWidth = chipWidth,
        chipHeight = chipHeight,
        iconSize = iconSize,
        useLegacyIcon = useLegacyFolderIcons,
        isCompact = compactFoldersView,
        isEnabled = isEnabled
    )
}

@Composable
private fun FolderChip(
    modifier: Modifier = Modifier,
    name: String,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit,
    isFavorite: Boolean,
    chipWidth: Dp,
    chipHeight: Dp,
    iconSize: Dp,
    useLegacyIcon: Boolean,
    isCompact: Boolean,
    isEnabled: Boolean = true
) {
    val alpha = if (isEnabled) 1f else 0.5f
    val color = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    val textStyle = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    Column(
        modifier = modifier
            .width(chipWidth)
            .height(chipHeight)
            .graphicsLayer(alpha = alpha)
            .pointerInput(onClick, onLongClick, isEnabled) {
                detectTapGestures(
                    onLongPress = { offset -> onLongClick(offset) },
                    onTap = { if (isEnabled) onClick() }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                if (useLegacyIcon) {
                    Text(
                        text = name
                            .take(1)
                            .uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = name,
                        tint = contentColor,
                        modifier = Modifier.size(iconSize * 0.6f)
                    )
                }
            }
            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (4).dp, y = (4).dp)
                        .size(iconSize / 2.2f),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = Color.Black.copy(alpha = 0.4f))
                    }
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = stringResource(R.string.favorite),
                        tint = Color.White,
                        modifier = Modifier.size(iconSize / 3.5f)
                    )
                }
            }
        }
        Text(
            text = name,
            style = textStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaItemCard(
    item: MediaItem,
    previousItem: MediaItem?,
    previewItems: List<MediaItem>,
    exoPlayer: ExoPlayer,
    imageLoader: ImageLoader,
    gifImageLoader: ImageLoader,
    onSwipeLeft: () -> Boolean,
    onSwipeRight: () -> Boolean,
    onSwipeDown: () -> Unit,
    onSwipeToDeletePool: () -> Unit,
    onTap: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    sensitivity: SwipeSensitivity,
    swipeDownAction: SwipeDownAction,
    videoPlaybackSpeed: Float,
    onSetVideoPlaybackSpeed: (Float) -> Unit,
    isVideoMuted: Boolean,
    onToggleMute: () -> Unit,
    isPendingConversion: Boolean,
    screenshotDeletesVideo: Boolean,
    fullScreenSwipe: Boolean
) {
    SwipeCardStack(
        item = item,
        previousItem = previousItem,
        nextItem = previewItems.firstOrNull(),
        exoPlayer = exoPlayer,
        imageLoader = imageLoader,
        gifImageLoader = gifImageLoader,
        onSwipeLeft = onSwipeLeft,
        onSwipeRight = onSwipeRight,
        onSwipeDown = onSwipeDown,
        onSwipeToDeletePool = onSwipeToDeletePool,
        onTap = onTap,
        modifier = modifier,
        sensitivity = sensitivity,
        swipeDownAction = swipeDownAction,
        videoPlaybackSpeed = videoPlaybackSpeed,
        onSetVideoPlaybackSpeed = onSetVideoPlaybackSpeed,
        isVideoMuted = isVideoMuted,
        onToggleMute = onToggleMute,
        isPendingConversion = isPendingConversion,
        screenshotDeletesVideo = screenshotDeletesVideo,
        fullScreenSwipe = fullScreenSwipe,
        pageContent = { mediaItem, isCurrent, isPreview, pageModifier ->
            Card(
                modifier = pageModifier,
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clip(MaterialTheme.shapes.medium)
                ) {
                    MediaCardArtwork(
                        item = mediaItem,
                        exoPlayer = exoPlayer,
                        imageLoader = imageLoader,
                        gifImageLoader = gifImageLoader,
                        modifier = Modifier.fillMaxSize(),
                        isPreview = isPreview
                    )
                }
            }
        }
    )
}


@Composable
private fun MediaCardArtwork(
    item: MediaItem,
    exoPlayer: ExoPlayer,
    imageLoader: ImageLoader,
    gifImageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    isPreview: Boolean
) {
    if (item.isVideo && !isPreview) {
        VideoPlayer(
            exoPlayer = exoPlayer,
            modifier = modifier
        )
        return
    }

    val loader = if (item.mimeType == "image/gif" && !isPreview) gifImageLoader else imageLoader
    Box(modifier = modifier) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .crossfade(!isPreview)
                .build(),
            imageLoader = loader,
            contentDescription = item.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center
        )
        if (item.isVideo && isPreview) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(44.dp)
                )
            }
        }
    }
}


@Composable
private fun VideoPlayer(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textureView = remember { TextureView(context) }
    AndroidView(
        factory = {
            textureView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            textureView
        },
        update = { view ->
            exoPlayer.setVideoTextureView(view)
        },
        onRelease = { view ->
            exoPlayer.clearVideoTextureView(view)
        },
        modifier = modifier
    )
}

@Composable
private fun NoMoreItemsMessage(pendingChanges: List<PendingChange>, onShowSummarySheet: () -> Unit) {
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.no_more_items))
        Spacer(modifier = Modifier.height(16.dp))
        if (pendingChanges.isNotEmpty()) {
            Button(onClick = onShowSummarySheet) { Text(stringResource(R.string.review_changes)) }
        }
    }
}

@Composable
private fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun AlreadyOrganizedDialog(
    onSelectNewFolders: () -> Unit,
    showResetHistoryButton: Boolean,
    onResetHistory: () -> Unit,
    onResetSingleFolderHistory: () -> Unit,
    skippedCount: Int,
    onReviewSkipped: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.all_organized_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        if (skippedCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            val skippedText = pluralStringResource(R.plurals.skipped_session_items_plurals, skippedCount, skippedCount)
            Text(
                text = skippedText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        if (skippedCount > 0) {
            Button(
                onClick = onReviewSkipped,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.review_skipped_items))
            }
        }
        Button(
            onClick = onSelectNewFolders,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.select_different_folders))
        }
        Button(
            onClick = onResetSingleFolderHistory,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.reset_single_folder))
        }
        if (showResetHistoryButton) {
            Button(
                onClick = onResetHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.reset_all_sorted_media))
            }
        }
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.close_app))
        }
    }
}
