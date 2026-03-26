/*
 * Copyright (C) 2026 Fleey
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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.fleey.ppocrv5.ocr.OcrEngine
import me.fleey.ppocrv5.ocr.OcrResult
import me.fleey.ppocrv5.ui.model.GalleryImage

class GalleryOcrService private constructor(private val context: Context) {

  private val repository = GalleryRepository.getInstance(context)
  private val processMutex = Mutex()
  private var ocrEngine: OcrEngine? = null

  suspend fun getOrProcess(image: GalleryImage): ProcessedOcrImage = withContext(Dispatchers.IO) {
    repository.getCachedOcrResults(image)?.let { cachedResults ->
      val dimensions = loadImageDimensions(image.uri.toUri())
      return@withContext ProcessedOcrImage(
        results = cachedResults,
        width = dimensions.first,
        height = dimensions.second,
      )
    }

    val uri = image.uri.toUri()
    val bitmap = decodeBitmapForOcr(uri)
      ?: throw IllegalStateException("Failed to load image for OCR: $uri")

    try {
      val dimensions = bitmap.width to bitmap.height
      val results = processMutex.withLock {
        repository.getImage(image.id)?.let { latestImage ->
          repository.getCachedOcrResults(latestImage)
        } ?: run {
          val engine = ocrEngine ?: OcrEngine.create(context).getOrThrow().also { created ->
            ocrEngine = created
          }
          engine.process(bitmap).also { processed ->
            repository.updateImageOcr(image.id, processed)
          }
        }
      }

      ProcessedOcrImage(
        results = results,
        width = dimensions.first,
        height = dimensions.second,
      )
    } finally {
      if (!bitmap.isRecycled) {
        bitmap.recycle()
      }
    }
  }

  private fun decodeBitmapForOcr(uri: Uri): Bitmap? {
    val options = BitmapFactory.Options().apply {
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
      BitmapFactory.decodeStream(input, null, options)
    } ?: return null

    if (decoded.config == Bitmap.Config.ARGB_8888) {
      return decoded
    }

    Log.w(TAG, "Converting bitmap config ${decoded.config} to ARGB_8888 for OCR")
    return decoded.copy(Bitmap.Config.ARGB_8888, false)?.also { converted ->
      if (converted !== decoded) {
        decoded.recycle()
      }
    } ?: decoded
  }

  private fun loadImageDimensions(uri: Uri): Pair<Int, Int> {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { input ->
      BitmapFactory.decodeStream(input, null, options)
    }
    return options.outWidth to options.outHeight
  }

  data class ProcessedOcrImage(
    val results: List<OcrResult>,
    val width: Int,
    val height: Int,
  )

  companion object {
    private const val TAG = "GalleryOcrService"

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var instance: GalleryOcrService? = null

    fun getInstance(context: Context): GalleryOcrService {
      return instance ?: synchronized(this) {
        instance ?: GalleryOcrService(context.applicationContext).also { instance = it }
      }
    }
  }
}
