/*
 * Copyright (C) 2024 The Android Open Source Project
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

class TestDeviceFilterState : DeviceFilterState() {
  val oemFilter = SetFilterState(Manufacturer)

  override fun apply(row: DeviceProfile): Boolean = oemFilter.apply(row) && super.apply(row)
}

@Composable
fun TestDeviceFilters(profiles: List<DeviceProfile>, filterState: TestDeviceFilterState) {
  SingleSelectionDropdown(FormFactor.uniqueValuesOf(profiles), filterState.formFactorFilter)
  SetFilter(Manufacturer.uniqueValuesOf(profiles), filterState.oemFilter)
}

@Composable
fun TestDeviceTable(profiles: List<DeviceProfile>) {
  val filterState = TestDeviceFilterState()
  DeviceTable(
    profiles,
    filterContent = { TestDeviceFilters(profiles, filterState) },
    filterState = filterState,
  )
}
