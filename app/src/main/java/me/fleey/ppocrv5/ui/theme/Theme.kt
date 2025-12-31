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

package me.fleey.ppocrv5.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
  primary = Color(0xFF1976D2),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFBBDEFB),
  onPrimaryContainer = Color(0xFF0D47A1),
  secondary = Color(0xFF4CAF50),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFC8E6C9),
  onSecondaryContainer = Color(0xFF1B5E20),
  tertiary = Color(0xFFFF9800),
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFFFE0B2),
  onTertiaryContainer = Color(0xFFE65100),
  error = Color(0xFFD32F2F),
  onError = Color.White,
  errorContainer = Color(0xFFFFCDD2),
  onErrorContainer = Color(0xFFB71C1C),
  background = Color(0xFFFAFAFA),
  onBackground = Color(0xFF212121),
  surface = Color.White,
  onSurface = Color(0xFF212121),
  surfaceVariant = Color(0xFFF5F5F5),
  onSurfaceVariant = Color(0xFF757575),
  outline = Color(0xFFBDBDBD),
  outlineVariant = Color(0xFFE0E0E0),
)

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFF90CAF9),
  onPrimary = Color(0xFF0D47A1),
  primaryContainer = Color(0xFF1565C0),
  onPrimaryContainer = Color(0xFFBBDEFB),
  secondary = Color(0xFFA5D6A7),
  onSecondary = Color(0xFF1B5E20),
  secondaryContainer = Color(0xFF388E3C),
  onSecondaryContainer = Color(0xFFC8E6C9),
  tertiary = Color(0xFFFFCC80),
  onTertiary = Color(0xFFE65100),
  tertiaryContainer = Color(0xFFF57C00),
  onTertiaryContainer = Color(0xFFFFE0B2),
  error = Color(0xFFEF9A9A),
  onError = Color(0xFFB71C1C),
  errorContainer = Color(0xFFC62828),
  onErrorContainer = Color(0xFFFFCDD2),
  background = Color(0xFF121212),
  onBackground = Color(0xFFE0E0E0),
  surface = Color(0xFF1E1E1E),
  onSurface = Color(0xFFE0E0E0),
  surfaceVariant = Color(0xFF2C2C2C),
  onSurfaceVariant = Color(0xFFBDBDBD),
  outline = Color(0xFF757575),
  outlineVariant = Color(0xFF424242),
)

@Composable
fun PPOCRv5AndroidTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content,
  )
}
