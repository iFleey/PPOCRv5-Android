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

package me.fleey.ppocrv5.ui.component

import android.graphics.PointF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.graphics.withRotation
import me.fleey.ppocrv5.ocr.OcrResult
import kotlin.math.atan2
import kotlin.math.sqrt

private val HighConfidenceColor = Color(0xFF4CAF50)
private val MediumConfidenceColor = Color(0xFFFFC107)
private val LowConfidenceColor = Color(0xFFF44336)
private val SelectedColor = Color(0xFF2196F3)

private const val HIGH_CONFIDENCE_THRESHOLD = 0.8f
private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.5f
private const val BOX_STROKE_WIDTH = 3f
private const val TEXT_PADDING = 4f

@Composable
fun ImageViewerOcrOverlay(
  results: List<OcrResult>,
  sourceWidth: Int,
  sourceHeight: Int,
  onTextCopied: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val hapticFeedback = LocalHapticFeedback.current
  var selectedIndex by remember { mutableStateOf<Int?>(null) }

  val textPaint = remember {
    android.graphics.Paint().apply {
      color = android.graphics.Color.WHITE
      isAntiAlias = true
      typeface = Typeface.DEFAULT_BOLD
    }
  }

  val backgroundPaint = remember {
    android.graphics.Paint().apply {
      style = android.graphics.Paint.Style.FILL
    }
  }

  BoxWithConstraints(modifier = modifier) {
    val canvasWidth = constraints.maxWidth.toFloat()
    val canvasHeight = constraints.maxHeight.toFloat()

    if (sourceWidth <= 0 || sourceHeight <= 0) return@BoxWithConstraints

    val sourceAspect = sourceWidth.toFloat() / sourceHeight
    val canvasAspect = canvasWidth / canvasHeight

    val scale: Float
    val offsetX: Float
    val offsetY: Float

    if (sourceAspect > canvasAspect) {
      scale = canvasWidth / sourceWidth
      offsetX = 0f
      offsetY = (canvasHeight - sourceHeight * scale) / 2f
    } else {
      scale = canvasHeight / sourceHeight
      offsetX = (canvasWidth - sourceWidth * scale) / 2f
      offsetY = 0f
    }

    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(results, scale, offsetX, offsetY) {
          detectTapGestures(
            onTap = { tapOffset ->
              val index = findTappedResultIndex(results, tapOffset, scale, offsetX, offsetY)
              if (index != null) {
                selectedIndex = index
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onTextCopied(results[index].text)
              } else {
                selectedIndex = null
              }
            },
          )
        },
    ) {
      results.forEachIndexed { index, result ->
        val isSelected = selectedIndex == index
        drawOcrBox(
          result = result,
          scale = scale,
          offsetX = offsetX,
          offsetY = offsetY,
          isSelected = isSelected,
          textPaint = textPaint,
          backgroundPaint = backgroundPaint,
        )
      }
    }
  }
}

