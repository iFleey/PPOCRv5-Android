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

package me.fleey.ppocrv5.ui.state

import me.fleey.ppocrv5.ocr.OcrResult
import me.fleey.ppocrv5.ui.model.GalleryImage

data class ImageViewerUiState(
  val isProcessing: Boolean = false,
  val ocrResults: List<OcrResult> = emptyList(),
  val error: String? = null,
  val imageWidth: Int = 0,
  val imageHeight: Int = 0,
  val textCopiedEvent: TextCopiedEvent? = null,
  val allImages: List<GalleryImage> = emptyList(),
  val currentIndex: Int = 0,
) {
  val currentImage: GalleryImage? get() = allImages.getOrNull(currentIndex)
  val hasPrevious: Boolean get() = currentIndex > 0
  val hasNext: Boolean get() = currentIndex < allImages.size - 1
}

data class TextCopiedEvent(
  val text: String,
  val timestamp: Long = System.currentTimeMillis(),
)
