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
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.fleey.ppocrv5.ocr.OcrResult
import me.fleey.ppocrv5.ui.model.GalleryImage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "GalleryRepository"

class GalleryRepository private constructor(private val context: Context) {

  private val galleryDir: File = File(context.filesDir, GALLERY_DIR_NAME).apply {
    if (!exists()) mkdirs()
  }

  private val indexFile: File = File(galleryDir, INDEX_FILE_NAME)
  private val mutex = Mutex()

  private val _imageAdded = MutableSharedFlow<GalleryImage>()
  val imageAdded: SharedFlow<GalleryImage> = _imageAdded.asSharedFlow()

  suspend fun saveCapture(bitmap: Bitmap): GalleryImage? = withContext(Dispatchers.IO) {
    try {
      val imageId = UUID.randomUUID().toString()
      val destFile = File(galleryDir, "$imageId.jpg")

      Log.d(TAG, "Saving capture to: ${destFile.absolutePath}")

      saveToMediaStore(bitmap)

      FileOutputStream(destFile).use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
      }

      Log.d(TAG, "File saved, exists: ${destFile.exists()}, size: ${destFile.length()}")

      val fileUri = Uri.fromFile(destFile).toString()
      Log.d(TAG, "File URI: $fileUri")

      val image = GalleryImage(
        id = imageId,
        uri = fileUri,
        timestamp = System.currentTimeMillis(),
      )

      mutex.withLock {
        val images = loadIndexInternal().toMutableList()
        images.add(0, image)
        saveIndexInternal(images)
        Log.d(TAG, "Index saved, total images: ${images.size}")
      }

      _imageAdded.emit(image)
      Log.d(TAG, "Image added event emitted")
      image
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save capture", e)
      null
    }
  }

  private fun saveToMediaStore(bitmap: Bitmap) {
    try {
      val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
      val filename = "PPOCRv5_$timestamp.jpg"

      val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PPOCRv5")
          put(MediaStore.Images.Media.IS_PENDING, 1)
        }
      }

      val resolver = context.contentResolver
      val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

      Log.d(TAG, "MediaStore URI: $uri")

      uri?.let { mediaUri ->
        resolver.openOutputStream(mediaUri)?.use { output ->
          bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          contentValues.clear()
          contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
          resolver.update(mediaUri, contentValues, null, null)
        }
        Log.d(TAG, "Saved to MediaStore successfully")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save to MediaStore", e)
    }
  }

  suspend fun importImage(sourceUri: Uri): GalleryImage? =
    withContext(Dispatchers.IO) {
      try {
        val imageId = UUID.randomUUID().toString()
        val destFile = File(galleryDir, "$imageId.jpg")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
          FileOutputStream(destFile).use { output ->
            input.copyTo(output)
          }
        }

        val image = GalleryImage(
          id = imageId,
          uri = destFile.toURI().toString(),
          timestamp = System.currentTimeMillis(),
        )

        mutex.withLock {
          val images = loadIndexInternal().toMutableList()
          images.add(0, image)
          saveIndexInternal(images)
        }

        _imageAdded.emit(image)
        image
      } catch (e: Exception) {
        null
      }
    }

  suspend fun deleteImage(imageId: String): Boolean = withContext(Dispatchers.IO) {
    mutex.withLock {
      try {
        val images = loadIndexInternal().toMutableList()
        val image = images.find { it.id == imageId } ?: return@withContext false

        val file = File(image.uri.toUri().path ?: "")
        if (file.exists()) {
          file.delete()
        }

        images.removeAll { it.id == imageId }
        saveIndexInternal(images)
        true
      } catch (e: Exception) {
        false
      }
    }
  }

  suspend fun loadImages(): List<GalleryImage> = withContext(Dispatchers.IO) {
    mutex.withLock {
      loadIndexInternal()
    }
  }

  suspend fun getImage(imageId: String): GalleryImage? = withContext(Dispatchers.IO) {
    mutex.withLock {
      loadIndexInternal().find { it.id == imageId }
    }
  }

  suspend fun getAllImages(): List<GalleryImage> = withContext(Dispatchers.IO) {
    mutex.withLock {
      loadIndexInternal()
    }
  }

  fun getImageIndex(imageId: String, images: List<GalleryImage>): Int {
    return images.indexOfFirst { it.id == imageId }
  }

  suspend fun updateImageOcr(imageId: String, ocrResults: List<OcrResult>) {
    withContext(Dispatchers.IO) {
      mutex.withLock {
        val images = loadIndexInternal().toMutableList()
        val index = images.indexOfFirst { it.id == imageId }
        if (index >= 0) {
          val json = serializeOcrResults(ocrResults)
          images[index] = images[index].copy(
            ocrResultsJson = json,
            ocrProcessed = true,
          )
          saveIndexInternal(images)
        }
      }
    }
  }

  fun getCachedOcrResults(image: GalleryImage): List<OcrResult>? {
    if (!image.ocrProcessed || image.ocrResultsJson == null) return null
    return deserializeOcrResults(image.ocrResultsJson)
  }

  private fun serializeOcrResults(results: List<OcrResult>): String {
    val jsonArray = JSONArray()
    results.forEach { result ->
      val obj = JSONObject().apply {
        put("text", result.text)
        put("confidence", result.confidence.toDouble())
        put("centerX", result.centerX.toDouble())
        put("centerY", result.centerY.toDouble())
        put("width", result.width.toDouble())
        put("height", result.height.toDouble())
        put("angle", result.angle.toDouble())
      }
      jsonArray.put(obj)
    }
    return jsonArray.toString()
  }

  private fun deserializeOcrResults(json: String): List<OcrResult> {
    return try {
      val jsonArray = JSONArray(json)
      (0 until jsonArray.length()).map { i ->
        val obj = jsonArray.getJSONObject(i)
        OcrResult(
          text = obj.getString("text"),
          confidence = obj.getDouble("confidence").toFloat(),
          centerX = obj.getDouble("centerX").toFloat(),
          centerY = obj.getDouble("centerY").toFloat(),
          width = obj.getDouble("width").toFloat(),
          height = obj.getDouble("height").toFloat(),
          angle = obj.getDouble("angle").toFloat(),
        )
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun loadIndexInternal(): List<GalleryImage> {
    if (!indexFile.exists()) return emptyList()

    return try {
      indexFile.readLines()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
          val parts = line.split(DELIMITER)
          if (parts.size >= 3) {
            val id = parts[0]
            val uri = parts[1]
            val timestamp = parts[2].toLongOrNull() ?: 0L
            val ocrJson = parts.getOrNull(3)?.let {
              if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
            }
            val ocrProcessed = parts.getOrNull(4)?.toBooleanStrictOrNull() ?: false

            val filePath = try {
              uri.toUri().path
            } catch (e: Exception) {
              uri.removePrefix("file://")
            }

            val file = File(filePath ?: "")
            if (file.exists()) {
              GalleryImage(
                id = id,
                uri = uri,
                timestamp = timestamp,
                ocrResultsJson = ocrJson,
                ocrProcessed = ocrProcessed,
              )
            } else {
              Log.w(TAG, "File not found: $filePath")
              null
            }
          } else {
            null
          }
        }
        .sortedByDescending { it.timestamp }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load index", e)
      emptyList()
    }
  }

  private fun saveIndexInternal(images: List<GalleryImage>) {
    try {
      val content = images.joinToString("\n") { image ->
        val encodedJson = image.ocrResultsJson?.let { URLEncoder.encode(it, "UTF-8") } ?: ""
        "${image.id}$DELIMITER${image.uri}$DELIMITER${image.timestamp}$DELIMITER$encodedJson$DELIMITER${image.ocrProcessed}"
      }
      indexFile.writeText(content)
    } catch (e: Exception) {
      // Silently fail
    }
  }

  companion object {
    private const val GALLERY_DIR_NAME = "gallery"
    private const val INDEX_FILE_NAME = "index.txt"
    private const val DELIMITER = "|"

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var instance: GalleryRepository? = null

    fun getInstance(context: Context): GalleryRepository {
      return instance ?: synchronized(this) {
        instance ?: GalleryRepository(context.applicationContext).also { instance = it }
      }
    }
  }
}
