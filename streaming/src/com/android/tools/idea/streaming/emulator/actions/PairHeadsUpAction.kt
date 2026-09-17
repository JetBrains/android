/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator.actions

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.core.AbstractDevicePanel
import com.android.tools.idea.streaming.core.findBySerialNumber
import com.android.tools.idea.streaming.core.pairedPhoneId
import com.android.tools.idea.streaming.core.serialNumber
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content

/**
 * Finds an AI Glasses AVD in the Running Devices window and calls [DeviceHeadsUpListener.userInvolvementRequired] with the serial numbers
 * of the AI Glasses and its paired phone. If the glasses are not paired, the first running handheld device is used.
 */
class PairHeadsUpAction : AnAction(), DumbAware {

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID) ?: return

    val contents: List<Content> = toolWindow.contentManager.contentsRecursively
    val glassPanel = contents.findPanelOfDeviceType(DeviceType.AI_GLASSES) ?: return
    val glassSerialNumber = glassPanel.id.serialNumber
    val phoneSerialNumber =
      getPairedPhoneSerialNumber(glassSerialNumber, project)
        ?: contents.findPanelOfDeviceType(DeviceType.HANDHELD)?.id?.serialNumber
        ?: return

    project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).userInvolvementRequired(glassSerialNumber, phoneSerialNumber, project)
  }

  private fun Iterable<Content>.findPanelOfDeviceType(deviceType: DeviceType): AbstractDevicePanel<*>? =
    find { (it.component as? AbstractDevicePanel<*>)?.deviceType == deviceType }?.component as? AbstractDevicePanel<*>

  private fun getPairedPhoneSerialNumber(glassesSerialNumber: String, project: Project): String? {
    val deviceProvisioner = project.service<DeviceProvisionerService>().deviceProvisioner
    val devices = deviceProvisioner.devices.value
    val pairedId = devices.findBySerialNumber(glassesSerialNumber)?.pairedPhoneId ?: return null
    return devices.find { it.id == pairedId }?.serialNumber
  }
}
