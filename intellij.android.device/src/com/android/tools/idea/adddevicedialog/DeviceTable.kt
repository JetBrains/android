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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import icons.StudioIcons
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
internal fun DeviceTable(
  devices: List<DeviceProfile>,
  modifier: Modifier = Modifier,
  tableSelectionState: TableSelectionState<DeviceProfile> = remember { TableSelectionState() },
) {
  val filterState = remember { DeviceFilterState(devices) }

  val columns: List<TableColumn<DeviceProfile>> = remember {
    listOf(
      TableColumn("", 0.5f) {
        // TODO: Represent actual device type
        Icon("studio/icons/avd/device-mobile.svg", "", StudioIcons::class.java)
      },
      TableTextColumn("OEM") { it.manufacturer },
      TableTextColumn("Name", 2f) { it.name },
      TableTextColumn("API") {
        // This case is a bit strange, because we adjust the display based on the API filter.
        // TODO: We will need a way to pass the API level on to the next stage.
        (filterState.apiLevelFilter.apiLevel.apiLevel ?: it.apiRange.upperEndpoint()).toString()
      },
      TableTextColumn("Width") { it.resolution.width.toString() },
      TableTextColumn("Height") { it.resolution.height.toString() },
      TableTextColumn("Density") { "${it.displayDensity} dpi" },
      TableTextColumn("Type") { if (it.isVirtual) "Virtual" else "Physical" },
      TableTextColumn("Source") { if (it.isRemote) "Remote" else "Local" },
    )
  }
  Column(modifier) {
    TextField(
      filterState.textFilter.searchText,
      onValueChange = { filterState.textFilter.searchText = it },
      leadingIcon = { Icon("studio/icons/common/search.svg", "Search", StudioIcons::class.java) },
      placeholder = {
        Text(
          "Search for a device by name, model, or OEM",
          fontWeight = FontWeight.Light,
          modifier = Modifier.padding(start = 4.dp),
        )
      },
      modifier = Modifier.fillMaxWidth().padding(4.dp),
    )
    HorizontalSplitLayout(
      first = { DeviceFilters(filterState, modifier = it) },
      second = {
        Table(
          columns,
          devices.filter(filterState::apply),
          { it },
          tableSelectionState = tableSelectionState,
          modifier = it,
        )
      },
      modifier = Modifier.fillMaxSize(),
      minRatio = 0.1f,
      maxRatio = 0.5f,
    )
  }
}
