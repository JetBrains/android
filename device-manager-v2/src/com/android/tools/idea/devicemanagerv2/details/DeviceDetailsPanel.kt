/*
 * Copyright (C) 2023 The aoid Open Source Project
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
import com.android.tools.idea.devicemanagerv2.DeviceManagerPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A panel within the [DeviceManagerPanel] that shows a [DeviceInfoPanel], and if the device
 * supports Wear pairing, a [PairedDevicesPanel]. If both panels are present, a tabbed pane is used
 * to switch between them.
 */
internal class DeviceDetailsPanel
private constructor(
  val scope: CoroutineScope,
  val handle: DeviceHandle,
  mainComponent: JComponent,
  private val tabbedPane: JBTabbedPane?,
) : CloseablePanel(handle.state.properties.title, mainComponent), Disposable {

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
    fun create(scope: CoroutineScope, handle: DeviceHandle): DeviceDetailsPanel {
      val deviceInfoPanel = DeviceInfoPanel()
      scope.launch { populateDeviceInfo(deviceInfoPanel, handle) }

      val pairedDevicesPanel =
        handle.state.properties.wearPairingId?.let { PairedDevicesPanel(scope, handle) }
      val tabbedPane =
        pairedDevicesPanel?.let { createTabbedPane(deviceInfoPanel, pairedDevicesPanel) }
      val mainComponent = tabbedPane ?: JBScrollPane(deviceInfoPanel)
      return DeviceDetailsPanel(scope, handle, mainComponent, tabbedPane)
    }

    private fun createTabbedPane(
      deviceInfoPanel: DeviceInfoPanel,
      pairedDevicesPanel: PairedDevicesPanel
    ) =
      JBTabbedPane().apply {
        tabComponentInsets = JBUI.emptyInsets()
        insertTab("Device Info", null, JBScrollPane(deviceInfoPanel), null, DEVICE_INFO_TAB_INDEX)
        insertTab(
          "Paired Devices",
          null,
          JBScrollPane(pairedDevicesPanel),
          null,
          PAIRED_DEVICES_TAB_INDEX
        )
      }

    private const val DEVICE_INFO_TAB_INDEX = 0
    private const val PAIRED_DEVICES_TAB_INDEX = 1
  }
}
