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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.Text

@Composable
fun <DeviceT : DeviceProfile> WizardPageScope.DeviceLoadingPage(
  source: DeviceSource<DeviceT>,
  content: @Composable (List<DeviceT>) -> Unit,
) {
  val profiles by remember { source.profiles }.collectAsState(LoadingState.Loading)

  nextActionName = "Configure"
  finishActionName = "Add"

  when (val profiles = profiles) {
    LoadingState.Loading -> {
      nextAction = WizardAction.Disabled
      finishAction = WizardAction.Disabled
      Box(Modifier.fillMaxSize()) { Text("Loading devices...", Modifier.align(Alignment.Center)) }
    }
    is LoadingState.Ready -> {
      content(profiles.value)
    }
  }
}

@Composable
fun <DeviceT : DeviceProfile> WizardPageScope.DefaultDeviceGridPage(
  profiles: List<DeviceT>,
  columns: List<TableColumn<DeviceT>>,
  filterContent: @Composable () -> Unit,
  filterState: DeviceFilterState<DeviceT>,
  selectionState: TableSelectionState<DeviceT> = getOrCreateState { TableSelectionState() },
  onSelectionUpdated: (DeviceT) -> Unit,
  modifier: Modifier = Modifier,
) {
  DeviceGridPage(filterState, selectionState, onSelectionUpdated) {
    DeviceTable(
      profiles,
      columns,
      filterContent = filterContent,
      tableSelectionState = selectionState,
      filterState = filterState,
      modifier = modifier,
    )
  }
}

@Composable
fun <DeviceT : DeviceProfile> WizardPageScope.DeviceGridPage(
  filterState: DeviceFilterState<DeviceT>,
  selectionState: TableSelectionState<DeviceT> = getOrCreateState { TableSelectionState() },
  onSelectionUpdated: (DeviceT) -> Unit,
  content: @Composable () -> Unit,
) {
  content()

  val selection = selectionState.selection
  if (selection == null || !filterState.apply(selection)) {
    nextAction = WizardAction.Disabled
    finishAction = WizardAction.Disabled
  } else {
    onSelectionUpdated(selection)
  }
}
