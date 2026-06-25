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
package com.android.tools.idea.devicemanagerv2.details

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.devicemanagerv2.DeviceManagerPanel
import com.android.tools.idea.devicemanagerv2.PairingStatus
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow

/**
 * A panel within the [DeviceManagerPanel] that shows a [DeviceInfoPanel], and if the device supports Wear pairing, a [PairedDevicesPanel].
 * If both panels are present, a tabbed pane is used to switch between them.
 */
internal class DeviceDetailsPanel
private constructor(val scope: CoroutineScope, heading: String, mainComponent: JComponent, private val tabbedPane: JBTabbedPane?) :
  CloseablePanel(heading, mainComponent), Disposable {

  fun showDeviceInfo() {
    tabbedPane?.let { it.selectedIndex = DEVICE_INFO_TAB_INDEX }
  }

  fun showPairedDevices() {
    tabbedPane?.let { it.selectedIndex = PAIRED_DEVICES_TAB_INDEX }
  }

  override fun dispose() {
    scope.cancel()
  }

  companion object {
    fun create(scope: CoroutineScope, template: DeviceTemplate): DeviceDetailsPanel {
      val deviceInfoPanel = DeviceInfoPanel()
      deviceInfoPanel.populateDeviceInfo(template.properties)
      deviceInfoPanel.powerLabel.isVisible = false
      deviceInfoPanel.availableStorageLabel.isVisible = false
      return DeviceDetailsPanel(scope, template.properties.title, deviceInfoPanel, null)
    }

    fun create(
      project: Project?,
      scope: CoroutineScope,
      handle: DeviceHandle,
      devicesFlow: Flow<List<DeviceHandle>>,
      pairedDevicesFlow: Flow<Map<String, List<PairingStatus>>>,
    ): DeviceDetailsPanel {
      val deviceInfoPanel = DeviceInfoPanel()
      deviceInfoPanel.trackDeviceProperties(scope, handle)
      deviceInfoPanel.trackDevicePowerAndStorage(scope, handle)

      val wearPairingId = handle.state.properties.wearPairingId
      val isGlassesOrPhoneForGlasses =
        StudioFlags.AI_GLASSES_PAIRING_DETAILS_ENABLED.get() &&
          (handle.state.properties.deviceType == DeviceType.AI_GLASSES || handle.state.properties.deviceType == DeviceType.HANDHELD)

      val pairingDelegates = buildList {
        if (wearPairingId != null) {
          add(WearPairingDelegate(project))
        }
        if (isGlassesOrPhoneForGlasses) {
          add(GlassesPairingDelegate(project, devicesFlow))
        }
      }

      val pairedDevicesPanel =
        if (pairingDelegates.isNotEmpty()) {
          PairedDevicesPanel.create(
            pairingDelegates = pairingDelegates,
            scope = scope,
            uiContext = Dispatchers.EDT,
            handle = handle,
            devicesFlow = devicesFlow,
          )
        } else null

      val tabbedPane =
        pairedDevicesPanel?.let { panel ->
          JBTabbedPane().apply {
            tabComponentInsets = JBUI.emptyInsets()
            insertTab("Device Info", null, JBScrollPane(deviceInfoPanel), null, DEVICE_INFO_TAB_INDEX)
            insertTab("Paired Devices", null, JBScrollPane(panel), null, PAIRED_DEVICES_TAB_INDEX)
          }
        }

      val mainComponent = tabbedPane ?: JBScrollPane(deviceInfoPanel)
      return DeviceDetailsPanel(scope, handle.state.properties.title, mainComponent, tabbedPane)
    }

    private const val DEVICE_INFO_TAB_INDEX = 0
    private const val PAIRED_DEVICES_TAB_INDEX = 1
  }
}
