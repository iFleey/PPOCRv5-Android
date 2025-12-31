/*
 * Copyright (C) 2025 Fleey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.fleey.ppocrv5.ui.screen

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import me.fleey.ppocrv5.R
import me.fleey.ppocrv5.ui.model.GalleryImage

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.GalleryScreen(
  onImageClick: (uri: String, id: String) -> Unit,
  animatedVisibilityScope: AnimatedVisibilityScope,
  modifier: Modifier = Modifier,
  viewModel: GalleryViewModel = viewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  var showDeleteDialog by remember { mutableStateOf(false) }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri: Uri? ->
    uri?.let { viewModel.importImage(it) }
  }

  BackHandler(enabled = uiState.isSelectionMode) {
    viewModel.exitSelectionMode()
  }

  Scaffold(
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      if (uiState.isSelectionMode) {
        SelectionTopBar(
          selectedCount = uiState.selectedCount,
          allSelected = uiState.allSelected,
          onClose = { viewModel.exitSelectionMode() },
          onSelectAll = { viewModel.selectAll() },
          onDeselectAll = { viewModel.deselectAll() },
          onDelete = { showDeleteDialog = true },
        )
      } else {
        LargeTopAppBar(
          title = { Text(stringResource(R.string.gallery_title)) },
          scrollBehavior = scrollBehavior,
        )
      }
    },
    floatingActionButton = {
      AnimatedVisibility(
        visible = !uiState.isSelectionMode,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
      ) {
        ExtendedFloatingActionButton(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
          },
          icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
          text = { Text(stringResource(R.string.gallery_import)) },
        )
      }
    },
  ) { padding ->
    when {
      uiState.isLoading -> {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      }

      uiState.images.isEmpty() -> {
        EmptyGalleryContent(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        )
      }

      else -> {
        GalleryGrid(
          images = uiState.images,
          isSelectionMode = uiState.isSelectionMode,
          selectedIds = uiState.selectedIds,
          onImageClick = { image ->
            if (uiState.isSelectionMode) {
              viewModel.toggleSelection(image.id)
            } else {
              onImageClick(image.uri, image.id)
            }
          },
          onImageLongClick = { image ->
            if (!uiState.isSelectionMode) {
              viewModel.enterSelectionMode(image.id)
            }
          },
          animatedVisibilityScope = animatedVisibilityScope,
          contentPadding = padding,
        )
      }
    }
  }

  if (showDeleteDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(stringResource(R.string.gallery_delete_selected_title)) },
      text = { Text(stringResource(R.string.gallery_delete_selected_message, uiState.selectedCount)) },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteSelected()
            showDeleteDialog = false
          },
        ) {
          Text(stringResource(R.string.gallery_delete_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text(stringResource(R.string.common_cancel))
        }
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
  selectedCount: Int,
  allSelected: Boolean,
  onClose: () -> Unit,
  onSelectAll: () -> Unit,
  onDeselectAll: () -> Unit,
  onDelete: () -> Unit,
) {
  TopAppBar(
    navigationIcon = {
      IconButton(onClick = onClose) {
        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_cancel))
      }
    },
    title = { Text(stringResource(R.string.gallery_selected_count, selectedCount)) },
    actions = {
      IconButton(onClick = if (allSelected) onDeselectAll else onSelectAll) {
        Icon(
          Icons.Rounded.SelectAll,
          contentDescription = stringResource(
            if (allSelected) R.string.gallery_deselect_all else R.string.gallery_select_all,
          ),
        )
      }
      IconButton(onClick = onDelete, enabled = selectedCount > 0) {
        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.gallery_delete_confirm))
      }
    },
  )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.GalleryGrid(
  images: List<GalleryImage>,
  isSelectionMode: Boolean,
  selectedIds: Set<String>,
  onImageClick: (GalleryImage) -> Unit,
  onImageLongClick: (GalleryImage) -> Unit,
  animatedVisibilityScope: AnimatedVisibilityScope,
  contentPadding: PaddingValues,
  modifier: Modifier = Modifier,
) {
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 120.dp),
    contentPadding = contentPadding,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
    modifier = modifier.fillMaxSize(),
  ) {
    items(
      items = images,
      key = { it.id },
    ) { image ->
      GalleryImageItem(
        image = image,
        isSelectionMode = isSelectionMode,
        isSelected = image.id in selectedIds,
        onClick = { onImageClick(image) },
        onLongClick = { onImageLongClick(image) },
        animatedVisibilityScope = animatedVisibilityScope,
      )
    }
  }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SharedTransitionScope.GalleryImageItem(
  image: GalleryImage,
  isSelectionMode: Boolean,
  isSelected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  animatedVisibilityScope: AnimatedVisibilityScope,
  modifier: Modifier = Modifier,
) {
  val hapticFeedback = LocalHapticFeedback.current

  Box(
    modifier = modifier
      .aspectRatio(1f)
      .combinedClickable(
        onClick = onClick,
        onLongClick = {
          hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
          onLongClick()
        },
      ),
  ) {
    AsyncImage(
      model = image.uri,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .fillMaxSize()
        .sharedElement(
          sharedContentState = rememberSharedContentState(key = "image-${image.id}"),
          animatedVisibilityScope = animatedVisibilityScope,
        )
        .clip(RoundedCornerShape(0.dp)),
    )

    AnimatedVisibility(
      visible = isSelectionMode,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(8.dp),
    ) {
      Box(
        modifier = Modifier
          .size(24.dp)
          .background(
            color = if (isSelected) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            },
            shape = CircleShape,
          ),
        contentAlignment = Alignment.Center,
      ) {
        if (isSelected) {
          Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(24.dp),
          )
        }
      }
    }

    if (isSelected) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
      )
    }
  }
}

@Composable
private fun EmptyGalleryContent(
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = Icons.Rounded.PhotoLibrary,
      contentDescription = null,
      modifier = Modifier.size(64.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = stringResource(R.string.gallery_empty),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = stringResource(R.string.gallery_empty_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 32.dp),
    )
  }
}
