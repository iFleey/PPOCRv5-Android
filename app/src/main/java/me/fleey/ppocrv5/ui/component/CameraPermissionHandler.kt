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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import me.fleey.ppocrv5.R

private enum class PermissionState {
  UNKNOWN,
  GRANTED,
  SHOW_RATIONALE,
  DENIED,
}

@Composable
fun CameraPermissionHandler(
  onPermissionGranted: @Composable () -> Unit,
  onPermissionDenied: () -> Unit,
) {
  val context = LocalContext.current
  var permissionState by remember { mutableStateOf(PermissionState.UNKNOWN) }
  var hasRequestedOnce by remember { mutableStateOf(false) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { isGranted ->
    permissionState = if (isGranted) {
      PermissionState.GRANTED
    } else {
      hasRequestedOnce = true
      PermissionState.DENIED
    }
  }

  LaunchedEffect(Unit) {
    val hasPermission = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED

    permissionState = if (hasPermission) {
      PermissionState.GRANTED
    } else {
      PermissionState.UNKNOWN
    }
  }

  when (permissionState) {
    PermissionState.GRANTED -> {
      onPermissionGranted()
    }

    PermissionState.SHOW_RATIONALE -> {
      PermissionRationale(
        onRequestPermission = {
          permissionLauncher.launch(Manifest.permission.CAMERA)
        },
      )
    }

    PermissionState.DENIED -> {
      PermissionDeniedContent(
        isPermanentlyDenied = hasRequestedOnce,
        onRequestPermission = {
          permissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onPermissionDenied = onPermissionDenied,
      )
    }

    PermissionState.UNKNOWN -> {
      LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
      }

      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = stringResource(R.string.permission_camera_request_message),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun PermissionRationale(
  onRequestPermission: () -> Unit,
  modifier: Modifier = Modifier,
) {
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
      tint = MaterialTheme.colorScheme.primary,
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = stringResource(R.string.permission_camera_title),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = stringResource(R.string.permission_camera_rationale),
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(32.dp))

    Button(onClick = onRequestPermission) {
      Text(stringResource(R.string.permission_grant))
    }
  }
}

@Composable
private fun PermissionDeniedContent(
  isPermanentlyDenied: Boolean,
  onRequestPermission: () -> Unit,
  onPermissionDenied: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

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
      text = if (isPermanentlyDenied) {
        stringResource(R.string.permission_camera_denied_title)
      } else {
        stringResource(R.string.permission_camera_title)
      },
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = if (isPermanentlyDenied) {
        stringResource(R.string.permission_camera_denied_message)
      } else {
        stringResource(R.string.permission_camera_request_message)
      },
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(32.dp))

    if (isPermanentlyDenied) {
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

      OutlinedButton(onClick = onPermissionDenied) {
        Text(stringResource(R.string.common_cancel))
      }
    } else {
      Button(onClick = onRequestPermission) {
        Text(stringResource(R.string.permission_request))
      }
    }
  }
}
