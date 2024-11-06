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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.common.collect.Ordering
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

internal typealias DeviceAttribute<V> = RowAttribute<DeviceProfile, V>

@Composable
fun DeviceFiltersPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  VerticallyScrollableContainer(modifier.fillMaxSize()) {
    Column(Modifier.padding(6.dp).testTag("DeviceFilters")) { content() }
  }
}

/**
 * Holds the state of the filters for a [DeviceTable]. Can be extended to add or adjust filters for
 * subtypes of [DeviceProfile].
 */
@Stable
open class DeviceFilterState<in DeviceT : DeviceProfile> : RowFilter<DeviceT> {
  val formFactorFilter = FormFactor.initialSingleSelectionFilterState("Phone")
  open val textFilter = TextFilterState<DeviceT>()

  override fun apply(row: DeviceT): Boolean = formFactorFilter.apply(row) && textFilter.apply(row)
}

@Stable
open class TextFilterState<in DeviceT : DeviceProfile> : RowFilter<DeviceT> {
  var searchText: String by mutableStateOf("")
  open val description = "Search for a device by name"

  override fun apply(row: DeviceT): Boolean =
    searchText.isBlank() || row.name.contains(searchText.trim(), ignoreCase = true)
}

val Manufacturer = DeviceAttribute("OEM") { it.manufacturer }

internal val formFactorOrder: PersistentList<String> =
  with(FormFactors) { persistentListOf(PHONE, TABLET, WEAR, DESKTOP, TV, AUTO) }

private val formFactorComparator: Comparator<String> =
  compareBy(nullsLast(Ordering.explicit(formFactorOrder))) { formFactor: String ->
      formFactor.takeIf { it in formFactorOrder }
    }
    .then(naturalOrder())

val FormFactor = DeviceAttribute("Form Factor", comparator = formFactorComparator) { it.formFactor }

internal fun <V : Comparable<V>> DeviceAttribute(name: String, value: (DeviceProfile) -> V) =
  DeviceAttribute(name, Comparator.naturalOrder(), value)
