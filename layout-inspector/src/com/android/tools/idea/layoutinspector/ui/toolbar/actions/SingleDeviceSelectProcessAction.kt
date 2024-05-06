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
package com.android.tools.idea.layoutinspector.ui.toolbar.actions

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.appinspection.ide.ui.ICON_EMULATOR
import com.android.tools.idea.appinspection.ide.ui.ICON_PHONE
import com.android.tools.idea.appinspection.ide.ui.NO_PROCESS_ACTION
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcessDetectionSupport
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction

/**
 * A [DropDownAction] that shows the list of debuggable processes running in the device
 * corresponding to [targetDeviceSerialNumber]. Each process can be selected.
 *
 * This action will automatically show and hide itself when the device supports or doesn't support
 * auto-connect.
 */
class SingleDeviceSelectProcessAction(
  private val deviceModel: DeviceModel,
  private val targetDeviceSerialNumber: String,
  private val onProcessSelected: (newProcess: ProcessDescriptor) -> Unit,
) : DropDownAction("Select Process", "Select a process to connect to.", ICON_PHONE) {

  override fun displayTextInToolbar() = true

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    super.update(event)
    val targetDevice = deviceModel.devices.find { it.serial == targetDeviceSerialNumber }
    if (targetDevice == null) {
      // by default, don't show the process picker, unless auto-connect is off
      event.presentation.isVisible = !LayoutInspectorSettings.getInstance().autoConnectEnabled
      return
    }

    // no need to show the process picker if the device supports auto-connect
    event.presentation.isVisible =
      deviceModel.getForegroundProcessDetectionSupport(targetDevice) ==
        ForegroundProcessDetectionSupport.NOT_SUPPORTED
    event.presentation.icon = targetDevice.toIcon()
    deviceModel.selectedProcess?.name?.let { event.presentation.text = it }
  }

  public override fun updateActions(context: DataContext): Boolean {
    removeAll()

    val processes =
      deviceModel.processes
        .sortedBy { it.name }
        .filter { (it.isRunning) && (it.device.serial == targetDeviceSerialNumber) }

    for (process in processes) {
      add(SelectProcessAction(process))
    }
    if (childrenCount == 0) {
      add(NO_PROCESS_ACTION)
    }

    return true
  }

  private inner class SelectProcessAction(private val processDescriptor: ProcessDescriptor) :
    ToggleAction(processDescriptor.name) {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isSelected(event: AnActionEvent): Boolean {
      return processDescriptor == deviceModel.selectedProcess
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
      onProcessSelected(processDescriptor)
    }
  }
}

private fun DeviceDescriptor.toIcon() = if (isEmulator) ICON_EMULATOR else ICON_PHONE
