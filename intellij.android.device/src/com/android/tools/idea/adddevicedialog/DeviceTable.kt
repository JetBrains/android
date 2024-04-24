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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import icons.StudioIcons
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
internal fun DeviceTable(
  devices: List<DeviceProfile>,
  modifier: Modifier = Modifier,
  tableSelectionState: TableSelectionState<DeviceProfile> = remember { TableSelectionState() },
  filterState: DeviceFilterState = remember { DeviceFilterState(devices) },
) {
  var showDetails by remember { mutableStateOf(false) }

  val columns: List<TableColumn<DeviceProfile>> = remember {
    listOf(
      TableColumn("", TableColumnWidth.Fixed(16.dp)) { it.Icon(Modifier.size(16.dp)) },
      TableTextColumn("OEM") { it.manufacturer },
      TableTextColumn("Name", TableColumnWidth.Weighted(2f), maxLines = 2) { it.name },
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
    Row {
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
        modifier = Modifier.weight(1f).padding(2.dp),
      )
      IconButton(
        onClick = { showDetails = !showDetails },
        Modifier.align(Alignment.CenterVertically).padding(2.dp),
      ) {
        Icon("actions/previewDetails.svg", "Details", AllIcons::class.java, Modifier.size(20.dp))
      }
    }
    if (devices.none(filterState.textFilter::apply)) {
      EmptyStatePanel(
        "No devices found for \"${filterState.textFilter.searchText}\".",
        Modifier.fillMaxSize(),
      )
    } else {
      HorizontalSplitLayout(
        first = { DeviceFilters(filterState, modifier = it) },
        second = {
          Row(modifier = it) {
            val filteredDevices = devices.filter(filterState::apply)
            if (filteredDevices.isEmpty()) {
              EmptyStatePanel(
                "No devices found matching the current filters.",
                Modifier.fillMaxSize(),
              )
            } else {
              Table(
                columns,
                filteredDevices,
                { it },
                modifier = Modifier.weight(1f),
                tableSelectionState = tableSelectionState,
              )
              if (showDetails) {
                when (val selection = tableSelectionState.selection) {
                  null -> EmptyStatePanel("Select a device", Modifier.width(200.dp).fillMaxHeight())
                  else ->
                    DeviceDetails(
                      selection,
                      filterState.apiLevelFilter.apiLevel.apiLevel,
                      modifier = Modifier.width(200.dp).fillMaxHeight(),
                    )
                }
              }
            }
          }
        },
        modifier = Modifier.fillMaxSize(),
        minRatio = 0.1f,
        maxRatio = 0.5f,
      )
    }
  }
}

@Composable
private fun EmptyStatePanel(text: String, modifier: Modifier = Modifier) {
  Box(modifier) { Text(text, Modifier.align(Alignment.Center)) }
}
