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

package me.fleey.ppocrv5.ocr

/**
 * Hardware accelerator types for OCR inference.
 *
 * For FP16 models, GPU is recommended as the primary accelerator.
 * NPU (Hexagon DSP) is optimized for INT8, not FP16.
 */
enum class AcceleratorType(val value: Int) {
  /** GPU via OpenCL - recommended for FP16 models */
  GPU(0),

  /** CPU fallback - always available */
  CPU(1),

  /** NPU - not recommended for FP16, will fallback to GPU */
  @Deprecated("NPU is optimized for INT8, use GPU for FP16 models")
  NPU(2);

  companion object {
    fun fromValue(value: Int): AcceleratorType = when (value) {
      0 -> GPU
      1 -> CPU
      else -> GPU
    }
  }
}
