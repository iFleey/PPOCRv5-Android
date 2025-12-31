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

import me.fleey.ppocrv5.ocr.AcceleratorType
import me.fleey.ppocrv5.ocr.Benchmark
import me.fleey.ppocrv5.ocr.OcrResult
import me.fleey.ppocrv5.ocr.ResolutionPreset

sealed interface CameraUiState {
  data object Loading : CameraUiState

  data class Ready(
    val ocrResults: List<OcrResult> = emptyList(),
    val benchmark: Benchmark = Benchmark(),
    val acceleratorType: AcceleratorType = AcceleratorType.GPU,
    val resolutionPreset: ResolutionPreset = ResolutionPreset.DEFAULT,
    val benchmarkExpanded: Boolean = true,
    val flashEnabled: Boolean = false,
    val frozen: Boolean = false,
    val useFrontCamera: Boolean = false,
    val captureFlash: Boolean = false,
  ) : CameraUiState

  data class Error(val type: ErrorType) : CameraUiState
}

sealed interface ErrorType {
  data object CameraPermissionDenied : ErrorType
  data object ModelLoadFailed : ErrorType
  data class AcceleratorInitFailed(val fallbackTo: AcceleratorType?) : ErrorType
}
