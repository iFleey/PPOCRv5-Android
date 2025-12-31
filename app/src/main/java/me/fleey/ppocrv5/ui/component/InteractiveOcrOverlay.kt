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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.sp
import me.fleey.ppocrv5.ocr.OcrResult
import kotlin.math.atan2
import kotlin.math.sqrt

@Composable
fun InteractiveOcrOverlay(
  results: List<OcrResult>,
  sourceWidth: Int,
  sourceHeight: Int,
  scale: Float,
  offset: Offset,
  onTextCopied: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val highlightColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
  val strokeColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
  val selectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
  val textColor = MaterialTheme.colorScheme.onPrimaryContainer
  val hapticFeedback = LocalHapticFeedback.current
  val density = LocalDensity.current

  var selectedResultIndex by remember { mutableStateOf<Int?>(null) }

  BoxWithConstraints(modifier = modifier) {
    val canvasWidth = constraints.maxWidth.toFloat()
    val canvasHeight = constraints.maxHeight.toFloat()

    if (sourceWidth <= 0 || sourceHeight <= 0) return@BoxWithConstraints

    val sourceAspect = sourceWidth.toFloat() / sourceHeight
    val canvasAspect = canvasWidth / canvasHeight

    val imageScale: Float
    val imageOffsetX: Float
    val imageOffsetY: Float

    if (sourceAspect > canvasAspect) {
      imageScale = canvasWidth / sourceWidth
      imageOffsetX = 0f
      imageOffsetY = (canvasHeight - sourceHeight * imageScale) / 2f
    } else {
      imageScale = canvasHeight / sourceHeight
      imageOffsetX = (canvasWidth - sourceWidth * imageScale) / 2f
      imageOffsetY = 0f
    }

    val baseFontSizePx = with(density) { 12.sp.toPx() }

    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer(
          scaleX = scale,
          scaleY = scale,
          translationX = offset.x,
          translationY = offset.y,
        )
        .pointerInput(results, imageScale, imageOffsetX, imageOffsetY) {
          detectTapGestures(
            onLongPress = { tapOffset ->
              val index = findTappedResult(
                results = results,
                tapOffset = tapOffset,
                scale = imageScale,
                offsetX = imageOffsetX,
                offsetY = imageOffsetY,
              )
              if (index != null) {
                selectedResultIndex = index
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onTextCopied(results[index].text)
              }
            },
            onTap = {
              selectedResultIndex = null
            },
          )
        },
    ) {
      results.forEachIndexed { index, result ->
        val isSelected = selectedResultIndex == index
        drawOcrRegion(
          result = result,
          scale = imageScale,
          offsetX = imageOffsetX,
          offsetY = imageOffsetY,
          fillColor = if (isSelected) selectedColor else highlightColor,
          strokeColor = strokeColor,
          textColor = textColor,
          baseFontSizePx = baseFontSizePx,
        )
      }
    }
  }
}

private fun DrawScope.drawOcrRegion(
  result: OcrResult,
  scale: Float,
  offsetX: Float,
  offsetY: Float,
  fillColor: Color,
  strokeColor: Color,
  textColor: Color,
  baseFontSizePx: Float,
) {
  val corners = result.box.getCorners()
  val scaledCorners = corners.map { corner ->
    PointF(corner.x * scale + offsetX, corner.y * scale + offsetY)
  }

  val path = Path().apply {
    moveTo(scaledCorners[0].x, scaledCorners[0].y)
    scaledCorners.drop(1).forEach { corner ->
      lineTo(corner.x, corner.y)
    }
    close()
  }

  drawPath(
    path = path,
    color = fillColor,
  )

  drawPath(
    path = path,
    color = strokeColor,
    style = Stroke(width = 2f),
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

  val centerX = scaledCorners.map { it.x }.average().toFloat()
  val centerY = scaledCorners.map { it.y }.average().toFloat()

  val textPaint = android.graphics.Paint().apply {
    color = textColor.toArgb()
    isAntiAlias = true
    textAlign = android.graphics.Paint.Align.CENTER
  }

  val maxFontSize = boxHeight * 0.8f
  var fontSize = minOf(baseFontSizePx, maxFontSize)
  textPaint.textSize = fontSize

  var textWidth = textPaint.measureText(result.text)
  while (textWidth > boxWidth * 0.95f && fontSize > 6f) {
    fontSize *= 0.9f
    textPaint.textSize = fontSize
    textWidth = textPaint.measureText(result.text)
  }

  val canvas = drawContext.canvas.nativeCanvas
  canvas.save()
  canvas.rotate(Math.toDegrees(angle.toDouble()).toFloat(), centerX, centerY)

  val textMetrics = textPaint.fontMetrics
  val textY = centerY - (textMetrics.ascent + textMetrics.descent) / 2

  canvas.drawText(result.text, centerX, textY, textPaint)
  canvas.restore()
}

private fun findTappedResult(
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

    if (intersect) {
      inside = !inside
    }
    j = i
  }

  return inside
}
