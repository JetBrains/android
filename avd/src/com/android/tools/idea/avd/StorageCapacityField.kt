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
import kotlinx.collections.immutable.ImmutableCollection
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

    Dropdown(state.selectedUnit, state.units, state::selectedUnit::set, enabled = enabled)
  }
}

internal class StorageCapacityFieldState
internal constructor(
  value: StorageCapacity,
  minValue: StorageCapacity = StorageCapacity.MIN,
  internal val units: ImmutableCollection<StorageCapacity.Unit> =
    enumValues<StorageCapacity.Unit>().asIterable().toImmutableList(),
) {
  internal val value = TextFieldState(value.value.toString())
  internal var minValue by mutableStateOf(minValue)
  internal var selectedUnit by mutableStateOf(value.unit)
  internal val storageCapacity = snapshotFlow { result().storageCapacity }

  internal fun valid() = result() as Valid

  internal fun result() =
    if (value.text.isEmpty()) {
      Empty
    } else {
      try {
        val value = StorageCapacity(value.text.toString().toLong(), selectedUnit)
        if (value < minValue) LessThanMin else Valid(value)
      } catch (exception: NumberFormatException) {
        // value.text.toString().toLong() overflowed
        Overflow
      } catch (exception: ArithmeticException) {
        // StorageCapacity(value.text.toString().toLong(), unit) can't be expressed in bytes
        Overflow
      }
    }

  internal class Valid internal constructor(override val storageCapacity: StorageCapacity) :
    Result()

  internal object Empty : Result()

  internal object LessThanMin : Result()

  internal object Overflow : Result()

  internal sealed class Result {
    internal open val storageCapacity: StorageCapacity? = null
  }
}

private val STORAGE_CAPACITY_VALUE_REGEX = Regex("\\d*")
