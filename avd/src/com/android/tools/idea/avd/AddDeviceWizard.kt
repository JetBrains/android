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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.sdklib.devices.Device
import com.android.tools.idea.adddevicedialog.ComposeWizard
import com.android.tools.idea.adddevicedialog.DeviceFilterState
import com.android.tools.idea.adddevicedialog.DeviceGridPage
import com.android.tools.idea.adddevicedialog.DeviceLoadingPage
import com.android.tools.idea.adddevicedialog.DeviceTable
import com.android.tools.idea.adddevicedialog.DeviceTableColumns
import com.android.tools.idea.adddevicedialog.DeviceTableShowDetailsState
import com.android.tools.idea.adddevicedialog.FormFactor
import com.android.tools.idea.adddevicedialog.SingleSelectionRadioButtons
import com.android.tools.idea.adddevicedialog.TableColumn
import com.android.tools.idea.adddevicedialog.TableColumnWidth
import com.android.tools.idea.adddevicedialog.TableSelectionState
import com.android.tools.idea.adddevicedialog.TableSortState
import com.android.tools.idea.adddevicedialog.TableTextColumn
import com.android.tools.idea.adddevicedialog.WizardAction
import com.android.tools.idea.adddevicedialog.WizardButton
import com.android.tools.idea.adddevicedialog.WizardPageScope
import com.android.tools.idea.adddevicedialog.uniqueValuesOf
import com.android.tools.idea.avdmanager.AccelerationErrorCode
import com.android.tools.idea.avdmanager.ui.CloneDeviceAction
import com.android.tools.idea.avdmanager.ui.CreateDeviceAction
import com.android.tools.idea.avdmanager.ui.DeleteDeviceAction
import com.android.tools.idea.avdmanager.ui.DeviceUiAction
import com.android.tools.idea.avdmanager.ui.EditDeviceAction
import com.android.tools.idea.avdmanager.ui.ExportDeviceAction
import com.android.tools.idea.avdmanager.ui.ImportDevicesAction
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBMenuItem
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.util.ui.JBUI
import icons.StudioIconsCompose
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.bridge.LocalComponent
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon

