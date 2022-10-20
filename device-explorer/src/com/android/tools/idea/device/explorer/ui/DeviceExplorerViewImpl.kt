/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.ui

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.tools.idea.device.explorer.DeviceExplorerModel
import com.android.tools.idea.deviceprovisioner.DeviceHandleRenderer
import com.android.tools.idea.deviceprovisioner.toIterable
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLoadingPanel
import icons.AndroidIcons
import kotlinx.coroutines.flow.collect
import java.awt.BorderLayout
import java.util.concurrent.CancellationException
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JList

class DeviceExplorerViewImpl(project: Project, private val model: DeviceExplorerModel, private val toolWindowID: String): DeviceExplorerView {
  private val listeners: MutableList<DeviceExplorerViewListener> = ArrayList()
  private val loadingPanel: JBLoadingPanel = JBLoadingPanel(BorderLayout(), project)
  private val panel = DeviceExplorerPanel()

  val component: JComponent
    get() = loadingPanel


  override fun addListener(listener: DeviceExplorerViewListener) {
    listeners.add(listener)
  }

  override fun removeListener(listener: DeviceExplorerViewListener) {
    listeners.remove(listener)
  }

  override fun setup() {
    loadingPanel.add(panel.component, BorderLayout.CENTER)
    panel.deviceCombo.renderer = DeviceNameRenderer()
    panel.deviceCombo.addActionListener {
      val sel = panel.deviceCombo.selectedItem
      if (sel is DeviceHandle) {
        listeners.forEach(Consumer { it.deviceSelected(sel) })
      }
      else {
        listeners.forEach(Consumer { it.noDeviceSelected() })
      }
    }
    showPanel()
  }

  override fun addTab(tab: JComponent, title: String) {
    panel.tabPane.addTab(title, tab)
  }

  override suspend fun trackDeviceListChanges() {
    val devicesInComboBox = mutableSetOf<DeviceHandle>()

    model.devices.collect { newDevices ->
      (devicesInComboBox - newDevices.toSet()).forEach {
        devicesInComboBox.remove(it)
        panel.deviceCombo.removeItem(it)
      }

      for (device in newDevices) {
        if (devicesInComboBox.add(device)) {
          panel.deviceCombo.addItem(device)
        }
      }

      showPanel()
    }
  }

  override suspend fun trackActiveDeviceChanges() {
    model.activeDevice.collect {
      panel.deviceCombo.selectedItem = it
    }
  }

  override fun reportErrorGeneric(message: String, t: Throwable) { reportError(message, t) }

  private fun reportError(message: String, t: Throwable) {
    if (t is CancellationException) {
      return
    }

    val updatedMessage = if (t.message != null) "$message: ${t.message}" else message
    val notification = Notification(toolWindowID, toolWindowID, updatedMessage, NotificationType.WARNING)

    ApplicationManager.getApplication().invokeLater {
      Notifications.Bus.notify(notification)
    }
  }

  private fun showPanel() {
    if (panel.deviceCombo.itemCount == 0) {
      showNoDeviceScreen()
    } else {
      showActiveDeviceScreen()
    }
  }

  private fun showActiveDeviceScreen() {
    panel.showTabs()
  }

  private fun showNoDeviceScreen() {
    panel.showMessageLayer(
      "Connect a device via USB cable or run an Android Virtual Device",
      AndroidIcons.DeviceExplorer.DevicesLineup,
      false
    )
  }

  private class DeviceNameRenderer : ColoredListCellRenderer<DeviceHandle>() {
    override fun customizeCellRenderer(list: JList<out DeviceHandle>, value: DeviceHandle?, index: Int, selected: Boolean, hasFocus: Boolean) {
      if (value == null) {
        append("No Connected Devices", SimpleTextAttributes.ERROR_ATTRIBUTES)
        return
      }

      DeviceHandleRenderer.renderDevice(this, value, list.model.toIterable())
    }
  }
}