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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import me.fleey.ppocrv5.R
import me.fleey.ppocrv5.navigation.AboutRoute
import me.fleey.ppocrv5.navigation.CameraRoute
import me.fleey.ppocrv5.navigation.GalleryRoute

@Composable
fun AppBottomBar(
  navController: NavController,
  currentDestination: NavDestination?,
) {
  NavigationBar {
    NavigationBarItem(
      icon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
      label = { Text(stringResource(R.string.nav_camera)) },
      selected = currentDestination?.hasRoute<CameraRoute>() == true,
      onClick = {
        navController.navigate(CameraRoute) {
          popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
          }
          launchSingleTop = true
          restoreState = true
        }
      },
    )
    NavigationBarItem(
      icon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
      label = { Text(stringResource(R.string.nav_gallery)) },
      selected = currentDestination?.hasRoute<GalleryRoute>() == true,
      onClick = {
        navController.navigate(GalleryRoute) {
          popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
          }
          launchSingleTop = true
          restoreState = true
        }
      },
    )
    NavigationBarItem(
      icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
      label = { Text(stringResource(R.string.nav_about)) },
      selected = currentDestination?.hasRoute<AboutRoute>() == true,
      onClick = {
        navController.navigate(AboutRoute) {
          popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
          }
          launchSingleTop = true
          restoreState = true
        }
      },
    )
  }
}