internal class AddDeviceWizard(
  val source: LocalVirtualDeviceSource,
  val project: Project?,
  val accelerationCheck: () -> AccelerationErrorCode,
) {

  fun createDialog(): ComposeWizard {
    return ComposeWizard(project, "Add Device", minimumSize = DEVICE_DIALOG_MIN_SIZE) {
      DeviceGridPage()
    }
  }

  @Composable
  internal fun WizardPageScope.DeviceGridPage() {
    val component = LocalComponent.current
    val density = LocalDensity.current

    var accelerationError by remember { mutableStateOf(AccelerationErrorCode.ALREADY_INSTALLED) }
    LaunchedEffect(Unit) {
      withContext(AndroidDispatchers.workerThread) { accelerationError = accelerationCheck() }
    }

    val deviceTableShowDetailsState = getOrCreateState { DeviceTableShowDetailsState() }
    val lazyListState = getOrCreateState { LazyListState() }
    val tableSortState = getOrCreateState { TableSortState<VirtualDeviceProfile>() }
    val filterState = getOrCreateState { VirtualDeviceFilterState() }
    val selectionState = getOrCreateState { TableSelectionState<VirtualDeviceProfile>() }

    val profilesFlow = remember { source.profiles }
    DeviceLoadingPage(profilesFlow) { profiles ->
      // Holds a Device that should be selected as a result of a DeviceUiAction; e.g. when a new
      // Device is created, we select it automatically.
      var dialogSelectedDevice by remember { mutableStateOf<Device?>(null) }
      val deviceProvider = remember {
        object : DeviceUiAction.DeviceProvider {
          override fun getDevice(): Device? = selectionState.selection?.device

          override fun refreshDevices() {}

          override fun setDevice(device: Device?) {
            dialogSelectedDevice = device
          }

          override fun selectDefaultDevice() {
            selectionState.selection = null
          }

          override fun getProject(): Project? = this@AddDeviceWizard.project
        }
      }
      if (dialogSelectedDevice != null) {
        profiles
          .find { it.device == dialogSelectedDevice }
          ?.let {
            dialogSelectedDevice = null
            filterState.formFactorFilter.selection = it.formFactor
            selectionState.selection = it
          }
      }
      leftSideButtons =
        remember(deviceProvider) {
          listOf(
            WizardButton(
              "New hardware profile...",
              WizardAction { CreateDeviceAction(deviceProvider).actionPerformed(null) },
            ),
            WizardButton(
              "Import hardware profile...",
              WizardAction { ImportDevicesAction(deviceProvider).actionPerformed(null) },
            ),
          )
        }

      Column {
        if (accelerationError != AccelerationErrorCode.ALREADY_INSTALLED) {
          val coroutineScope = rememberCoroutineScope()
          AccelerationErrorBanner(
            accelerationError,
            refresh = { coroutineScope.launch { accelerationError = accelerationCheck() } },
            Modifier.padding(vertical = 8.dp),
          )
        }

        DeviceGridPage(
          filterState = filterState,
          selectionState = selectionState,
          onSelectionUpdated = { with(source) { selectionUpdated(it) } },
        ) {
          DeviceTable(
            profiles,
            avdColumns,
            filterContent = {
              SingleSelectionRadioButtons(
                FormFactor.uniqueValuesOf(profiles),
                filterState.formFactorFilter,
              )
              Divider(
                orientation = Orientation.Horizontal,
                Modifier.padding(vertical = 16.dp, horizontal = 2.dp),
              )
              CheckboxRow(
                "Show obsolete device profiles",
                checked = filterState.showDeprecated,
                onCheckedChange = { filterState.showDeprecated = it },
              )
            },
            showDetailsState = deviceTableShowDetailsState,
            lazyListState = lazyListState,
            tableSortState = tableSortState,
            tableSelectionState = selectionState,
            filterState = filterState,
            onRowSecondaryClick = { device, offset ->
              selectionState.selection = device

              val menu = JBPopupMenu()

              fun createMenuItem(action: DeviceUiAction) =
                JBMenuItem(action).apply { text = action.text }

              menu.add(createMenuItem(CloneDeviceAction(deviceProvider)))
              menu.add(createMenuItem(EditDeviceAction(deviceProvider)))
              menu.add(createMenuItem(ExportDeviceAction(deviceProvider)))
              menu.add(createMenuItem(DeleteDeviceAction(deviceProvider)))

              menu.show(
                component,
                (offset.x / density.density).toInt(),
                (offset.y / density.density).toInt(),
              )
            },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

private class VirtualDeviceFilterState : DeviceFilterState<VirtualDeviceProfile>() {
  var showDeprecated: Boolean by mutableStateOf(false)

  override fun apply(row: VirtualDeviceProfile): Boolean =
    super.apply(row) && (showDeprecated || !row.isDeprecated)
}

private val virtualDeviceName =
  TableTextColumn<VirtualDeviceProfile>(
    "Name",
    TableColumnWidth.Weighted(3f),
    attribute = { if (it.isDeprecated) it.name + " (Obsolete)" else it.name },
    maxLines = 2,
  )

private val playColumn =
  TableColumn<VirtualDeviceProfile>(
    "Play",
    TableColumnWidth.Fixed(45.dp),
    comparator = compareBy { it.isGooglePlaySupported },
  ) {
    if (it.isGooglePlaySupported) {
      Icon(
        StudioIconsCompose.Avd.DevicePlayStore,
        contentDescription = "Play Store supported",
        modifier = Modifier.size(16.dp),
      )
    }
  }

private val avdColumns =
  with(DeviceTableColumns) {
    persistentListOf(icon, virtualDeviceName, playColumn, apiRange, width, height, density)
  }

internal val DEVICE_DIALOG_MIN_SIZE
  get() = JBUI.size(750, 550)
