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

import android.util.Size
import androidx.annotation.StringRes
import me.fleey.ppocrv5.R

enum class ResolutionPreset(
  val size: Size,
  @param:StringRes val labelRes: Int,
) {
  RES_320x240(Size(320, 240), R.string.resolution_320x240),
  RES_640x480(Size(640, 480), R.string.resolution_640x480),
  RES_800x600(Size(800, 600), R.string.resolution_800x600),
  RES_1024x768(Size(1024, 768), R.string.resolution_1024x768),
  RES_1280x960(Size(1280, 960), R.string.resolution_1280x960),
  RES_1600x1200(Size(1600, 1200), R.string.resolution_1600x1200),
  RES_2048x1536(Size(2048, 1536), R.string.resolution_2048x1536),

  RES_320x180(Size(320, 180), R.string.resolution_320x180),
  RES_640x360(Size(640, 360), R.string.resolution_640x360),
  RES_854x480(Size(854, 480), R.string.resolution_854x480),
  RES_1280x720(Size(1280, 720), R.string.resolution_1280x720),
  RES_1920x1080(Size(1920, 1080), R.string.resolution_1920x1080),
  RES_2560x1440(Size(2560, 1440), R.string.resolution_2560x1440),
  RES_3840x2160(Size(3840, 2160), R.string.resolution_3840x2160),

  RES_240x240(Size(240, 240), R.string.resolution_240x240),
  RES_480x480(Size(480, 480), R.string.resolution_480x480),
  RES_640x640(Size(640, 640), R.string.resolution_640x640),
  RES_720x720(Size(720, 720), R.string.resolution_720x720),
  RES_1080x1080(Size(1080, 1080), R.string.resolution_1080x1080);

  val width: Int get() = size.width
  val height: Int get() = size.height
  val aspectRatio: Float get() = width.toFloat() / height.toFloat()
  val megapixels: Float get() = (width * height) / 1_000_000f

  companion object {
    val DEFAULT = RES_320x180

    fun getByAspectRatio(ratio: AspectRatio): List<ResolutionPreset> = when (ratio) {
      AspectRatio.RATIO_4_3 -> listOf(
        RES_320x240, RES_640x480, RES_800x600, RES_1024x768,
        RES_1280x960, RES_1600x1200, RES_2048x1536,
      )

      AspectRatio.RATIO_16_9 -> listOf(
        RES_320x180, RES_640x360, RES_854x480, RES_1280x720,
        RES_1920x1080, RES_2560x1440, RES_3840x2160,
      )

      AspectRatio.RATIO_1_1 -> listOf(
        RES_240x240, RES_480x480, RES_640x640, RES_720x720, RES_1080x1080,
      )
    }

    fun getLowLatencyPresets(): List<ResolutionPreset> = listOf(
      RES_320x240, RES_320x180, RES_240x240, RES_640x480, RES_640x360, RES_480x480,
    )

    fun getBalancedPresets(): List<ResolutionPreset> = listOf(
      RES_800x600, RES_854x480, RES_640x640, RES_1024x768, RES_1280x720, RES_720x720,
    )

    fun getHighQualityPresets(): List<ResolutionPreset> = listOf(
      RES_1280x960, RES_1600x1200, RES_2048x1536,
      RES_1920x1080, RES_2560x1440, RES_3840x2160,
      RES_1080x1080,
    )
  }
}

enum class AspectRatio(val value: Float, @param:StringRes val labelRes: Int) {
  RATIO_4_3(4f / 3f, R.string.aspect_ratio_4_3),
  RATIO_16_9(16f / 9f, R.string.aspect_ratio_16_9),
  RATIO_1_1(1f, R.string.aspect_ratio_1_1);

  companion object {
    fun fromSize(size: Size): AspectRatio {
      val ratio = size.width.toFloat() / size.height.toFloat()
      return entries.minByOrNull { kotlin.math.abs(it.value - ratio) } ?: RATIO_4_3
    }
  }
}
