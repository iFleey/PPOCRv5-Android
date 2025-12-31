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

package me.fleey.ppocrv5.ui.screen

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.fleey.ppocrv5.BuildConfig
import me.fleey.ppocrv5.R
import me.fleey.ppocrv5.util.ChromeTabsHelper

private const val GITHUB_URL = "https://github.com/iFleey/PPOCRv5-Android"
private const val LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private const val AUTHOR_NAME = "Fleey"
private const val AUTHOR_WEBSITE = "https://fleey.me"
private const val AUTHOR_GITHUB = "https://github.com/iFleey"

private data class OpenSourceLibrary(
  val name: String,
  val license: String,
  val url: String,
)

private val OpenSourceLibraries = listOf(
  OpenSourceLibrary(
    name = "PaddleOCR",
    license = "Apache 2.0",
    url = "https://github.com/PaddlePaddle/PaddleOCR",
  ),
  OpenSourceLibrary(
    name = "Jetpack Compose",
    license = "Apache 2.0",
    url = "https://developer.android.com/jetpack/compose",
  ),
  OpenSourceLibrary(
    name = "CameraX",
    license = "Apache 2.0",
    url = "https://developer.android.com/training/camerax",
  ),
  OpenSourceLibrary(
    name = "LiteRT (TensorFlow Lite)",
    license = "Apache 2.0",
    url = "https://ai.google.dev/edge/litert",
  ),
  OpenSourceLibrary(
    name = "Coil",
    license = "Apache 2.0",
    url = "https://coil-kt.github.io/coil/",
  ),
  OpenSourceLibrary(
    name = "Navigation Compose",
    license = "Apache 2.0",
    url = "https://developer.android.com/jetpack/compose/navigation",
  ),
  OpenSourceLibrary(
    name = "Kotlinx Serialization",
    license = "Apache 2.0",
    url = "https://github.com/Kotlin/kotlinx.serialization",
  ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val isDarkTheme = isSystemInDarkTheme()
  val toolbarColor = MaterialTheme.colorScheme.surface
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  Scaffold(
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      LargeTopAppBar(
        title = { Text(stringResource(R.string.about_title)) },
        scrollBehavior = scrollBehavior,
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineSmall,
      )

      Text(
        text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(32.dp))

      AboutSection(
        title = stringResource(R.string.about_project),
        modifier = Modifier.fillMaxWidth(),
      ) {
        AboutItem(
          icon = { Icon(Icons.Outlined.Code, contentDescription = null) },
          title = stringResource(R.string.about_github),
          subtitle = GITHUB_URL.removePrefix("https://"),
          onClick = {
            ChromeTabsHelper.openUrl(context, GITHUB_URL, toolbarColor, isDarkTheme)
          },
        )

        AboutItem(
          icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
          title = stringResource(R.string.about_author),
          subtitle = AUTHOR_NAME,
          onClick = null,
        )

        AboutItem(
          icon = { Icon(Icons.Outlined.Language, contentDescription = null) },
          title = stringResource(R.string.about_author_website),
          subtitle = AUTHOR_WEBSITE.removePrefix("https://"),
          onClick = {
            ChromeTabsHelper.openUrl(context, AUTHOR_WEBSITE, toolbarColor, isDarkTheme)
          },
        )

        AboutItem(
          icon = { Icon(Icons.Outlined.Code, contentDescription = null) },
          title = stringResource(R.string.about_author_github),
          subtitle = AUTHOR_GITHUB.removePrefix("https://"),
          onClick = {
            ChromeTabsHelper.openUrl(context, AUTHOR_GITHUB, toolbarColor, isDarkTheme)
          },
        )

        AboutItem(
          icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
          title = stringResource(R.string.about_license),
          subtitle = stringResource(R.string.about_license_name),
          onClick = {
            ChromeTabsHelper.openUrl(context, LICENSE_URL, toolbarColor, isDarkTheme)
          },
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      AboutSection(
        title = stringResource(R.string.about_acknowledgements),
        modifier = Modifier.fillMaxWidth(),
      ) {
        OpenSourceLibraries.forEach { library ->
          AboutItem(
            icon = { Icon(Icons.Outlined.Code, contentDescription = null) },
            title = library.name,
            subtitle = library.license,
            onClick = {
              ChromeTabsHelper.openUrl(context, library.url, toolbarColor, isDarkTheme)
            },
          )
        }
      }

      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}

@Composable
private fun AboutSection(
  title: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Column(modifier = modifier) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    Surface(
      tonalElevation = 1.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
      ) {
        content()
      }
    }
  }
}

@Composable
private fun AboutItem(
  icon: @Composable () -> Unit,
  title: String,
  subtitle: String,
  onClick: (() -> Unit)?,
) {
  Surface(
    onClick = onClick ?: {},
    enabled = onClick != null,
  ) {
    ListItem(
      leadingContent = icon,
      headlineContent = { Text(title) },
      supportingContent = { Text(subtitle) },
    )
  }
}
