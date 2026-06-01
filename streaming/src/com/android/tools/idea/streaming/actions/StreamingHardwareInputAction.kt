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
package com.android.tools.idea.streaming.actions

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.actions.enableRichTooltip
import com.android.tools.idea.streaming.core.StreamingDeviceId
import com.android.tools.idea.streaming.xr.XrInputMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil.createConcurrentList

/**
 * ToggleAction for hardware input.
 *
 * When hardware input is enabled, Android Studio forwards unaltered mouse and keyboard events to the device.
 */
internal class StreamingHardwareInputAction : ToggleAction(), DumbAware {

  override fun isSelected(event: AnActionEvent): Boolean {
    val displayView = getDisplayView(event) ?: return false
    return event.hardwareInputStateStorage?.isHardwareInputEnabled(displayView.deviceId) == true
  }

  override fun setSelected(event: AnActionEvent, selected: Boolean) {
    val displayView = getDisplayView(event) ?: return
    event.hardwareInputStateStorage?.setHardwareInputEnabled(displayView.deviceId, selected)
    displayView.hardwareInputStateChanged(event, selected)
  }

  override fun update(event: AnActionEvent) {
    super.update(event)

    val presentation = event.presentation
    val deviceType = getDeviceType(event)
    if (deviceType == DeviceType.AI_GLASSES || deviceType == DeviceType.XR_HEADSET && isHandOrEyeTrackingEnabled(event)) {
      presentation.isEnabledAndVisible = false
      return
    }

    val controller = getXrInputController(event)
    if (controller != null && controller.inputMode != XrInputMode.MOUSE) {
      presentation.isEnabled = false
      Toggleable.setSelected(presentation, false)
    }
    presentation.enableRichTooltip(this)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  companion object {
    const val ACTION_ID = "android.streaming.hardware.input"
  }
}

private val AnActionEvent.hardwareInputStateStorage: HardwareInputStateStorage?
  get() = project?.let { HardwareInputStateStorage.getInstance(it) }

@Service(Service.Level.PROJECT)
internal class HardwareInputStateStorage {

  private val enabledDevices = createConcurrentList<String>()

  fun isHardwareInputEnabled(deviceId: StreamingDeviceId): Boolean = enabledDevices.contains(deviceId.storageKey)

  fun setHardwareInputEnabled(deviceId: StreamingDeviceId, enabled: Boolean) {
    when {
      enabled -> enabledDevices.addIfAbsent(deviceId.storageKey)
      else -> enabledDevices.remove(deviceId.storageKey)
    }
  }

  private val StreamingDeviceId.storageKey: String
    get() =
      when (this) {
        is StreamingDeviceId.EmulatorDeviceId -> emulatorId.avdId
        is StreamingDeviceId.PhysicalDeviceId -> serialNumber
      }

  companion object {
    fun getInstance(project: Project): HardwareInputStateStorage = project.service()
  }
}
