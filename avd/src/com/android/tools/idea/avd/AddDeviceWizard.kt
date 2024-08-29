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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.sdklib.devices.Device
import com.android.tools.idea.adddevicedialog.ComposeWizard
import com.android.tools.idea.adddevicedialog.DeviceFilterState
import com.android.tools.idea.adddevicedialog.DeviceGridPage
import com.android.tools.idea.adddevicedialog.DeviceLoadingPage
import com.android.tools.idea.adddevicedialog.DeviceProfile
import com.android.tools.idea.adddevicedialog.DeviceSource
import com.android.tools.idea.adddevicedialog.DeviceTableColumns
import com.android.tools.idea.adddevicedialog.FormFactor
import com.android.tools.idea.adddevicedialog.SingleSelectionDropdown
import com.android.tools.idea.adddevicedialog.TableSelectionState
import com.android.tools.idea.adddevicedialog.uniqueValuesOf
import com.android.tools.idea.avdmanager.ui.CreateDeviceAction
import com.android.tools.idea.avdmanager.ui.DeviceUiAction
import com.android.tools.idea.avdmanager.ui.ImportDevicesAction
import com.intellij.openapi.project.Project
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text

class AddDeviceWizard(val source: DeviceSource, val project: Project?) {
  fun createDialog(): ComposeWizard {
    return ComposeWizard(project, "Add Device") {
      val filterState = getOrCreateState { DeviceFilterState() }
      val selectionState = getOrCreateState { TableSelectionState<DeviceProfile>() }

      DeviceLoadingPage(source) { profiles ->
        // Holds a Device that should be selected as a result of a DeviceUiAction; e.g. when a new
        // Device is created, we select it automatically.
        var dialogSelectedDevice by remember { mutableStateOf<Device?>(null) }
        val deviceProvider =
          object : DeviceUiAction.DeviceProvider {
            override fun getDevice(): Device? =
              (selectionState.selection as? VirtualDeviceProfile)?.device

            override fun refreshDevices() {}

            override fun setDevice(device: Device?) {
              dialogSelectedDevice = device
            }

            override fun selectDefaultDevice() {
              selectionState.selection = null
            }

            override fun getProject(): Project? = this@AddDeviceWizard.project
          }
        if (dialogSelectedDevice != null) {
          profiles
            .find { (it as VirtualDeviceProfile).device == dialogSelectedDevice }
            ?.let {
              dialogSelectedDevice = null
              selectionState.selection = it
            }
        }

        Column {
          DeviceGridPage(
            profiles,
            avdColumns,
            filterContent = {
              SingleSelectionDropdown(
                FormFactor.uniqueValuesOf(profiles),
                filterState.formFactorFilter,
              )
            },
            filterState = filterState,
            selectionState = selectionState,
            onSelectionUpdated = { with(source) { selectionUpdated(it) } },
            modifier = Modifier.weight(1f),
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { CreateDeviceAction(deviceProvider).actionPerformed(null) }) {
              Text("New hardware profile...")
            }

            OutlinedButton(
              onClick = { ImportDevicesAction(deviceProvider).actionPerformed(null) }
            ) {
              Text("Import hardware profile...")
            }
          }
        }
      }
    }
  }
}

private val avdColumns =
  with(DeviceTableColumns) { persistentListOf(icon, name, width, height, density) }
