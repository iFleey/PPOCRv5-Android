/*
 * Copyright (C) 2025-2026 Fleey
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
import android.os.SystemClock
import android.util.Log
import androidx.core.graphics.get
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
  private val bitmapLock = Any()
  private var lastCapturedBitmap: Bitmap? = null
  private var pendingFrameBitmap: Bitmap? = null

  private var lastFpsTime = 0.0

  private var lastFrameHash = 0L
  private var stableFrameCount = 0
  private var lastSubmittedAtMs = 0L
  private var processingIntervalMs = DEFAULT_PROCESSING_INTERVAL_MS

  private companion object {
    const val STABILITY_THRESHOLD = 2
    const val HASH_DIFF_THRESHOLD = 3000L
    const val HASH_GRID_SIZE = 8
    const val DEFAULT_PROCESSING_INTERVAL_MS = 150L
    const val MIN_PROCESSING_INTERVAL_MS = 90L
    const val MAX_PROCESSING_INTERVAL_MS = 280L
    const val PROCESSING_INTERVAL_HEADROOM = 1.1f
  }

  fun initialize(context: Context) {
    galleryRepository = GalleryRepository.getInstance(context)
    viewModelScope.launch {
      val savedAccelerator = AcceleratorType.coerceToSelectable(
        PreferencesManager.getAcceleratorType(context),
      )
      initializeEngine(context, savedAccelerator)
    }
  }

  private suspend fun initializeEngine(
    context: Context,
    acceleratorType: AcceleratorType,
    resolutionPreset: ResolutionPreset = ResolutionPreset.DEFAULT,
  ) {
    val requestedAccelerator = AcceleratorType.coerceToSelectable(acceleratorType)
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

        OcrEngine.create(context, requestedAccelerator)
          .onSuccess { engine ->
            ocrEngine = engine
            lastFpsTime = 0.0
            lastFrameHash = 0L
            stableFrameCount = 0
            lastSubmittedAtMs = 0L
            processingIntervalMs = DEFAULT_PROCESSING_INTERVAL_MS

            val activeAccelerator = engine.getActiveAccelerator()
            if (activeAccelerator != requestedAccelerator) {
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
    val engine = ocrEngine ?: run {
      bitmap.recycleSafely()
      return
    }
    val currentState = _uiState.value
    if (currentState !is CameraUiState.Ready || currentState.frozen) {
      bitmap.recycleSafely()
      return
    }

    val currentHash = computeFrameHash(bitmap)
    val hashDiff = kotlin.math.abs(currentHash - lastFrameHash)
    lastFrameHash = currentHash

    if (hashDiff > HASH_DIFF_THRESHOLD) {
      stableFrameCount = 0
      bitmap.recycleSafely()
      return
    }

    if (++stableFrameCount < STABILITY_THRESHOLD) {
      bitmap.recycleSafely()
      return
    }

    val now = SystemClock.elapsedRealtime()
    if (now - lastSubmittedAtMs < processingIntervalMs) {
      bitmap.recycleSafely()
      return
    }

    if (!isProcessing.compareAndSet(false, true)) {
      replacePendingFrame(bitmap)
      return
    }
    lastSubmittedAtMs = now

    launchFrameProcessing(engine, bitmap)
  }

  private fun computeFrameHash(bitmap: Bitmap): Long {
    val maxX = bitmap.width - 1
    val maxY = bitmap.height - 1
    var hash = 0L
    for (y in 0 until HASH_GRID_SIZE) {
      val sampleY = if (maxY <= 0) 0 else (maxY * y) / (HASH_GRID_SIZE - 1)
      for (x in 0 until HASH_GRID_SIZE) {
        val sampleX = if (maxX <= 0) 0 else (maxX * x) / (HASH_GRID_SIZE - 1)
        val pixel = bitmap[sampleX, sampleY]
        hash += ((android.graphics.Color.red(pixel) +
          android.graphics.Color.green(pixel) +
          android.graphics.Color.blue(pixel)) / 3).toLong()
      }
    }
    return hash
  }

  private suspend fun processFrame(engine: OcrEngine, bitmap: Bitmap) {
    val currentState = _uiState.value
    if (currentState !is CameraUiState.Ready) {
      bitmap.recycleSafely()
      return
    }

    withContext(Dispatchers.Default) {
      var retainBitmap = false
      runCatching {
        if (ocrEngine !== engine) return@withContext

        val results = engine.process(bitmap)
        val benchmark = engine.getBenchmark()
        retainBitmap = true
        replaceCapturedBitmap(bitmap)
        processingIntervalMs = benchmark.totalTimeMs
          .takeIf { it > 0f }
          ?.let { suggested ->
            (suggested * PROCESSING_INTERVAL_HEADROOM)
              .toLong()
              .coerceIn(MIN_PROCESSING_INTERVAL_MS, MAX_PROCESSING_INTERVAL_MS)
          }
          ?: DEFAULT_PROCESSING_INTERVAL_MS

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
      }.onFailure {
        Log.e("CameraViewModel", "Failed to process frame", it)
      }.also {
        if (!retainBitmap) {
          bitmap.recycleSafely()
        }
      }
    }
  }

  private fun launchFrameProcessing(engine: OcrEngine, bitmap: Bitmap) {
    viewModelScope.launch {
      processFrameLoop(engine, bitmap)
    }
  }

  private suspend fun processFrameLoop(engine: OcrEngine, initialBitmap: Bitmap) {
    var currentBitmap: Bitmap? = initialBitmap
    while (currentBitmap != null) {
      processFrame(engine, currentBitmap)

      currentBitmap = takePendingFrame()
      if (currentBitmap != null) {
        val currentState = _uiState.value
        if (currentState is CameraUiState.Ready && !currentState.frozen && ocrEngine === engine) {
          lastSubmittedAtMs = SystemClock.elapsedRealtime()
          continue
        }
        currentBitmap.recycleSafely()
        currentBitmap = null
      }

      isProcessing.set(false)

      val racedBitmap = takePendingFrame()
      if (racedBitmap == null) {
        return
      }
      if (!isProcessing.compareAndSet(false, true)) {
        replacePendingFrame(racedBitmap)
        return
      }

      val currentState = _uiState.value
      if (currentState !is CameraUiState.Ready || currentState.frozen || ocrEngine !== engine) {
        racedBitmap.recycleSafely()
        isProcessing.set(false)
        return
      }

      lastSubmittedAtMs = SystemClock.elapsedRealtime()
      currentBitmap = racedBitmap
    }
  }

  fun onAcceleratorChanged(context: Context, type: AcceleratorType) {
    val selectableType = AcceleratorType.coerceToSelectable(type)
    val currentState = _uiState.value
    if (currentState is CameraUiState.Ready && currentState.acceleratorType == selectableType) {
      return
    }

    PreferencesManager.saveAcceleratorType(context, selectableType)

    viewModelScope.launch {
      val resolution = (currentState as? CameraUiState.Ready)?.resolutionPreset
        ?: ResolutionPreset.DEFAULT
      initializeEngine(context, selectableType, resolution)
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
    val repo = galleryRepository ?: return false

    val bitmapCopy = synchronized(bitmapLock) {
      val bitmap = lastCapturedBitmap ?: return false
      if (bitmap.isRecycled) return false

      try {
        bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
      } catch (e: Exception) {
        null
      }
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
      val savedAccelerator = AcceleratorType.coerceToSelectable(
        PreferencesManager.getAcceleratorType(context),
      )
      initializeEngine(context, savedAccelerator)
    }
  }

  override fun onCleared() {
    super.onCleared()
    ocrEngine?.close()
    ocrEngine = null
    synchronized(bitmapLock) {
      lastCapturedBitmap?.recycleSafely()
      lastCapturedBitmap = null
      pendingFrameBitmap?.recycleSafely()
      pendingFrameBitmap = null
    }
  }

  private fun replaceCapturedBitmap(bitmap: Bitmap) {
    synchronized(bitmapLock) {
      if (lastCapturedBitmap === bitmap) return
      lastCapturedBitmap?.recycleSafely()
      lastCapturedBitmap = bitmap
    }
  }

  private fun replacePendingFrame(bitmap: Bitmap) {
    synchronized(bitmapLock) {
      if (pendingFrameBitmap === bitmap) return
      pendingFrameBitmap?.recycleSafely()
      pendingFrameBitmap = bitmap
    }
  }

  private fun takePendingFrame(): Bitmap? = synchronized(bitmapLock) {
    pendingFrameBitmap.also {
      pendingFrameBitmap = null
    }
  }

  private fun Bitmap.recycleSafely() {
    if (!isRecycled) {
      recycle()
    }
  }
}
