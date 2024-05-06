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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.sdklib.AndroidVersion
import com.android.sdklib.getApiNameAndDetails
import kotlinx.collections.immutable.ImmutableCollection
import org.jetbrains.jewel.ui.component.Dropdown
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
internal fun DeviceAndApiPanel(
  device: VirtualDevice,
  images: ImmutableCollection<SystemImage>,
  onDeviceChange: (VirtualDevice) -> Unit
) {
  Text("Name")
  TextField(device.name, { onDeviceChange(device.copy(name = it)) })

  Text("API level")
  ApiLevelDropdown(images)
}

@Composable
private fun ApiLevelDropdown(images: ImmutableCollection<SystemImage>) {
  if (images.isEmpty()) {
    Dropdown(menuContent = {}, content = {})
    return
  }

  val levels =
    images //
      .map(SystemImage::apiLevel)
      .distinct()
      .sortedDescending()

  var selectedLevel by remember { mutableStateOf(levels.first()) }

  Dropdown(
    menuContent = {
      levels.forEach {
        selectableItem(selectedLevel == it, { selectedLevel = it }, content = { ApiLevelText(it) })
      }
    },
    content = { ApiLevelText(selectedLevel) }
  )
}

@Composable
private fun ApiLevelText(level: AndroidVersion) {
  val nameAndDetails = level.getApiNameAndDetails(includeReleaseName = true, includeCodeName = true)

  val string = buildString {
    append(nameAndDetails.name)

    nameAndDetails.details?.let {
      append(' ')
      append(it)
    }
  }

  Text(string)
}
