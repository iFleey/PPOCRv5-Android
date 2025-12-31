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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.fleey.ppocrv5.R
import me.fleey.ppocrv5.ocr.OcrResult
import me.fleey.ppocrv5.ui.component.ImageViewerOcrOverlay
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ImageViewerScreen(
  imageUri: String,
  imageId: String,
  onBack: () -> Unit,
  animatedVisibilityScope: AnimatedVisibilityScope,
  modifier: Modifier = Modifier,
  viewModel: ImageViewerViewModel = viewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val textCopiedMessage = stringResource(R.string.image_viewer_text_copied)
  val scope = rememberCoroutineScope()

  var dragOffsetY by remember { mutableFloatStateOf(0f) }
  val dismissThreshold = 250f

  val backgroundAlpha by animateFloatAsState(
    targetValue = (1f - abs(dragOffsetY) / dismissThreshold).coerceIn(0.2f, 1f),
    label = "backgroundAlpha",
  )

  LaunchedEffect(imageId) {
    viewModel.initialize(imageId)
  }

  LaunchedEffect(uiState.textCopiedEvent) {
    uiState.textCopiedEvent?.let {
      snackbarHostState.showSnackbar(textCopiedMessage)
      viewModel.clearCopyEvent()
    }
  }

  Scaffold(
    snackbarHost = {
      SnackbarHost(hostState = snackbarHostState) { data ->
        Snackbar(
          snackbarData = data,
          containerColor = MaterialTheme.colorScheme.inverseSurface,
          contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        )
      }
    },
    floatingActionButton = {
      AnimatedVisibility(
        visible = uiState.ocrResults.isNotEmpty(),
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
      ) {
        FloatingActionButton(
          onClick = { viewModel.copyAllText() },
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.navigationBarsPadding(),
        ) {
          Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = stringResource(R.string.image_viewer_copy_all),
          )
        }
      }
    },
    containerColor = Color.Transparent,
    modifier = modifier,
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .background(Color.Black.copy(alpha = backgroundAlpha)),
    ) {
      if (uiState.allImages.isNotEmpty()) {
        val pagerState = rememberPagerState(
          initialPage = uiState.currentIndex,
          pageCount = { uiState.allImages.size },
        )

        LaunchedEffect(pagerState) {
          snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.onPageChanged(page)
          }
        }

        HorizontalPager(
          state = pagerState,
          modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .graphicsLayer {
              val scale = (1f - abs(dragOffsetY) / 800f).coerceIn(0.85f, 1f)
              scaleX = scale
              scaleY = scale
            },
          beyondViewportPageCount = 1,
          flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
        ) { page ->
          val image = uiState.allImages[page]
          val isCurrentPage = page == uiState.currentIndex

          ImagePage(
            imageUri = image.uri,
            imageId = image.id,
            isCurrentPage = isCurrentPage,
            ocrResults = if (isCurrentPage) uiState.ocrResults else emptyList(),
            imageWidth = if (isCurrentPage) uiState.imageWidth else 0,
            imageHeight = if (isCurrentPage) uiState.imageHeight else 0,
            isProcessing = isCurrentPage && uiState.isProcessing,
            animatedVisibilityScope = animatedVisibilityScope,
            onTextCopied = { text ->
              viewModel.copyText(text)
            },
            onVerticalDrag = { delta ->
              dragOffsetY += delta
            },
            onDragEnd = {
              if (abs(dragOffsetY) > dismissThreshold) {
                onBack()
              } else {
                scope.launch {
                  dragOffsetY = 0f
                }
              }
            },
          )
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(
            Brush.verticalGradient(
              listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
            ),
          )
          .statusBarsPadding()
          .padding(8.dp),
      ) {
        IconButton(onClick = onBack) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.image_viewer_back),
            tint = Color.White,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.ImagePage(
  imageUri: String,
  imageId: String,
  isCurrentPage: Boolean,
  ocrResults: List<OcrResult>,
  imageWidth: Int,
  imageHeight: Int,
  isProcessing: Boolean,
  animatedVisibilityScope: AnimatedVisibilityScope,
  onTextCopied: (String) -> Unit,
  onVerticalDrag: (Float) -> Unit,
  onDragEnd: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var scale by remember { mutableFloatStateOf(1f) }
  var offset by remember { mutableStateOf(Offset.Zero) }

  LaunchedEffect(isCurrentPage) {
    if (!isCurrentPage) {
      scale = 1f
      offset = Offset.Zero
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .pointerInput(Unit) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          var zoom = 1f
          var pan = Offset.Zero
          var pastTouchSlop = false
          val touchSlop = viewConfiguration.touchSlop
          var isVerticalDrag = false
          var totalDragY = 0f

          do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
              val zoomChange = event.calculateZoom()
              val panChange = event.calculatePan()

              if (!pastTouchSlop) {
                zoom *= zoomChange
                pan += panChange
                val centroidSize = (pan.getDistance() * zoom).coerceAtLeast(0.0001f)
                if (centroidSize > touchSlop) {
                  pastTouchSlop = true
                  isVerticalDrag = scale <= 1f && abs(panChange.y) > abs(panChange.x) * 1.5f
                }
              }

              if (pastTouchSlop) {
                if (zoomChange != 1f) {
                  scale = (scale * zoomChange).coerceIn(1f, 5f)
                }

                if (scale > 1f) {
                  offset += panChange
                  event.changes.forEach { if (it.positionChanged()) it.consume() }
                } else if (isVerticalDrag) {
                  totalDragY += panChange.y
                  onVerticalDrag(panChange.y)
                  event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
              }
            }
          } while (event.changes.any { it.pressed })

          if (isVerticalDrag) {
            onDragEnd()
          }

          if (scale <= 1f) {
            offset = Offset.Zero
          }
        }
      },
  ) {
    AsyncImage(
      model = imageUri,
      contentDescription = null,
      contentScale = ContentScale.Fit,
      modifier = Modifier
        .fillMaxSize()
        .sharedElement(
          sharedContentState = rememberSharedContentState(key = "image-$imageId"),
          animatedVisibilityScope = animatedVisibilityScope,
        )
        .graphicsLayer(
          scaleX = scale,
          scaleY = scale,
          translationX = offset.x,
          translationY = offset.y,
        ),
    )

    if (ocrResults.isNotEmpty() && imageWidth > 0 && imageHeight > 0) {
      ImageViewerOcrOverlay(
        results = ocrResults,
        sourceWidth = imageWidth,
        sourceHeight = imageHeight,
        onTextCopied = onTextCopied,
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer(
            scaleX = scale,
            scaleY = scale,
            translationX = offset.x,
            translationY = offset.y,
          ),
      )
    }

    if (isProcessing) {
      CircularProgressIndicator(
        modifier = Modifier.align(Alignment.Center),
        color = Color.White,
      )
    }
  }
}
