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

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.fleey.ppocrv5.R
import me.fleey.ppocrv5.ocr.AcceleratorType
import me.fleey.ppocrv5.ocr.Benchmark
import java.util.Locale

private const val ANIMATION_DURATION_MS = 300
private const val ROLLING_AVERAGE_WINDOW = 30

private val NpuColor = Color(0xFF4CAF50)
private val GpuColor = Color(0xFF2196F3)
private val CpuColor = Color(0xFFFF9800)

class RollingAverage(private val windowSize: Int = ROLLING_AVERAGE_WINDOW) {
  private val values = ArrayDeque<Float>(windowSize)

  fun add(value: Float): Float {
    if (value <= 0f) return average()
    if (values.size >= windowSize) {
      values.removeFirst()
    }
    values.addLast(value)
    return average()
  }

  fun average(): Float {
    if (values.isEmpty()) return 0f
    return values.sum() / values.size
  }

  fun reset() {
    values.clear()
  }
}

data class BenchmarkAverages(
  val detectionTimeMs: Float = 0f,
  val recognitionTimeMs: Float = 0f,
  val totalTimeMs: Float = 0f,
  val fps: Float = 0f,
)

@Composable
fun rememberBenchmarkAverages(benchmark: Benchmark): BenchmarkAverages {
  val detectionAvg = remember { RollingAverage() }
  val recognitionAvg = remember { RollingAverage() }
  val totalAvg = remember { RollingAverage() }
  val fpsAvg = remember { RollingAverage() }

  return remember(benchmark) {
    BenchmarkAverages(
      detectionTimeMs = detectionAvg.add(benchmark.detectionTimeMs),
      recognitionTimeMs = recognitionAvg.add(benchmark.recognitionTimeMs),
      totalTimeMs = totalAvg.add(benchmark.totalTimeMs),
      fps = fpsAvg.add(benchmark.fps),
    )
  }
}

@Composable
fun BenchmarkPanel(
  benchmark: Benchmark,
  acceleratorType: AcceleratorType,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val averages = rememberBenchmarkAverages(benchmark)
  val rotationAngle by animateFloatAsState(
    targetValue = if (expanded) 180f else 0f,
    animationSpec = tween(durationMillis = ANIMATION_DURATION_MS),
    label = "arrow_rotation",
  )

  Card(
    modifier = modifier,
    shape = RoundedCornerShape(0.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      BenchmarkHeader(
        acceleratorType = acceleratorType,
        fps = averages.fps,
        rotationAngle = rotationAngle,
        onClick = onToggleExpanded,
      )

      AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(animationSpec = tween(ANIMATION_DURATION_MS)),
        exit = shrinkVertically(animationSpec = tween(ANIMATION_DURATION_MS)),
      ) {
        BenchmarkDetails(averages = averages)
      }
    }
  }
}

@Composable
private fun BenchmarkHeader(
  acceleratorType: AcceleratorType,
  fps: Float,
  rotationAngle: Float,
  onClick: () -> Unit,
) {
  val fpsText = formatFps(fps)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      AcceleratorBadge(acceleratorType = acceleratorType)
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        text = if (fps > 0f) {
          stringResource(R.string.benchmark_fps, fpsText)
        } else {
          stringResource(R.string.benchmark_fps_placeholder)
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }

    Icon(
      imageVector = Icons.Default.KeyboardArrowDown,
      contentDescription = stringResource(R.string.benchmark_toggle_details),
      modifier = Modifier
        .size(24.dp)
        .rotate(rotationAngle),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Suppress("DEPRECATION")
@Composable
private fun AcceleratorBadge(acceleratorType: AcceleratorType) {
  val (color, labelRes) = when (acceleratorType) {
    AcceleratorType.NPU -> NpuColor to R.string.accelerator_npu
    AcceleratorType.GPU -> GpuColor to R.string.accelerator_gpu
    AcceleratorType.CPU -> CpuColor to R.string.accelerator_cpu
  }

  Card(
    shape = RoundedCornerShape(0.dp),
    colors = CardDefaults.cardColors(containerColor = color),
  ) {
    Text(
      text = stringResource(labelRes),
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = Color.White,
    )
  }
}

@Composable
private fun BenchmarkDetails(averages: BenchmarkAverages) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .padding(bottom = 12.dp),
  ) {
    HorizontalDivider(
      modifier = Modifier.padding(bottom = 12.dp),
      color = MaterialTheme.colorScheme.outlineVariant,
    )

    MetricRow(
      labelRes = R.string.benchmark_detection,
      value = formatLatency(averages.detectionTimeMs),
    )
    Spacer(modifier = Modifier.height(8.dp))
    MetricRow(
      labelRes = R.string.benchmark_recognition,
      value = formatLatency(averages.recognitionTimeMs),
    )
    Spacer(modifier = Modifier.height(8.dp))
    MetricRow(
      labelRes = R.string.benchmark_total,
      value = formatLatency(averages.totalTimeMs),
      isHighlighted = true,
    )
  }
}

@Composable
private fun MetricRow(
  @StringRes labelRes: Int,
  value: String,
  isHighlighted: Boolean = false,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(labelRes),
      style = MaterialTheme.typography.bodyMedium,
      color = if (isHighlighted) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
      color = if (isHighlighted) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.onSurface
      },
    )
  }
}

@Composable
private fun formatLatency(ms: Float): String {
  return if (ms > 0f) {
    stringResource(R.string.benchmark_ms, String.format(Locale.US, "%.1f", ms))
  } else {
    stringResource(R.string.benchmark_ms_placeholder)
  }
}

private fun formatFps(fps: Float): String {
  return if (fps > 0f) {
    String.format(Locale.US, "%.1f", fps)
  } else {
    ""
  }
}
