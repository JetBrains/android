/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.avd

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.jewel.ui.component.TextField

@Composable
internal fun StorageCapacityField(
  value: StorageCapacity,
  onValueChange: (StorageCapacity) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Row(modifier) {
    TextField(
      value.value.toString(),
      {
        if (STORAGE_CAPACITY_VALUE_REGEX.matches(it)) {
          try {
            onValueChange(StorageCapacity(it.toLong(), value.unit))
          } catch (_: NumberFormatException) {}
        }
      },
      Modifier.padding(end = Padding.SMALL).testTag("StorageCapacityFieldTextField"),
      enabled,
    )

    Dropdown(
      value.unit,
      UNITS,
      onSelectedItemChange = { onValueChange(StorageCapacity(value.value, it)) },
      enabled = enabled,
    )
  }
}

private val STORAGE_CAPACITY_VALUE_REGEX = Regex("\\d+")
private val UNITS = enumValues<StorageCapacity.Unit>().asIterable().toImmutableList()
