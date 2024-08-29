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
package com.android.tools.idea.avd

import com.android.tools.idea.adddevicedialog.ComposeWizard
import com.android.tools.idea.adddevicedialog.DeviceFilterState
import com.android.tools.idea.adddevicedialog.DeviceGridPage
import com.android.tools.idea.adddevicedialog.DeviceSource
import com.android.tools.idea.adddevicedialog.DeviceTableColumns
import com.android.tools.idea.adddevicedialog.FormFactor
import com.android.tools.idea.adddevicedialog.SingleSelectionDropdown
import com.android.tools.idea.adddevicedialog.uniqueValuesOf
import com.intellij.openapi.project.Project
import kotlinx.collections.immutable.persistentListOf

class AddDeviceWizard(val source: DeviceSource, val project: Project?) {
  fun createDialog(): ComposeWizard {
    return ComposeWizard(project, "Add Device") {
      val filterState = getOrCreateState { DeviceFilterState() }
      DeviceGridPage(
        source,
        avdColumns,
        filterContent = { profiles ->
          SingleSelectionDropdown(FormFactor.uniqueValuesOf(profiles), filterState.formFactorFilter)
        },
        filterState = filterState,
      )
    }
  }
}

private val avdColumns =
  with(DeviceTableColumns) { persistentListOf(icon, name, width, height, density) }
