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

package me.fleey.ppocrv5.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import me.fleey.ppocrv5.navigation.AboutRoute
import me.fleey.ppocrv5.navigation.CameraRoute
import me.fleey.ppocrv5.navigation.GalleryRoute
import me.fleey.ppocrv5.navigation.ImageViewerRoute
import me.fleey.ppocrv5.ui.component.AppBottomBar
import me.fleey.ppocrv5.ui.screen.AboutScreen
import me.fleey.ppocrv5.ui.screen.CameraScreen
import me.fleey.ppocrv5.ui.screen.GalleryScreen
import me.fleey.ppocrv5.ui.screen.ImageViewerScreen

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppEntryPoint(modifier: Modifier = Modifier) {
  SharedTransitionLayout {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route?.contains("ImageViewerRoute") != true

    Scaffold(
      modifier = modifier,
      bottomBar = {
        if (showBottomBar) {
          AppBottomBar(
            navController = navController,
            currentDestination = currentDestination,
          )
        }
      },
    ) { innerPadding ->
      NavHost(
        navController = navController,
        startDestination = CameraRoute,
        modifier = Modifier.fillMaxSize(),
      ) {
        composable<CameraRoute> {
          CameraScreen(
            modifier = Modifier
              .fillMaxSize()
              .padding(bottom = innerPadding.calculateBottomPadding()),
          )
        }
        composable<GalleryRoute> {
          GalleryScreen(
            onImageClick = { uri, id ->
              navController.navigate(ImageViewerRoute(uri, id))
            },
            animatedVisibilityScope = this,
            modifier = Modifier.padding(innerPadding),
          )
        }
        composable<ImageViewerRoute> { backStackEntry ->
          val route: ImageViewerRoute = backStackEntry.toRoute()
          ImageViewerScreen(
            imageUri = route.imageUri,
            imageId = route.imageId,
            onBack = { navController.popBackStack() },
            animatedVisibilityScope = this,
          )
        }
        composable<AboutRoute> {
          AboutScreen(
            modifier = Modifier.padding(innerPadding),
          )
        }
      }
    }
  }
}
