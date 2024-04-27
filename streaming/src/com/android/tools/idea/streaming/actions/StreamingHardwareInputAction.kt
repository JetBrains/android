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

import com.android.tools.idea.streaming.core.AbstractDisplayView
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.device.DEVICE_VIEW_KEY
import com.android.tools.idea.streaming.emulator.EMULATOR_VIEW_KEY
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware

/**
 * ToggleAction for hardware input.
 *
 * When hardware input is enabled, Android Studio forwards unaltered mouse and keyboard events to
 * the device.
 */
internal class StreamingHardwareInputAction : ToggleAction(), DumbAware {

  override fun isSelected(event: AnActionEvent): Boolean {
    val deviceId = getDeviceId(event) ?: return false
    return event.project?.service<HardwareInputStateStorage>()?.isHardwareInputEnabled(deviceId) ?: false
  }

  override fun setSelected(event: AnActionEvent, selected: Boolean) {
    val deviceId = getDeviceId(event) ?: return
    event.project?.service<HardwareInputStateStorage>()?.setHardwareInputEnabled(deviceId, selected)
    getDisplayView(event)?.hardwareInputStateChanged(event, selected)
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    enableRichTooltip(event.presentation)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun getDisplayView(event: AnActionEvent): AbstractDisplayView? {
    return event.getData(EMULATOR_VIEW_KEY) ?: event.getData(DEVICE_VIEW_KEY)
  }

  private fun getDeviceId(event: AnActionEvent): DeviceId? {
    return getDisplayView(event)?.deviceId
  }

  companion object {
    const val ACTION_ID = "android.streaming.hardware.input"
  }
}

@Service(Service.Level.PROJECT)
internal class HardwareInputStateStorage {
  private val enabledDevices = mutableSetOf<String>()

  fun isHardwareInputEnabled(deviceId: DeviceId): Boolean {
    return enabledDevices.contains(deviceId.storageKey)
  }

  fun setHardwareInputEnabled(deviceId: DeviceId, enabled: Boolean) {
    if (enabled) {
      enabledDevices.add(deviceId.storageKey)
    } else {
      enabledDevices.remove(deviceId.storageKey)
    }
  }

  private val DeviceId.storageKey: String
    get() = when (this) {
      is DeviceId.EmulatorDeviceId -> this.emulatorId.avdFolder.toString()
      is DeviceId.PhysicalDeviceId -> this.serialNumber
    }
}
