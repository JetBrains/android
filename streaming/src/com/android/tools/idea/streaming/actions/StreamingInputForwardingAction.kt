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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.emulator.EMULATOR_CONTROLLER_KEY
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

/**
 * ToggleAction for input forwarding.
 *
 * When input forwarding is enabled, Android Studio forwards unaltered mouse and keyboard events to
 * the device.
 */
internal class StreamingInputForwardingAction : ToggleAction("Input Forwarding"), DumbAware {

  override fun isSelected(event: AnActionEvent): Boolean {
    val deviceId = getDeviceId(event) ?: return false
    return event.project?.getService(InputForwardingStateStorage::class.java)?.isInputForwardingEnabled(deviceId) ?: false
  }

  override fun setSelected(event: AnActionEvent, selected: Boolean) {
    val deviceId = getDeviceId(event) ?: return
    event.project?.getService(InputForwardingStateStorage::class.java)?.setInputForwardingEnabled(deviceId, selected)
  }

  override fun update(event: AnActionEvent) {
    val presentation = event.presentation
    val enabled = StudioFlags.STREAMING_INPUT_FORWARDING_BUTTON.get()
    presentation.isEnabledAndVisible = enabled
    if (enabled) {
      super.update(event)
    }
  }

  private fun getDeviceId(event: AnActionEvent): DeviceId? {
    val emulatorController = event.getData(EMULATOR_CONTROLLER_KEY)
    if (emulatorController != null) {
      return DeviceId.ofEmulator(emulatorController.emulatorId)
    }
    val serialNumber = event.getData(SERIAL_NUMBER_KEY) ?: return null
    return DeviceId.ofPhysicalDevice(serialNumber)
  }
}

@Service(Service.Level.PROJECT)
@VisibleForTesting
internal class InputForwardingStateStorage {
  private val enabledDevices = mutableSetOf<String>()

  fun isInputForwardingEnabled(deviceId: DeviceId): Boolean {
    return enabledDevices.contains(deviceId.storageKey)
  }

  fun setInputForwardingEnabled(deviceId: DeviceId, enabled: Boolean) {
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

  companion object {
    @JvmStatic
    fun getInstance(project: Project): InputForwardingStateStorage =
        project.getService(InputForwardingStateStorage::class.java)
  }
}
