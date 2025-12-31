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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.fleey.ppocrv5.R
import me.fleey.ppocrv5.ocr.AcceleratorType
import me.fleey.ppocrv5.ocr.AspectRatio
import me.fleey.ppocrv5.ocr.ResolutionPreset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CameraSettingsContent(
  currentAccelerator: AcceleratorType,
  currentResolution: ResolutionPreset,
  onAcceleratorChanged: (AcceleratorType) -> Unit,
  onResolutionChanged: (ResolutionPreset) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedAspectRatio by remember(currentResolution) {
    mutableStateOf(AspectRatio.fromSize(currentResolution.size))
  }

  val availableResolutions = remember(selectedAspectRatio) {
    ResolutionPreset.getByAspectRatio(selectedAspectRatio)
  }

  Column(
    modifier = modifier
      .padding(24.dp)
      .navigationBarsPadding(),
  ) {
    Text(
      text = stringResource(R.string.settings_title),
      style = MaterialTheme.typography.headlineSmall,
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = stringResource(R.string.settings_accelerator),
      style = MaterialTheme.typography.labelLarge,
    )
    Spacer(modifier = Modifier.height(8.dp))

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
      AcceleratorType.entries.forEachIndexed { index, type ->
        SegmentedButton(
          selected = type == currentAccelerator,
          onClick = { onAcceleratorChanged(type) },
          shape = SegmentedButtonDefaults.itemShape(
            index = index,
            count = AcceleratorType.entries.size,
          ),
        ) {
          Text(getAcceleratorDisplayName(type))
        }
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = stringResource(R.string.settings_aspect_ratio),
      style = MaterialTheme.typography.labelLarge,
    )
    Spacer(modifier = Modifier.height(8.dp))

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      AspectRatio.entries.forEach { ratio ->
        val isSelected = selectedAspectRatio == ratio
        FilterChip(
          selected = isSelected,
          onClick = {
            selectedAspectRatio = ratio
            ResolutionPreset.getByAspectRatio(ratio)
              .firstOrNull()
              ?.let { onResolutionChanged(it) }
          },
          label = { Text(stringResource(ratio.labelRes)) },
          leadingIcon = if (isSelected) {
            {
              Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            }
          } else {
            null
          },
        )
      }
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = stringResource(R.string.settings_resolution),
      style = MaterialTheme.typography.labelLarge,
    )
    Spacer(modifier = Modifier.height(8.dp))

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      availableResolutions.forEach { preset ->
        val isSelected = preset == currentResolution
        FilterChip(
          selected = isSelected,
          onClick = { onResolutionChanged(preset) },
          label = { Text(stringResource(preset.labelRes)) },
          leadingIcon = if (isSelected) {
            {
              Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            }
          } else {
            null
          },
        )
      }
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}

@Composable
private fun getAcceleratorDisplayName(type: AcceleratorType): String {
  return when (type) {
    AcceleratorType.NPU -> stringResource(R.string.accelerator_npu)
    AcceleratorType.GPU -> stringResource(R.string.accelerator_gpu)
    AcceleratorType.CPU -> stringResource(R.string.accelerator_cpu)
  }
}
