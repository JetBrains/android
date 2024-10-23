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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import icons.StudioIconsCompose
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.rememberSplitLayoutState
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <DeviceT : DeviceProfile> DeviceTable(
  devices: List<DeviceT>,
  columns: List<TableColumn<DeviceT>>,
  filterContent: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  showDetailsState: DeviceTableShowDetailsState = remember { DeviceTableShowDetailsState() },
  lazyListState: LazyListState = rememberLazyListState(),
  tableSortState: TableSortState<DeviceT> = remember { TableSortState() },
  tableSelectionState: TableSelectionState<DeviceT> = remember { TableSelectionState() },
  filterState: DeviceFilterState<DeviceT> = remember { DeviceFilterState() },
  onRowSecondaryClick: (DeviceT, Offset) -> Unit = { _, _ -> },
) {
  val textState = rememberTextFieldState(filterState.textFilter.searchText)
  LaunchedEffect(Unit) {
    snapshotFlow { textState.text.toString() }.collect { filterState.textFilter.searchText = it }
  }
  val searchFieldFocusRequester = remember { FocusRequester() }

  HorizontalSplitLayout(
    first = { DeviceFiltersPanel { filterContent() } },
    second = {
      Column {
        Row(Modifier.padding(horizontal = 4.dp)) {
          TextField(
            textState,
            leadingIcon = {
              Icon(
                StudioIconsCompose.Common.Search,
                contentDescription = "Search",
                Modifier.padding(end = 4.dp),
              )
            },
            trailingIcon =
              (@Composable {
                  Icon(
                    AllIconsKeys.General.CloseSmall,
                    contentDescription = "Clear search",
                    Modifier.clickable(onClick = { textState.setTextAndPlaceCursorAtEnd("") })
                      .pointerHoverIcon(PointerIcon.Default),
                  )
                })
                .takeIf { textState.text.isNotEmpty() },
            placeholder = {
              Text(filterState.textFilter.description, fontWeight = FontWeight.Light)
            },
            modifier = Modifier.weight(1f).padding(2.dp).focusRequester(searchFieldFocusRequester),
          )
          Tooltip(tooltip = { Text("Show device details") }) {
            IconButton(
              onClick = { showDetailsState.visible = !showDetailsState.visible },
              Modifier.align(Alignment.CenterVertically).padding(2.dp),
            ) {
              Icon(
                AllIconsKeys.Actions.PreviewDetails,
                contentDescription = "Details",
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
        Row {
          val filteredDevices = devices.filter(filterState::apply)
          if (filteredDevices.isEmpty()) {
            if (devices.none(filterState.textFilter::apply)) {
              EmptyStatePanel(
                "No devices found for \"${filterState.textFilter.searchText}\".",
                Modifier.fillMaxSize(),
              )
            } else {
              EmptyStatePanel(
                "No devices found matching the current filters.",
                Modifier.fillMaxSize(),
              )
            }
          } else {
            Table(
              columns,
              filteredDevices,
              { it },
              modifier = Modifier.weight(1f),
              lazyListState = lazyListState,
              tableSortState = tableSortState,
              tableSelectionState = tableSelectionState,
              onRowSecondaryClick = onRowSecondaryClick,
            )
            if (showDetailsState.visible) {
              Divider(orientation = Orientation.Vertical)
              when (
                val selection = tableSelectionState.selection?.takeIf { filterState.apply(it) }
              ) {
                null -> EmptyStatePanel("Select a device", Modifier.width(200.dp).fillMaxHeight())
                else ->
                  DeviceDetails(
                    selection,
                    modifier =
                      Modifier.width(200.dp)
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                        .fillMaxHeight(),
                  )
              }
            }
          }
        }
      }
    },
    modifier = modifier.fillMaxSize(),
    firstPaneMinWidth = 100.dp,
    secondPaneMinWidth = 300.dp,
    state = rememberSplitLayoutState(2 / 9f), // default dialog width is 900dp; approximately 200dp
  )

  LaunchedEffect(Unit) { searchFieldFocusRequester.requestFocus() }
}

class DeviceTableShowDetailsState {
  var visible by mutableStateOf(false)
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
    DefaultSortableTableColumn<DeviceProfile, Int>(
      "API",
      attribute = { it.apiRange.lowerEndpoint() },
    )

  private val minApiComparator: Comparator<DeviceProfile> = compareBy {
    it.apiRange.let { if (it.hasLowerBound()) it.lowerEndpoint() else 1 }
  }
  private val maxApiComparator: Comparator<DeviceProfile> = compareBy {
    it.apiRange.let { if (it.hasUpperBound()) it.upperEndpoint() else Int.MAX_VALUE }
  }
  val apiRangeAscendingOrder = minApiComparator.then(maxApiComparator)
  val apiRangeDescendingOrder = maxApiComparator.then(minApiComparator).reversed()

  val apiRange =
    TableColumn(
      "API",
      width = TableColumnWidth.Weighted(1f),
      comparator = apiRangeAscendingOrder,
      reverseComparator = apiRangeDescendingOrder,
      rowContent = { Text(it.apiRange.firstAndLastApiLevel()) },
    )

  val width =
    DefaultSortableTableColumn<DeviceProfile, Int>("Width", attribute = { it.resolution.width })
  val height =
    DefaultSortableTableColumn<DeviceProfile, Int>("Height", attribute = { it.resolution.height })
  val density =
    TableTextColumn<DeviceProfile>(
      "Density",
      attribute = { "${it.displayDensity} dpi" },
      comparator = compareBy { it.displayDensity },
    )
  val type =
    TableTextColumn<DeviceProfile>(
      "Type",
      attribute = { if (it.isVirtual) "Virtual" else "Physical" },
    )
}

/**
 * A panel to be used when there is no data to show. Displays text in the center in a lighter color.
 */
@Composable
fun EmptyStatePanel(text: String, modifier: Modifier = Modifier) {
  Box(modifier) {
    Text(text, Modifier.align(Alignment.Center), color = JewelTheme.globalColors.text.info)
  }
}
