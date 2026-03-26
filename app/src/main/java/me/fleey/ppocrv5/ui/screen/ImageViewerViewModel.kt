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

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.fleey.ppocrv5.data.GalleryOcrService
import me.fleey.ppocrv5.data.GalleryRepository
import me.fleey.ppocrv5.ocr.OcrResult
import me.fleey.ppocrv5.ui.model.GalleryImage
import me.fleey.ppocrv5.ui.state.ImageViewerUiState
import me.fleey.ppocrv5.ui.state.TextCopiedEvent

class ImageViewerViewModel(application: Application) : AndroidViewModel(application) {

  private val _uiState = MutableStateFlow(ImageViewerUiState())
  val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()

  private val repository = GalleryRepository.getInstance(application)
  private val ocrService = GalleryOcrService.getInstance(application)
  private val ocrCache = mutableMapOf<String, Pair<List<OcrResult>, Pair<Int, Int>>>()

  fun initialize(imageId: String) {
    viewModelScope.launch {
      val allImages = repository.getAllImages()
      val index = allImages.indexOfFirst { it.id == imageId }.coerceAtLeast(0)

      _uiState.update {
        it.copy(
          allImages = allImages,
          currentIndex = index,
        )
      }

      allImages.getOrNull(index)?.let { image ->
        loadOcrForImage(image)
      }
    }
  }

  fun onPageChanged(index: Int) {
    val allImages = _uiState.value.allImages
    if (index < 0 || index >= allImages.size) return

    val cached = ocrCache[allImages[index].id]
    _uiState.update {
      it.copy(
        currentIndex = index,
        ocrResults = cached?.first ?: emptyList(),
        imageWidth = cached?.second?.first ?: 0,
        imageHeight = cached?.second?.second ?: 0,
        isProcessing = cached == null,
      )
    }

    if (cached == null) {
      viewModelScope.launch {
        loadOcrForImage(allImages[index])
      }
    }
  }

  private suspend fun loadOcrForImage(image: GalleryImage) {
    val cachedResults = repository.getCachedOcrResults(image)

    if (cachedResults != null) {
      val cachedData = ocrService.getOrProcess(image)
      ocrCache[image.id] = cachedData.results to (cachedData.width to cachedData.height)
      if (_uiState.value.currentImage?.id == image.id) {
        _uiState.update {
          it.copy(
            isProcessing = false,
            ocrResults = cachedData.results,
            imageWidth = cachedData.width,
            imageHeight = cachedData.height,
          )
        }
      }
      return
    }

    val result = withContext(Dispatchers.IO) {
      runCatching {
        ocrService.getOrProcess(image)
      }
    }

    result.fold(
      onSuccess = { processed ->
        ocrCache[image.id] = processed.results to (processed.width to processed.height)
        if (_uiState.value.currentImage?.id == image.id) {
          _uiState.update {
            it.copy(
              isProcessing = false,
              ocrResults = processed.results,
              imageWidth = processed.width,
              imageHeight = processed.height,
            )
          }
        }
      },
      onFailure = { error ->
        if (_uiState.value.currentImage?.id == image.id) {
          _uiState.update {
            it.copy(
              isProcessing = false,
              error = error.message,
            )
          }
        }
      },
    )
  }

  fun copyAllText() {
    val allText = _uiState.value.ocrResults.joinToString("\n") { it.text }

    if (allText.isNotBlank()) {
      val context = getApplication<Application>()
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val clip = ClipData.newPlainText("OCR Text", allText)
      clipboard.setPrimaryClip(clip)
      _uiState.update { it.copy(textCopiedEvent = TextCopiedEvent(allText)) }
    }
  }

  fun copyText(text: String) {
    if (text.isNotBlank()) {
      val context = getApplication<Application>()
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val clip = ClipData.newPlainText("OCR Text", text)
      clipboard.setPrimaryClip(clip)
      _uiState.update { it.copy(textCopiedEvent = TextCopiedEvent(text)) }
    }
  }

  fun clearCopyEvent() {
    _uiState.update { it.copy(textCopiedEvent = null) }
  }
}
