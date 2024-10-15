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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.TextField

@Composable
internal fun StorageCapacityField(
  value: StorageCapacity?,
  errorMessage: String?,
  onValueChange: (StorageCapacity?) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Row(modifier) {
    var textFieldValue by remember { mutableStateOf(value?.value?.toString() ?: "") }
    var dropdownValue by remember { mutableStateOf(value?.unit ?: StorageCapacity.Unit.MB) }

    @OptIn(ExperimentalFoundationApi::class)
    ErrorTooltip(errorMessage) {
      TextField(
        textFieldValue,
        {
          textFieldValue = it
          onValueChange(storageCapacity(it, dropdownValue))
        },
        Modifier.padding(end = Padding.SMALL).testTag("StorageCapacityFieldTextField"),
        enabled,
        // TODO: http://b/373463053
        outline = if (errorMessage == null) Outline.None else Outline.Error,
      )
    }

    Dropdown(
      dropdownValue,
      UNITS,
      onSelectedItemChange = {
        dropdownValue = it
        onValueChange(storageCapacity(textFieldValue, dropdownValue))
      },
      enabled = enabled,
    )
  }
}

private fun storageCapacity(textFieldValue: String, dropdownValue: StorageCapacity.Unit) =
  if (STORAGE_CAPACITY_VALUE_REGEX.matches(textFieldValue)) {
    try {
      StorageCapacity(textFieldValue.toLong(), dropdownValue)
    } catch (exception: NumberFormatException) {
      // TODO: http://b/373706926
      null
    }
  } else {
    null
  }

private val STORAGE_CAPACITY_VALUE_REGEX = Regex("\\d+")
private val UNITS = enumValues<StorageCapacity.Unit>().asIterable().toImmutableList()
