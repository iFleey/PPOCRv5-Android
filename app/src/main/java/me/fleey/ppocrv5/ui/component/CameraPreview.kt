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

package me.fleey.ppocrv5.ui.component

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.fleey.ppocrv5.ocr.ResolutionPreset
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
  resolutionPreset: ResolutionPreset,
  flashEnabled: Boolean,
  useFrontCamera: Boolean,
  onFrameAnalyzed: (Bitmap) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
  val currentOnFrameAnalyzed = rememberUpdatedState(onFrameAnalyzed)
  val currentResolution = rememberUpdatedState(resolutionPreset)
  var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
  val cameraSelector = if (useFrontCamera) {
    CameraSelector.DEFAULT_FRONT_CAMERA
  } else {
    CameraSelector.DEFAULT_BACK_CAMERA
  }

  LaunchedEffect(flashEnabled, useFrontCamera) {
    if (!useFrontCamera) {
      cameraControl?.enableTorch(flashEnabled)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      analysisExecutor.shutdown()
    }
  }

  AndroidView(
    factory = { ctx ->
      PreviewView(ctx).apply {
        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        scaleType = PreviewView.ScaleType.FIT_CENTER
      }
    },
    modifier = modifier,
    update = { previewView ->
      Log.d("CameraPreview", "Update called, resolution: ${currentResolution.value.size}, front: $useFrontCamera")

      val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
      cameraProviderFuture.addListener(
        {
          val cameraProvider = cameraProviderFuture.get()

          val preview = Preview.Builder()
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

          @Suppress("DEPRECATION")
          val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(currentResolution.value.size)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
              analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                processImageProxy(imageProxy, currentOnFrameAnalyzed.value, useFrontCamera)
              }
            }

          runCatching {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
              lifecycleOwner,
              cameraSelector,
              preview,
              imageAnalyzer,
            )
            cameraControl = camera.cameraControl
            if (!useFrontCamera) {
              cameraControl?.enableTorch(flashEnabled)
            }
          }.onFailure {
            Log.e("CameraPreview", "Failed to bind camera", it)
          }
        },
        ContextCompat.getMainExecutor(context),
      )
    },
  )
}

private fun processImageProxy(
  imageProxy: ImageProxy,
  onFrameAnalyzed: (Bitmap) -> Unit,
  useFrontCamera: Boolean = false,
) {
  try {
    val bitmap = imageProxy.toBitmap()
    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
    imageProxy.close()

    val matrix = Matrix().apply {
      if (rotationDegrees != 0) {
        postRotate(rotationDegrees.toFloat())
      }
      if (useFrontCamera) {
        postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
      }
    }

    val finalBitmap = if (rotationDegrees == 0 && !useFrontCamera) {
      bitmap
    } else {
      val transformed = Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true,
      )
      bitmap.recycle()
      transformed
    }

    onFrameAnalyzed(finalBitmap)
  } catch (e: Exception) {
    Log.e("CameraPreview", "Error processing image", e)
    imageProxy.close()
  }
}
