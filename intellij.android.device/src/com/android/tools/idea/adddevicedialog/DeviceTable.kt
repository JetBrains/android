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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import icons.StudioIconsCompose
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.rememberSplitLayoutState
import org.jetbrains.jewel.ui.icon.PathIconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <DeviceT : DeviceProfile> DeviceTable(
  devices: List<DeviceT>,
  columns: List<TableColumn<DeviceT>>,
  filterContent: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  tableSelectionState: TableSelectionState<DeviceT> = remember { TableSelectionState() },
  filterState: DeviceFilterState<DeviceT> = remember { DeviceFilterState() },
  onRowSecondaryClick: (DeviceT, Offset) -> Unit = { _, _ -> },
) {
  var showDetails by remember { mutableStateOf(false) }
  val textState = rememberTextFieldState(filterState.textFilter.searchText)
  LaunchedEffect(Unit) {
    snapshotFlow { textState.text.toString() }.collect { filterState.textFilter.searchText = it }
  }

  Column(modifier) {
    Row {
      TextField(
        textState,
        leadingIcon = { Icon(StudioIconsCompose.Common.Search, contentDescription = "Search") },
        trailingIcon = {
          Icon(
            AllIconsKeys.General.CloseSmall,
            contentDescription = "Clear search",
            Modifier.clickable(onClick = { textState.setTextAndPlaceCursorAtEnd("") })
              .pointerHoverIcon(PointerIcon.Default),
          )
        },
        placeholder = {
          Text(
            filterState.textFilter.description,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(start = 4.dp),
          )
        },
        modifier = Modifier.weight(1f).padding(2.dp),
      )
      Tooltip(tooltip = { Text("Show device details") }) {
        IconButton(
          onClick = { showDetails = !showDetails },
          Modifier.align(Alignment.CenterVertically).padding(2.dp),
        ) {
          Icon(
            key = PathIconKey("actions/previewDetails.svg", AllIcons::class.java),
            contentDescription = "Details",
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
    if (devices.none(filterState.textFilter::apply)) {
      EmptyStatePanel(
        "No devices found for \"${filterState.textFilter.searchText}\".",
        Modifier.fillMaxSize(),
      )
    } else {
      HorizontalSplitLayout(
        first = { DeviceFiltersPanel { filterContent() } },
        second = {
          Row {
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
                onRowSecondaryClick = onRowSecondaryClick,
              )
              if (showDetails) {
                when (val selection = tableSelectionState.selection) {
                  null -> EmptyStatePanel("Select a device", Modifier.width(200.dp).fillMaxHeight())
                  else ->
                    DeviceDetails(selection, modifier = Modifier.width(200.dp).fillMaxHeight())
                }
              }
            }
          }
        },
        modifier = Modifier.fillMaxSize(),
        firstPaneMinWidth = 100.dp,
        secondPaneMinWidth = 300.dp,
        state = rememberSplitLayoutState(.3f),
      )
    }
  }
}

object DeviceTableColumns {
  val icon =
    TableColumn<DeviceProfile>("", TableColumnWidth.Fixed(16.dp)) { it.Icon(Modifier.size(16.dp)) }
  val oem = TableTextColumn<DeviceProfile>("OEM", attribute = { it.manufacturer })
  val name =
    TableTextColumn<DeviceProfile>(
      "Name",
      TableColumnWidth.Weighted(2f),
      attribute = { it.name },
      maxLines = 2,
    )
  val api =
    TableTextColumn<DeviceProfile>("API", attribute = { it.apiRange.lowerEndpoint().toString() })
  val width =
    TableTextColumn<DeviceProfile>("Width", attribute = { it.resolution.width.toString() })
  val height =
    TableTextColumn<DeviceProfile>("Height", attribute = { it.resolution.height.toString() })
  val density =
    TableTextColumn<DeviceProfile>("Density", attribute = { "${it.displayDensity} dpi" })
  val type =
    TableTextColumn<DeviceProfile>(
      "Type",
      attribute = { if (it.isVirtual) "Virtual" else "Physical" },
    )
}

@Composable
private fun EmptyStatePanel(text: String, modifier: Modifier = Modifier) {
  Box(modifier) { Text(text, Modifier.align(Alignment.Center)) }
}
