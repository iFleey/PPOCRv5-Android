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

package me.fleey.ppocrv5.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.fleey.ppocrv5.ocr.OcrEngine
import me.fleey.ppocrv5.ui.model.GalleryImage
import java.util.LinkedList

class OcrProcessingManager private constructor(private val context: Context) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val repository = GalleryRepository.getInstance(context)
  private val queue = LinkedList<String>()
  private val mutex = Mutex()
  private var ocrEngine: OcrEngine? = null
  private var isProcessing = false

  private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
  val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

  init {
    observeNewImages()
  }

  private fun observeNewImages() {
    scope.launch {
      repository.imageAdded.collect { image ->
        enqueue(image.id)
      }
    }
  }

  fun enqueue(imageId: String) {
    scope.launch {
      mutex.withLock {
        if (!queue.contains(imageId)) {
          queue.add(imageId)
        }
      }
      processNext()
    }
  }

  private suspend fun processNext() {
    mutex.withLock {
      if (isProcessing || queue.isEmpty()) return
      isProcessing = true
    }

    val imageId = mutex.withLock { queue.poll() } ?: run {
      mutex.withLock { isProcessing = false }
      return
    }

    _processingState.value = ProcessingState.Processing(imageId)

    try {
      val image = repository.getImage(imageId)
      if (image != null && !image.ocrProcessed) {
        processImage(image)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to process image $imageId", e)
    }

    mutex.withLock { isProcessing = false }
    _processingState.value = ProcessingState.Idle

    processNext()
  }

  private suspend fun processImage(image: GalleryImage) {
    try {
      val uri = image.uri.toUri()
      val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input)
      } ?: return

      if (ocrEngine == null) {
        ocrEngine = OcrEngine.create(context).getOrNull()
      }

      val engine = ocrEngine ?: return
      val results = engine.process(bitmap)
      repository.updateImageOcr(image.id, results)

      Log.d(TAG, "Processed image ${image.id}: ${results.size} results")
    } catch (e: Exception) {
      Log.e(TAG, "Error processing image ${image.id}", e)
    }
  }

  fun processUnprocessedImages() {
    scope.launch {
      val images = repository.loadImages()
      images.filter { !it.ocrProcessed }.forEach { image ->
        enqueue(image.id)
      }
    }
  }

  sealed interface ProcessingState {
    data object Idle : ProcessingState
    data class Processing(val imageId: String) : ProcessingState
  }

  companion object {
    private const val TAG = "OcrProcessingManager"

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var instance: OcrProcessingManager? = null

    fun getInstance(context: Context): OcrProcessingManager {
      return instance ?: synchronized(this) {
        instance ?: OcrProcessingManager(context.applicationContext).also { instance = it }
      }
    }
  }
}
