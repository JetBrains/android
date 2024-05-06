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

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal typealias DeviceAttribute<V> = RowAttribute<DeviceProfile, V>

@Composable
internal fun DeviceFilters(filterState: DeviceFilterState, modifier: Modifier = Modifier) {
  val scrollState = rememberScrollState()
  Box(modifier.fillMaxSize()) {
    Column(Modifier.padding(6.dp).testTag("DeviceFilters").verticalScroll(scrollState)) {
      ApiFilter(filterState.apiLevelFilter)
      for (attribute in DeviceSetAttributes) {
        SetFilter(filterState[attribute])
      }
    }
    VerticalScrollbar(
      rememberScrollbarAdapter(scrollState),
      modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
  }
}

@Stable
internal class DeviceFilterState(profiles: List<DeviceProfile>) : RowFilter<DeviceProfile> {
  private val setFilters: ImmutableList<SetFilterState<DeviceProfile, *>> =
    DeviceSetAttributes.map { it.initialSetFilterState(profiles) }.toImmutableList()

  val apiLevelFilter = ApiLevelSelectionState()

  val textFilter = TextFilterState()

  operator fun <V> get(attribute: DeviceAttribute<V>): SetFilterState<DeviceProfile, V> =
    setFilters.find { it.attribute == attribute } as SetFilterState<DeviceProfile, V>

  override fun apply(row: DeviceProfile): Boolean {
    return setFilters.all { it.apply(row) } && apiLevelFilter.apply(row) && textFilter.apply(row)
  }
}

@Stable
class TextFilterState : RowFilter<DeviceProfile> {
  var searchText: String by mutableStateOf("")

  override fun apply(row: DeviceProfile): Boolean =
    searchText.isBlank() ||
      row.manufacturer.contains(searchText.trim(), ignoreCase = true) ||
      row.name.contains(searchText.trim(), ignoreCase = true)
}

private val Manufacturer = DeviceAttribute("OEM") { it.manufacturer }
private val IsRemote = DeviceAttribute("Type") { if (it.isRemote) "Remote" else "Local" }
private val FormFactor = DeviceAttribute("Form Factor") { it.formFactor }
// The DeviceAttributes that we use set-based filters on. Note that this determines the display
// order.
internal val DeviceSetAttributes = listOf<DeviceAttribute<*>>(IsRemote, FormFactor, Manufacturer)

internal fun <V : Comparable<V>> DeviceAttribute(name: String, value: (DeviceProfile) -> V) =
  DeviceAttribute(name, Comparator.naturalOrder(), value)
