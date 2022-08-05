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
import com.android.tools.idea.appinspection.ide.ui.NO_PROCESS_ACTION
import com.android.tools.idea.appinspection.ide.ui.buildDeviceName
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.pipeline.DeviceModel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import icons.StudioIcons
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Action used to display a dropdown of all inspectable devices.
 *
 * The UI of this action always reflects the [DeviceModel] passed as argument.
 */
class SelectDeviceAction(
  private val deviceModel: DeviceModel,
  private val onDeviceSelected: (newDevice: DeviceDescriptor) -> Unit,
  private val onProcessSelected: (newProcess: ProcessDescriptor) -> Unit,
  private val detachPresentation: DetachPresentation = DetachPresentation(),
  private val onDetachAction: ((ProcessDescriptor) -> Unit)? = null,
  private val customDeviceAttribution: (DeviceDescriptor, AnActionEvent) -> Unit = { _, _ -> }
) :
  DropDownAction(
    "Select device",
    "Select a device to connect to.",
    ICON_PHONE) {

  var button: JComponent? = null
    private set

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place).also { button = it }
  }

  override fun update(event: AnActionEvent) {
    val selectedDevice = deviceModel.selectedDevice
    val selectedProcess = deviceModel.selectedProcess

    val dropDownPresentation = if (selectedDevice != null) {
      // if a device is selected, use the device
      DropDownPresentation(createDeviceLabel(selectedDevice), selectedDevice.toIcon())
    }
    else if (selectedProcess != null) {
      // if a device is not selected, but a process is, use the process's device
      // this is for the case where ForegroundProcessDetection does not work, and we fall back to having the user selecting the process.
      DropDownPresentation(createDeviceLabel(selectedProcess.device, selectedProcess), selectedProcess.device.toIcon())
    }
    else if (deviceModel.devices.isEmpty()) {
      DropDownPresentation("No Device Available", null)
    }
    else {
      DropDownPresentation("No Device Selected", null)
    }

    event.presentation.icon = dropDownPresentation.icon
    event.presentation.text = dropDownPresentation.text
  }

  public override fun updateActions(context: DataContext): Boolean {
    removeAll()

    // Rebuild the action tree.
    deviceModel.devices.sortedBy { it.buildDeviceName() }.forEach { device ->
      if (deviceModel.supportsForegroundProcessDetection(device)) {
        add(DeviceAction(device))
      }
      else {
        add(DeviceProcessPickerAction(device))
      }
    }

    if (childrenCount == 0) {
      add(NO_DEVICE_ACTION)
    }

    // For consistency, always add a detach action, but only enable it if there's a current process
    // that can actually be detached.
    val detachInspectionAction = object : AnAction(detachPresentation.text,
                                                   detachPresentation.desc,
                                                   StudioIcons.Shell.Toolbar.STOP) {
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = (deviceModel.selectedProcess?.isRunning == true)
      }

      override fun actionPerformed(e: AnActionEvent) {
        onDetachAction?.invoke(deviceModel.selectedProcess!!)
      }
    }
    add(detachInspectionAction)

    return true
  }

  override fun displayTextInToolbar() = true

  class DetachPresentation(val text: String = "Detach Inspector",
                           val desc: String = "Detach the inspector from the process being inspected.")

  /**
   * A device which the user can select.
   */
  private inner class DeviceAction(
    private val device: DeviceDescriptor,
  ) : ToggleAction(device.buildDeviceName(), null, device.toIcon()) {
    override fun displayTextInToolbar() = true

    override fun update(event: AnActionEvent) {
      super.update(event)
      // restore the icon after the Action was de-selected
      if (!Toggleable.isSelected(event.presentation)) {
        event.presentation.icon = device.toIcon()
      }
      customDeviceAttribution(device, event)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return device == deviceModel.selectedDevice
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      onDeviceSelected.invoke(device)
    }
  }

  /**
   * A device with all its debuggable processes, which the user can select.
   */
  private inner class DeviceProcessPickerAction(
    private val device: DeviceDescriptor,
  ) : DropDownAction(device.buildDeviceName(), null, device.toIcon()) {
    override fun displayTextInToolbar() = true

    init {
      val processes = deviceModel.processes
        .sortedBy { it.name }
        .filter { (it.isRunning) && (it.device.serial == device.serial) }

      for (process in processes) {
        add(ConnectAction(process))
      }
      if (childrenCount == 0) {
        add(NO_PROCESS_ACTION)
      }
    }

    override fun update(event: AnActionEvent) {
      super.update(event)
      customDeviceAttribution(device, event)
    }
  }

  private inner class ConnectAction(private val processDescriptor: ProcessDescriptor) :
    ToggleAction(processDescriptor.name) {
    override fun isSelected(event: AnActionEvent): Boolean {
      return processDescriptor == deviceModel.selectedProcess
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      onProcessSelected(processDescriptor)
    }
  }
}

private fun createDeviceLabel(device: DeviceDescriptor, process: ProcessDescriptor? = null): String {
  return if (process != null) {
    "${device.buildDeviceName()} > ${process.name}"
  }
  else {
    device.buildDeviceName()
  }
}

private fun DeviceDescriptor.toIcon() = if (isEmulator) ICON_EMULATOR else ICON_PHONE

private data class DropDownPresentation(val text: String, val icon: Icon?)