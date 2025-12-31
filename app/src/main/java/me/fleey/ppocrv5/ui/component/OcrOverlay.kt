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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.core.graphics.withRotation
import me.fleey.ppocrv5.ocr.OcrResult
import kotlin.math.atan2
import kotlin.math.sqrt

private val HighConfidenceColor = Color(0xFF4CAF50)
private val MediumConfidenceColor = Color(0xFFFFC107)
private val LowConfidenceColor = Color(0xFFF44336)

private const val HIGH_CONFIDENCE_THRESHOLD = 0.8f
private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.5f

private const val BOX_STROKE_WIDTH = 2f
private const val TEXT_PADDING = 4f

@Composable
fun OcrOverlay(
  results: List<OcrResult>,
  sourceWidth: Int,
  sourceHeight: Int,
  modifier: Modifier = Modifier,
) {
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

  Canvas(modifier = modifier) {
    if (sourceWidth <= 0 || sourceHeight <= 0) return@Canvas
    if (results.isEmpty()) return@Canvas

    val canvasWidth = size.width
    val canvasHeight = size.height
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

    results.forEach { result ->
      drawOcrResult(
        result = result,
        scale = scale,
        offsetX = offsetX,
        offsetY = offsetY,
        textPaint = textPaint,
        backgroundPaint = backgroundPaint,
      )
    }
  }
}

private fun DrawScope.drawOcrResult(
  result: OcrResult,
  scale: Float,
  offsetX: Float,
  offsetY: Float,
  textPaint: android.graphics.Paint,
  backgroundPaint: android.graphics.Paint,
) {
  val boxColor = getConfidenceColor(result.confidence)
  val corners = result.box.getCorners()
  val scaledCorners = corners.map { corner ->
    PointF(corner.x * scale + offsetX, corner.y * scale + offsetY)
  }

  drawBoxWithText(
    text = result.text,
    confidence = result.confidence,
    corners = scaledCorners,
    boxColor = boxColor,
    textPaint = textPaint,
    backgroundPaint = backgroundPaint,
  )
}

private fun DrawScope.drawBoxWithText(
  text: String,
  confidence: Float,
  corners: List<PointF>,
  boxColor: Color,
  textPaint: android.graphics.Paint,
  backgroundPaint: android.graphics.Paint,
) {
  if (corners.size != 4 || text.isEmpty()) return

  val path = Path().apply {
    moveTo(corners[0].x, corners[0].y)
    lineTo(corners[1].x, corners[1].y)
    lineTo(corners[2].x, corners[2].y)
    lineTo(corners[3].x, corners[3].y)
    close()
  }

  backgroundPaint.color = boxColor.copy(alpha = 0.7f).toArgb()
  val nativePath = android.graphics.Path().apply {
    moveTo(corners[0].x, corners[0].y)
    lineTo(corners[1].x, corners[1].y)
    lineTo(corners[2].x, corners[2].y)
    lineTo(corners[3].x, corners[3].y)
    close()
  }
  drawContext.canvas.nativeCanvas.drawPath(nativePath, backgroundPaint)

  drawPath(
    path = path,
    color = boxColor,
    style = Stroke(width = BOX_STROKE_WIDTH),
  )

  val topLeft = corners[0]
  val topRight = corners[1]
  val bottomLeft = corners[3]

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

  val displayText = "$text (${(confidence * 100).toInt()}%)"

  val maxTextWidth = boxWidth - TEXT_PADDING * 2
  val maxTextHeight = boxHeight - TEXT_PADDING * 2

  var textSize = maxTextHeight * 0.8f
  textPaint.textSize = textSize

  var textWidth = textPaint.measureText(displayText)
  if (textWidth > maxTextWidth && maxTextWidth > 0) {
    textSize *= (maxTextWidth / textWidth)
    textPaint.textSize = textSize
    textWidth = textPaint.measureText(displayText)
  }

  if (textSize < 8f) {
    textPaint.textSize = 8f
  }

  val centerX = (topLeft.x + topRight.x + corners[2].x + bottomLeft.x) / 4
  val centerY = (topLeft.y + topRight.y + corners[2].y + bottomLeft.y) / 4

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
