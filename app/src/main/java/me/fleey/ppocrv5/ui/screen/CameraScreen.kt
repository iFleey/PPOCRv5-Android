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

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Lens
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.fleey.ppocrv5.R
import me.fleey.ppocrv5.ocr.AcceleratorType
import me.fleey.ppocrv5.ocr.ResolutionPreset
import me.fleey.ppocrv5.ui.component.BenchmarkPanel
import me.fleey.ppocrv5.ui.component.CameraPermissionHandler
import me.fleey.ppocrv5.ui.component.CameraPreview
import me.fleey.ppocrv5.ui.component.CameraSettingsContent
import me.fleey.ppocrv5.ui.component.ErrorContent
import me.fleey.ppocrv5.ui.component.OcrOverlay
import me.fleey.ppocrv5.ui.state.CameraUiState

@Composable
fun CameraScreen(
  viewModel: CameraViewModel = viewModel(),
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  CameraPermissionHandler(
    onPermissionGranted = {
      LaunchedEffect(Unit) {
        viewModel.initialize(context)
      }

      CameraScreenContent(
        uiState = uiState,
        onFrameAnalyzed = viewModel::onFrameReceived,
        onAcceleratorChanged = { type -> viewModel.onAcceleratorChanged(context, type) },
        onResolutionChanged = viewModel::onResolutionChanged,
        onToggleBenchmark = viewModel::toggleBenchmarkPanel,
        onToggleFlash = viewModel::toggleFlash,
        onToggleFreeze = viewModel::toggleFreeze,
        onToggleCamera = viewModel::toggleCamera,
        onCapture = viewModel::capture,
        onRetry = { viewModel.retry(context) },
        modifier = modifier,
      )
    },
    onPermissionDenied = viewModel::onPermissionDenied,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraScreenContent(
  uiState: CameraUiState,
  onFrameAnalyzed: (Bitmap) -> Unit,
  onAcceleratorChanged: (AcceleratorType) -> Unit,
  onResolutionChanged: (ResolutionPreset) -> Unit,
  onToggleBenchmark: () -> Unit,
  onToggleFlash: () -> Unit,
  onToggleFreeze: () -> Unit,
  onToggleCamera: () -> Unit,
  onCapture: () -> Boolean,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  when (uiState) {
    is CameraUiState.Loading -> {
      LoadingContent(modifier = modifier)
    }

    is CameraUiState.Ready -> {
      ReadyContent(
        state = uiState,
        onFrameAnalyzed = onFrameAnalyzed,
        onAcceleratorChanged = onAcceleratorChanged,
        onResolutionChanged = onResolutionChanged,
        onToggleBenchmark = onToggleBenchmark,
        onToggleFlash = onToggleFlash,
        onToggleFreeze = onToggleFreeze,
        onToggleCamera = onToggleCamera,
        onCapture = onCapture,
        modifier = modifier,
      )
    }

    is CameraUiState.Error -> {
      ErrorContent(
        errorType = uiState.type,
        onRetry = onRetry,
        modifier = modifier,
      )
    }
  }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center,
  ) {
    CircularProgressIndicator(
      color = MaterialTheme.colorScheme.primary,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyContent(
  state: CameraUiState.Ready,
  onFrameAnalyzed: (Bitmap) -> Unit,
  onAcceleratorChanged: (AcceleratorType) -> Unit,
  onResolutionChanged: (ResolutionPreset) -> Unit,
  onToggleBenchmark: () -> Unit,
  onToggleFlash: () -> Unit,
  onToggleFreeze: () -> Unit,
  onToggleCamera: () -> Unit,
  onCapture: () -> Boolean,
  modifier: Modifier = Modifier,
) {
  var sourceWidth by remember { mutableIntStateOf(0) }
  var sourceHeight by remember { mutableIntStateOf(0) }
  var showSettings by remember { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState()

  Box(modifier = modifier.fillMaxSize()) {
    CameraPreview(
      resolutionPreset = state.resolutionPreset,
      flashEnabled = state.flashEnabled,
      useFrontCamera = state.useFrontCamera,
      onFrameAnalyzed = { bitmap ->
        sourceWidth = bitmap.width
        sourceHeight = bitmap.height
        onFrameAnalyzed(bitmap)
      },
      modifier = Modifier.fillMaxSize(),
    )

    OcrOverlay(
      results = state.ocrResults,
      sourceWidth = sourceWidth,
      sourceHeight = sourceHeight,
      modifier = Modifier.fillMaxSize(),
    )

    AnimatedVisibility(
      visible = state.captureFlash,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.White.copy(alpha = 0.7f)),
      )
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.TopCenter)
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color.Black.copy(alpha = 0.6f),
              Color.Transparent,
            ),
          ),
        )
        .statusBarsPadding()
        .padding(16.dp),
    ) {
      BenchmarkPanel(
        benchmark = state.benchmark,
        acceleratorType = state.acceleratorType,
        expanded = state.benchmarkExpanded,
        onToggleExpanded = onToggleBenchmark,
        modifier = Modifier.align(Alignment.TopStart),
      )
    }

    Row(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color.Transparent,
              Color.Black.copy(alpha = 0.5f),
            ),
          ),
        )
        .navigationBarsPadding()
        .padding(bottom = 32.dp, top = 20.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {

      FilledTonalIconButton(
        onClick = { showSettings = true },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ),
      ) {
        Icon(
          imageVector = Icons.Rounded.Tune,
          contentDescription = stringResource(R.string.camera_settings),
        )
      }

      FilledTonalIconButton(
        onClick = onToggleFreeze,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
          containerColor = if (state.frozen) {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f)
          } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
          },
        ),
      ) {
        Icon(
          imageVector = if (state.frozen) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
          contentDescription = stringResource(
            if (state.frozen) R.string.camera_resume else R.string.camera_freeze,
          ),
        )
      }

      LargeFloatingActionButton(
        onClick = { onCapture() },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(80.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Lens,
          contentDescription = stringResource(R.string.camera_capture),
          modifier = Modifier.size(48.dp),
        )
      }

      FilledTonalIconButton(
        onClick = onToggleCamera,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ),
      ) {
        Icon(
          imageVector = Icons.Rounded.Cameraswitch,
          contentDescription = stringResource(R.string.camera_switch),
        )
      }

      FilledTonalIconButton(
        onClick = onToggleFlash,
        enabled = !state.useFrontCamera,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
          containerColor = if (state.flashEnabled && !state.useFrontCamera) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
          } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
          },
        ),
      ) {
        Icon(
          imageVector = if (state.flashEnabled && !state.useFrontCamera) {
            Icons.Rounded.FlashOn
          } else {
            Icons.Rounded.FlashOff
          },
          contentDescription = if (state.flashEnabled) {
            stringResource(R.string.camera_flash_on)
          } else {
            stringResource(R.string.camera_flash_off)
          },
        )
      }
    }
  }

  if (showSettings) {
    ModalBottomSheet(
      onDismissRequest = { showSettings = false },
      sheetState = sheetState,
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
      CameraSettingsContent(
        currentAccelerator = state.acceleratorType,
        currentResolution = state.resolutionPreset,
        onAcceleratorChanged = onAcceleratorChanged,
        onResolutionChanged = onResolutionChanged,
      )
    }
  }
}
