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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import icons.StudioIcons
import org.jetbrains.jewel.ui.component.Icon

@Composable
internal fun DeviceTable(
  devices: List<DeviceProfile>,
  modifier: Modifier = Modifier,
  tableSelectionState: TableSelectionState<DeviceProfile> = remember { TableSelectionState() },
) {
  val columns: List<TableColumn<DeviceProfile>> = remember {
    listOf(
      TableColumn("", 0.5f) {
        // TODO: Represent actual device type
        Icon("studio/icons/avd/device-mobile.svg", "", StudioIcons::class.java)
      },
      TableTextColumn("Brand") { it.manufacturer },
      TableTextColumn("Name", 2f) { it.name },
      TableTextColumn("API") {
        // TODO: Use latest API level supported or chosen API level
        "27"
      },
      TableTextColumn("Width") { it.resolution.width.toString() },
      TableTextColumn("Height") { it.resolution.height.toString() },
      TableTextColumn("Density") { "${it.displayDensity} dpi" },
      TableTextColumn("Type") { if (it.isVirtual) "Virtual" else "Physical" },
      TableTextColumn("Source") { if (it.isRemote) "Remote" else "Local" },
    )
  }
  Table(columns, devices, { it }, tableSelectionState = tableSelectionState, modifier = modifier)
}
