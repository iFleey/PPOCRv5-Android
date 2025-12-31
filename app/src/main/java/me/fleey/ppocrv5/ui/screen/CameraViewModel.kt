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

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.fleey.ppocrv5.data.GalleryRepository
import me.fleey.ppocrv5.ocr.AcceleratorType
import me.fleey.ppocrv5.ocr.Benchmark
import me.fleey.ppocrv5.ocr.OcrEngine
import me.fleey.ppocrv5.ocr.ResolutionPreset
import me.fleey.ppocrv5.ui.state.CameraUiState
import me.fleey.ppocrv5.ui.state.ErrorType
import me.fleey.ppocrv5.util.PreferencesManager
import java.util.concurrent.atomic.AtomicBoolean

class CameraViewModel : ViewModel() {

  private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Loading)
  val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

  private var ocrEngine: OcrEngine? = null
  private val isProcessing = AtomicBoolean(false)
  private val engineMutex = Mutex()
  private var galleryRepository: GalleryRepository? = null
  private var lastCapturedBitmap: Bitmap? = null

  private var lastFpsTime = 0.0

  private var lastFrameHash = 0L
  private var stableFrameCount = 0

  private companion object {
    const val STABILITY_THRESHOLD = 1
    const val HASH_DIFF_THRESHOLD = 3000L
  }

  fun initialize(context: Context) {
    galleryRepository = GalleryRepository.getInstance(context)
    viewModelScope.launch {
      val savedAccelerator = PreferencesManager.getAcceleratorType(context)
      initializeEngine(context, savedAccelerator)
    }
  }

  private suspend fun initializeEngine(
    context: Context,
    acceleratorType: AcceleratorType,
    resolutionPreset: ResolutionPreset = ResolutionPreset.DEFAULT,
  ) {
    engineMutex.withLock {
      val previousResolution = (_uiState.value as? CameraUiState.Ready)?.resolutionPreset
        ?: resolutionPreset
      _uiState.value = CameraUiState.Loading

      while (isProcessing.get()) {
        kotlinx.coroutines.delay(10)
      }

      withContext(Dispatchers.IO) {
        ocrEngine?.close()
        ocrEngine = null

        OcrEngine.create(context, acceleratorType)
          .onSuccess { engine ->
            ocrEngine = engine
            lastFpsTime = 0.0

            val activeAccelerator = engine.getActiveAccelerator()
            if (activeAccelerator != acceleratorType) {
              PreferencesManager.saveAcceleratorType(context, activeAccelerator)
            }
            _uiState.value = CameraUiState.Ready(
              acceleratorType = activeAccelerator,
              resolutionPreset = previousResolution,
              benchmark = Benchmark(),
            )
          }
          .onFailure {
            _uiState.value = CameraUiState.Error(ErrorType.ModelLoadFailed)
          }
      }
    }
  }

  fun onFrameReceived(bitmap: Bitmap) {
    val engine = ocrEngine ?: return
    val currentState = _uiState.value
    if (currentState !is CameraUiState.Ready) return
    if (currentState.frozen) return

    lastCapturedBitmap?.recycle()
    lastCapturedBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)

    val currentHash = computeFrameHash(bitmap)
    val hashDiff = kotlin.math.abs(currentHash - lastFrameHash)
    lastFrameHash = currentHash

    if (hashDiff > HASH_DIFF_THRESHOLD) {
      stableFrameCount = 0
      return
    }

    if (++stableFrameCount < STABILITY_THRESHOLD) return
    if (!isProcessing.compareAndSet(false, true)) return

    viewModelScope.launch {
      processFrame(engine, bitmap)
      isProcessing.set(false)
    }
  }

  private fun computeFrameHash(bitmap: Bitmap): Long {
    val scaled = bitmap.scale(8, 8)
    var hash = 0L
    for (y in 0 until 8) {
      for (x in 0 until 8) {
        val pixel = scaled[x, y]
        hash += ((android.graphics.Color.red(pixel) +
          android.graphics.Color.green(pixel) +
          android.graphics.Color.blue(pixel)) / 3).toLong()
      }
    }
    scaled.recycle()
    return hash
  }

  private suspend fun processFrame(engine: OcrEngine, bitmap: Bitmap) {
    val currentState = _uiState.value
    if (currentState !is CameraUiState.Ready) return

    withContext(Dispatchers.Default) {
      runCatching {
        if (ocrEngine !== engine) return@withContext

        val results = engine.process(bitmap)
        val benchmark = engine.getBenchmark()

        val currentTime = System.nanoTime() / 1_000_000.0
        val instantFps = if (lastFpsTime > 0) {
          val deltaTime = currentTime - lastFpsTime
          if (deltaTime > 0) (1000.0 / deltaTime).toFloat() else 0f
        } else {
          0f
        }
        lastFpsTime = currentTime

        val adjustedBenchmark = benchmark.copy(fps = instantFps)

        Log.d(
          "CameraViewModel",
          "OCR results: ${results.size} items, det=${benchmark.detectionTimeMs}ms, rec=${benchmark.recognitionTimeMs}ms, fps=$instantFps",
        )

        _uiState.update { state ->
          if (state is CameraUiState.Ready && ocrEngine === engine) {
            state.copy(
              ocrResults = results,
              benchmark = adjustedBenchmark,
            )
          } else {
            state
          }
        }
      }
    }
  }

  fun onAcceleratorChanged(context: Context, type: AcceleratorType) {
    val currentState = _uiState.value
    if (currentState is CameraUiState.Ready && currentState.acceleratorType == type) {
      return
    }

    PreferencesManager.saveAcceleratorType(context, type)

    viewModelScope.launch {
      val resolution = (currentState as? CameraUiState.Ready)?.resolutionPreset
        ?: ResolutionPreset.DEFAULT
      initializeEngine(context, type, resolution)
    }
  }

  fun onResolutionChanged(resolution: ResolutionPreset) {
    _uiState.update { state ->
      if (state is CameraUiState.Ready && state.resolutionPreset != resolution) {
        state.copy(resolutionPreset = resolution, benchmark = Benchmark())
      } else {
        state
      }
    }
  }

  fun toggleBenchmarkPanel() {
    _uiState.update { state ->
      if (state is CameraUiState.Ready) {
        state.copy(benchmarkExpanded = !state.benchmarkExpanded)
      } else {
        state
      }
    }
  }

  fun toggleFlash() {
    _uiState.update { state ->
      if (state is CameraUiState.Ready) {
        state.copy(flashEnabled = !state.flashEnabled)
      } else {
        state
      }
    }
  }

  fun toggleFreeze() {
    _uiState.update { state ->
      if (state is CameraUiState.Ready) {
        state.copy(frozen = !state.frozen)
      } else {
        state
      }
    }
  }

  fun capture(): Boolean {
    val bitmap = lastCapturedBitmap ?: return false
    val repo = galleryRepository ?: return false

    // Check if bitmap is valid
    if (bitmap.isRecycled) return false

    // Copy bitmap before async operation since original may be recycled
    val bitmapCopy = try {
      bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
    } catch (e: Exception) {
      return false
    } ?: return false

    _uiState.update { state ->
      if (state is CameraUiState.Ready) state.copy(captureFlash = true) else state
    }

    viewModelScope.launch {
      try {
        repo.saveCapture(bitmapCopy)
      } finally {
        if (!bitmapCopy.isRecycled) {
          bitmapCopy.recycle()
        }
      }
      kotlinx.coroutines.delay(150)
      _uiState.update { state ->
        if (state is CameraUiState.Ready) state.copy(captureFlash = false) else state
      }
    }
    return true
  }

  fun toggleCamera() {
    _uiState.update { state ->
      if (state is CameraUiState.Ready) {
        state.copy(useFrontCamera = !state.useFrontCamera, flashEnabled = false)
      } else {
        state
      }
    }
  }

  fun onPermissionDenied() {
    _uiState.value = CameraUiState.Error(ErrorType.CameraPermissionDenied)
  }

  fun retry(context: Context) {
    viewModelScope.launch {
      val savedAccelerator = PreferencesManager.getAcceleratorType(context)
      initializeEngine(context, savedAccelerator)
    }
  }

  override fun onCleared() {
    super.onCleared()
    ocrEngine?.close()
    ocrEngine = null
    lastCapturedBitmap?.recycle()
    lastCapturedBitmap = null
  }
}
