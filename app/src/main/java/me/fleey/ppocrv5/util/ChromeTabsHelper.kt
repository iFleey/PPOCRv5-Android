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

package me.fleey.ppocrv5.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

object ChromeTabsHelper {

  fun openUrl(
    context: Context,
    url: String,
    toolbarColor: Color,
    isDarkTheme: Boolean,
  ) {
    val colorScheme = if (isDarkTheme) {
      CustomTabsIntent.COLOR_SCHEME_DARK
    } else {
      CustomTabsIntent.COLOR_SCHEME_LIGHT
    }

    val colorParams = CustomTabColorSchemeParams.Builder()
      .setToolbarColor(toolbarColor.toArgb())
      .build()

    val customTabsIntent = CustomTabsIntent.Builder()
      .setColorScheme(colorScheme)
      .setDefaultColorSchemeParams(colorParams)
      .setShowTitle(true)
      .setShareState(CustomTabsIntent.SHARE_STATE_ON)
      .build()

    try {
      customTabsIntent.launchUrl(context, Uri.parse(url))
    } catch (e: Exception) {
      // Fallback to default browser
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
      context.startActivity(intent)
    }
  }
}
