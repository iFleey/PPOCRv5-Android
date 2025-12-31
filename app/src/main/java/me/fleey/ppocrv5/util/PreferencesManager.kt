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
import androidx.core.content.edit
import me.fleey.ppocrv5.ocr.AcceleratorType

object PreferencesManager {
  private const val PREFS_NAME = "ppocrv5_prefs"
  private const val KEY_ACCELERATOR_TYPE = "accelerator_type"

  fun saveAcceleratorType(context: Context, type: AcceleratorType) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit {
        putInt(KEY_ACCELERATOR_TYPE, type.value)
      }
  }

  fun getAcceleratorType(context: Context): AcceleratorType {
    val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getInt(KEY_ACCELERATOR_TYPE, AcceleratorType.GPU.value)
    return AcceleratorType.fromValue(value)
  }
}
