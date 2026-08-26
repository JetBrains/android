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

import com.android.emulator.control.MicrophoneState
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.NotificationTracker
import com.android.tools.idea.streaming.emulator.getEmptyObserver
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent

/** Toggles the microphone input for the emulator. */
internal class EmulatorMicrophoneToggleAction : AbstractEmulatorAction(configFilter = { it.deviceType == DeviceType.AI_GLASSES }) {

  override fun actionPerformed(event: AnActionEvent) {
    val emulatorController = getEmulatorController(event) ?: return
    val microphoneInput = emulatorController.microphoneInput ?: return

    emulatorController.setMicrophoneState(MicrophoneState.newBuilder().setRealAudioEnabled(!microphoneInput).build(), getEmptyObserver())
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    val presentation = event.presentation
    val microphoneInput = getEmulatorController(event)?.microphoneInput
    if (microphoneInput == true) {
      presentation.icon = AllIcons.CodeWithMe.CwmMicOn
      presentation.text = "Disconnect Emulator from System Microphone"
    } else {
      if (microphoneInput == null) {
        presentation.isEnabled = false
      }
      presentation.icon = AllIcons.CodeWithMe.CwmMicOff
      presentation.text = "Connect Emulator to System Microphone"
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private val EmulatorController.microphoneInput: Boolean?
  get() = NotificationTracker.forEmulator(this).microphoneInput.value
