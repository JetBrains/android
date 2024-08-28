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
import androidx.compose.runtime.Stable
import com.intellij.openapi.project.Project

class AddDeviceWizard(val source: DeviceSource, val project: Project?) {
  fun createDialog(): ComposeWizard {
    return ComposeWizard(project, "Add Device") { DeviceGridPage(source) }
  }
}

@Stable
internal class DeviceGridState(profiles: List<DeviceProfile>) {
  val selectionState = TableSelectionState<DeviceProfile>()
  val filterState = DeviceFilterState(profiles)
}

@Composable
internal fun WizardPageScope.DeviceGridPage(source: DeviceSource) {
  val profiles = source.profiles
  nextActionName = "Configure"
  finishActionName = "Add"

  val pageState = getOrCreateState { DeviceGridState(profiles) }
  val selectionState = pageState.selectionState
  val filterState = pageState.filterState
  DeviceTable(profiles, tableSelectionState = selectionState, filterState = filterState)

  val selection = selectionState.selection
  if (selection == null || !filterState.apply(selection)) {
    nextAction = WizardAction.Disabled
    finishAction = WizardAction.Disabled
  } else {
    val apiLevelForSelectedProfile = filterState.apiLevelFilter.apiLevelSelection.apply(selection)
    val profileWithSelectedApi =
      selection.update { apiLevels = sortedSetOf(apiLevelForSelectedProfile) }
    source.apply { selectionUpdated(profileWithSelectedApi) }
  }
}
