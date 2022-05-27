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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.appinspection.ide.ui.ICON_EMULATOR
import com.android.tools.idea.appinspection.ide.ui.ICON_PHONE
import com.android.tools.idea.appinspection.ide.ui.NO_DEVICE_ACTION
import com.android.tools.idea.appinspection.ide.ui.buildDeviceName
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.pipeline.DeviceModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons
import javax.swing.JComponent

/**
 * Action used to display a dropdown of all inspectable devices.
 *
 * The UI of this action always reflects the [DeviceModel] passed as argument.
 */
class SelectDeviceAction(
  private val deviceModel: DeviceModel,
  private val onDeviceSelected: (newDevice: DeviceDescriptor) -> Unit,
  private val createDeviceLabel: (DeviceDescriptor) -> String = Companion::createDefaultDeviceLabel,
  private val stopPresentation: StopPresentation = StopPresentation(),
  private val onStopAction: ((ProcessDescriptor) -> Unit)? = null,
  private val customDeviceAttribution: (DeviceDescriptor, AnActionEvent) -> Unit = { _, _ -> }
) :
  DropDownAction(
    "Select device",
    "Select a device to connect to.",
    ICON_PHONE) {

  companion object {
    private fun createDefaultDeviceLabel(device: DeviceDescriptor): String {
      return device.buildDeviceName()
    }
  }

  private var lastDevice: DeviceDescriptor? = null
  private var lastDevicesCount = 0
  var button: JComponent? = null
    private set

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place).also { button = it }
  }

  override fun update(event: AnActionEvent) {
    val currentDevice = deviceModel.selectedDevice
    val content = currentDevice?.let {
      createDeviceLabel(it)
    } ?: if (deviceModel.devices.isEmpty()) {
      "No device available"
    } else {
      "No device selected"
    }

    event.presentation.icon = currentDevice?.toIcon()
    event.presentation.text = content

    lastDevice = currentDevice
    lastDevicesCount = deviceModel.devices.size
  }

  public override fun updateActions(context: DataContext): Boolean {
    removeAll()

    // Rebuild the action tree.
    deviceModel.devices.sortedBy { it.buildDeviceName() }.forEach { device -> add(DeviceAction(device)) }

    if (childrenCount == 0) {
      add(NO_DEVICE_ACTION)
    }

    // For consistency, always add a stop action, but only enable it if there's a current process
    // that can actually be stopped.
    val stopInspectionAction = object : AnAction(stopPresentation.text,
                                                 stopPresentation.desc,
                                                 StudioIcons.Shell.Toolbar.STOP) {
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = (deviceModel.selectedProcess?.isRunning == true)
      }

      override fun actionPerformed(e: AnActionEvent) {
        onStopAction?.invoke(deviceModel.selectedProcess!!)
      }
    }
    add(stopInspectionAction)

    return true
  }

  override fun displayTextInToolbar() = true

  class StopPresentation(val text: String = "Stop Inspector",
                         val desc: String = "Stop all inspector running on the selected device.")

  private inner class DeviceAction(
    private val device: DeviceDescriptor,
  ) : ToggleAction(device.buildDeviceName(), null, device.toIcon()) {
    override fun displayTextInToolbar() = true

    override fun update(event: AnActionEvent) {
      super.update(event)
      customDeviceAttribution(device, event)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return device == deviceModel.selectedDevice
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      onDeviceSelected.invoke(device)
    }
  }
}

private fun DeviceDescriptor?.toIcon() = if (this?.isEmulator == true) ICON_EMULATOR else ICON_PHONE