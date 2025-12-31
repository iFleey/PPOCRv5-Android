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

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.fleey.ppocrv5.data.GalleryRepository
import me.fleey.ppocrv5.data.OcrProcessingManager
import me.fleey.ppocrv5.ui.state.GalleryUiState

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

  private val _uiState = MutableStateFlow(GalleryUiState())
  val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

  private val repository = GalleryRepository.getInstance(application)
  private val ocrProcessor = OcrProcessingManager.getInstance(application)

  init {
    loadImages()
    observeNewImages()
    ocrProcessor.processUnprocessedImages()
  }

  private fun observeNewImages() {
    viewModelScope.launch {
      repository.imageAdded.collect { newImage ->
        _uiState.update { state ->
          if (state.images.none { it.id == newImage.id }) {
            state.copy(images = listOf(newImage) + state.images)
          } else {
            state
          }
        }
      }
    }
  }

  fun importImage(uri: Uri) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }

      // Image will be added via imageAdded flow observer
      repository.importImage(uri)

      _uiState.update { it.copy(isLoading = false) }
    }
  }

  fun deleteImage(imageId: String) {
    viewModelScope.launch {
      val success = repository.deleteImage(imageId)
      if (success) {
        _uiState.update { state ->
          state.copy(
            images = state.images.filter { it.id != imageId },
            selectedIds = state.selectedIds - imageId,
          )
        }
      }
    }
  }

  fun enterSelectionMode(imageId: String) {
    _uiState.update { state ->
      state.copy(
        isSelectionMode = true,
        selectedIds = setOf(imageId),
      )
    }
  }

  fun exitSelectionMode() {
    _uiState.update { state ->
      state.copy(
        isSelectionMode = false,
        selectedIds = emptySet(),
      )
    }
  }

  fun toggleSelection(imageId: String) {
    _uiState.update { state ->
      val newSelectedIds = if (imageId in state.selectedIds) {
        state.selectedIds - imageId
      } else {
        state.selectedIds + imageId
      }
      if (newSelectedIds.isEmpty()) {
        state.copy(isSelectionMode = false, selectedIds = emptySet())
      } else {
        state.copy(selectedIds = newSelectedIds)
      }
    }
  }

  fun selectAll() {
    _uiState.update { state ->
      state.copy(selectedIds = state.images.map { it.id }.toSet())
    }
  }

  fun deselectAll() {
    _uiState.update { state ->
      state.copy(isSelectionMode = false, selectedIds = emptySet())
    }
  }

  fun deleteSelected() {
    viewModelScope.launch {
      val idsToDelete = _uiState.value.selectedIds.toList()
      idsToDelete.forEach { imageId ->
        repository.deleteImage(imageId)
      }
      _uiState.update { state ->
        state.copy(
          images = state.images.filter { it.id !in idsToDelete },
          isSelectionMode = false,
          selectedIds = emptySet(),
        )
      }
    }
  }

  fun loadImages() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }

      val images = repository.loadImages()

      _uiState.update {
        it.copy(
          images = images,
          isLoading = false,
        )
      }
    }
  }
}
