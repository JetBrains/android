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
import com.android.tools.idea.streaming.core.findByAvdFolder
import com.android.tools.idea.streaming.core.pairedPhoneId
import com.android.tools.idea.streaming.core.serialNumber
import com.android.tools.idea.streaming.emulator.EmulatorToolWindowPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Finds an AI Glasses AVD in the Running Devices window and calls [DeviceHeadsUpListener.userInvolvementRequired] with the serial numbers
 * of the AI Glasses and its paired phone.
 */
class PairHeadsUpAction : AnAction(), DumbAware {

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID) ?: return

    val contents = toolWindow.contentManager.contentsRecursively

    for (content in contents) {
      val panel = content.component as? EmulatorToolWindowPanel ?: continue
      val emulator = panel.emulator
      if (emulator.emulatorConfig.deviceType == DeviceType.AI_GLASSES) {
        val glassSerialNumber = emulator.emulatorId.serialNumber
        val avdFolder = emulator.emulatorId.avdFolder

        val deviceProvisioner = project.service<DeviceProvisionerService>().deviceProvisioner
        val devices = deviceProvisioner.devices.value

        val glassDevice = devices.findByAvdFolder(avdFolder) ?: continue
        val pairedId = glassDevice.pairedPhoneId ?: continue

        val pairedDevice = devices.find { it.id == pairedId } ?: continue
        val phoneSerialNumber = pairedDevice.serialNumber ?: continue

        project.messageBus.syncPublisher(DeviceHeadsUpListener.TOPIC).userInvolvementRequired(glassSerialNumber, phoneSerialNumber, project)
        break
      }
    }
  }
}