private fun DrawScope.drawOcrBox(
  result: OcrResult,
  scale: Float,
  offsetX: Float,
  offsetY: Float,
  isSelected: Boolean,
  textPaint: android.graphics.Paint,
  backgroundPaint: android.graphics.Paint,
) {
  val boxColor = if (isSelected) SelectedColor else getConfidenceColor(result.confidence)
  val corners = result.box.getCorners()
  val scaledCorners = corners.map { corner ->
    PointF(corner.x * scale + offsetX, corner.y * scale + offsetY)
  }

  if (scaledCorners.size != 4 || result.text.isEmpty()) return

  val path = Path().apply {
    moveTo(scaledCorners[0].x, scaledCorners[0].y)
    lineTo(scaledCorners[1].x, scaledCorners[1].y)
    lineTo(scaledCorners[2].x, scaledCorners[2].y)
    lineTo(scaledCorners[3].x, scaledCorners[3].y)
    close()
  }

  backgroundPaint.color = boxColor.copy(alpha = if (isSelected) 0.5f else 0.7f).toArgb()
  val nativePath = android.graphics.Path().apply {
    moveTo(scaledCorners[0].x, scaledCorners[0].y)
    lineTo(scaledCorners[1].x, scaledCorners[1].y)
    lineTo(scaledCorners[2].x, scaledCorners[2].y)
    lineTo(scaledCorners[3].x, scaledCorners[3].y)
    close()
  }
  drawContext.canvas.nativeCanvas.drawPath(nativePath, backgroundPaint)

  drawPath(
    path = path,
    color = if (isSelected) SelectedColor else boxColor,
    style = Stroke(width = if (isSelected) BOX_STROKE_WIDTH * 1.5f else BOX_STROKE_WIDTH),
  )

  val topLeft = scaledCorners[0]
  val topRight = scaledCorners[1]
  val bottomLeft = scaledCorners[3]

  val boxWidth = sqrt(
    (topRight.x - topLeft.x) * (topRight.x - topLeft.x) +
      (topRight.y - topLeft.y) * (topRight.y - topLeft.y),
  )
  val boxHeight = sqrt(
    (bottomLeft.x - topLeft.x) * (bottomLeft.x - topLeft.x) +
      (bottomLeft.y - topLeft.y) * (bottomLeft.y - topLeft.y),
  )

  val angle = atan2(
    (topRight.y - topLeft.y).toDouble(),
    (topRight.x - topLeft.x).toDouble(),
  ).toFloat()

  val displayText = result.text

  val maxTextWidth = boxWidth - TEXT_PADDING * 2
  val maxTextHeight = boxHeight - TEXT_PADDING * 2

  var textSize = maxTextHeight * 0.8f
  textPaint.textSize = textSize

  var textWidth = textPaint.measureText(displayText)
  if (textWidth > maxTextWidth && maxTextWidth > 0) {
    textSize *= (maxTextWidth / textWidth)
    textPaint.textSize = textSize
  }

  if (textSize < 8f) {
    textPaint.textSize = 8f
  }

  val centerX = (topLeft.x + topRight.x + scaledCorners[2].x + bottomLeft.x) / 4
  val centerY = (topLeft.y + topRight.y + scaledCorners[2].y + bottomLeft.y) / 4

  val canvas = drawContext.canvas.nativeCanvas
  canvas.withRotation(Math.toDegrees(angle.toDouble()).toFloat(), centerX, centerY) {
    val finalTextWidth = textPaint.measureText(displayText)
    val textMetrics = textPaint.fontMetrics
    val textHeight = textMetrics.descent - textMetrics.ascent

    drawText(
      displayText,
      centerX - finalTextWidth / 2,
      centerY + textHeight / 2 - textMetrics.descent,
      textPaint,
    )
  }
}

private fun findTappedResultIndex(
  results: List<OcrResult>,
  tapOffset: Offset,
  scale: Float,
  offsetX: Float,
  offsetY: Float,
): Int? {
  results.forEachIndexed { index, result ->
    val corners = result.box.getCorners()
    val scaledCorners = corners.map { corner ->
      PointF(corner.x * scale + offsetX, corner.y * scale + offsetY)
    }
    if (isPointInPolygon(tapOffset, scaledCorners)) {
      return index
    }
  }
  return null
}

private fun isPointInPolygon(point: Offset, polygon: List<PointF>): Boolean {
  if (polygon.size < 3) return false
  var inside = false
  var j = polygon.size - 1
  for (i in polygon.indices) {
    val xi = polygon[i].x
    val yi = polygon[i].y
    val xj = polygon[j].x
    val yj = polygon[j].y
    val intersect = ((yi > point.y) != (yj > point.y)) &&
      (point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi)
    if (intersect) inside = !inside
    j = i
  }
  return inside
}

private fun getConfidenceColor(confidence: Float): Color {
  return when {
    confidence >= HIGH_CONFIDENCE_THRESHOLD -> HighConfidenceColor
    confidence >= MEDIUM_CONFIDENCE_THRESHOLD -> MediumConfidenceColor
    else -> LowConfidenceColor
  }
}

private fun Color.toArgb(): Int {
  return android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
  )
}
