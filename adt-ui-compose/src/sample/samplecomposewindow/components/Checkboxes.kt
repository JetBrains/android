/*
 * Copyright (C) 2023 The Android Open Source Project
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
package sample.samplecomposewindow.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.TriStateCheckboxRow

/**
 * This sample composable showcasing the different checkbox components was adapted from the public Jewel repository standalone sample.
 * See: https://github.com/JetBrains/jewel
 */
@Composable
fun Checkboxes() {
  GroupHeader("Checkboxes")
  Row(
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    var checked by remember { mutableStateOf(ToggleableState.On) }
    TriStateCheckboxRow("Checkbox", checked, {
      checked = when (checked) {
        ToggleableState.On -> ToggleableState.Off
        ToggleableState.Off -> ToggleableState.Indeterminate
        ToggleableState.Indeterminate -> ToggleableState.On
      }
    })
    TriStateCheckboxRow("Error", checked, {
      checked = when (checked) {
        ToggleableState.On -> ToggleableState.Off
        ToggleableState.Off -> ToggleableState.Indeterminate
        ToggleableState.Indeterminate -> ToggleableState.On
      }
    }, outline = Outline.Error)
    TriStateCheckboxRow("Warning", checked, {
      checked = when (checked) {
        ToggleableState.On -> ToggleableState.Off
        ToggleableState.Off -> ToggleableState.Indeterminate
        ToggleableState.Indeterminate -> ToggleableState.On
      }
    }, outline = Outline.Warning)
    TriStateCheckboxRow("Disabled", checked, {}, enabled = false)
  }
}