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
package com.android.tools.idea.adddevicedialog

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import com.android.tools.idea.avdmanager.skincombobox.Skin
import kotlinx.collections.immutable.ImmutableCollection
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun AdditionalSettingsPanel(
  selectedSkin: Skin,
  skins: ImmutableCollection<Skin>,
  onSelectedSkinChange: (Skin) -> Unit
) {
  Row {
    Text("Device skin")
    DeviceSkinDropdown(selectedSkin, skins, onSelectedSkinChange)
  }
}

@Composable
private fun DeviceSkinDropdown(
  selectedSkin: Skin,
  skins: ImmutableCollection<Skin>,
  onSelectedSkinChange: (Skin) -> Unit
) {
  Dropdown(
    menuContent = {
      skins.forEach {
        selectableItem(
          selectedSkin == it,
          { onSelectedSkinChange(it) },
          content = { Text(it.toString()) }
        )
      }
    },
    content = { Text(selectedSkin.toString()) }
  )
}
