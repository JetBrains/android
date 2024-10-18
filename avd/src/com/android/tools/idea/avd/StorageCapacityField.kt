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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.TextField

@Composable
internal fun StorageCapacityField(
  state: StorageCapacityFieldState,
  errorMessage: String?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  outline: Outline = if (errorMessage == null) Outline.None else Outline.Error,
) {
  Row(modifier) {
    @OptIn(ExperimentalFoundationApi::class)
    ErrorTooltip(errorMessage) {
      TextField(
        state.value,
        Modifier.padding(end = Padding.SMALL).testTag("StorageCapacityFieldTextField"),
        enabled,
        inputTransformation = {
          if (!STORAGE_CAPACITY_VALUE_REGEX.matches(asCharSequence())) revertAllChanges()
        },
        outline = outline,
      )
    }

    Dropdown(state.unit, UNITS, state::unit::set, enabled = enabled)
  }
}

internal class StorageCapacityFieldState internal constructor(value: StorageCapacity) {
  internal val value = TextFieldState(value.value.toString())
  internal var unit by mutableStateOf(value.unit)
  internal val storageCapacity = snapshotFlow { toStorageCapacity() }

  internal fun toStorageCapacity() =
    try {
      StorageCapacity(value.text.toString().toLong(), unit)
    } catch (exception: NumberFormatException) {
      // TODO: http://b/373706926
      null
    }
}

private val STORAGE_CAPACITY_VALUE_REGEX = Regex("\\d*")
private val UNITS = enumValues<StorageCapacity.Unit>().asIterable().toImmutableList()
