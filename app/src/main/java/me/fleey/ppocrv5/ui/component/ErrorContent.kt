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

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.fleey.ppocrv5.R
import me.fleey.ppocrv5.ui.state.ErrorType

@Composable
fun ErrorContent(
  errorType: ErrorType,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  val (title, message, showSettings) = when (errorType) {
    is ErrorType.CameraPermissionDenied -> Triple(
      stringResource(R.string.error_camera_permission_title),
      stringResource(R.string.error_camera_permission_message),
      true,
    )

    is ErrorType.ModelLoadFailed -> Triple(
      stringResource(R.string.error_model_load_title),
      stringResource(R.string.error_model_load_message),
      false,
    )

    is ErrorType.AcceleratorInitFailed -> {
      val message = errorType.fallbackTo?.let {
        stringResource(R.string.error_accelerator_init_fallback, it.name)
      } ?: stringResource(R.string.error_accelerator_init_message)
      Triple(
        stringResource(R.string.error_accelerator_init_title),
        message,
        false,
      )
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = Icons.Default.Warning,
      contentDescription = null,
      modifier = Modifier.size(64.dp),
      tint = MaterialTheme.colorScheme.error,
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = title,
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(32.dp))

    if (showSettings) {
      Button(
        onClick = {
          val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
          }
          context.startActivity(intent)
        },
      ) {
        Text(stringResource(R.string.common_open_settings))
      }

      Spacer(modifier = Modifier.height(12.dp))

      FilledTonalButton(onClick = onRetry) {
        Text(stringResource(R.string.common_retry))
      }
    } else {
      FilledTonalButton(onClick = onRetry) {
        Text(stringResource(R.string.common_retry))
      }
    }
  }
}
