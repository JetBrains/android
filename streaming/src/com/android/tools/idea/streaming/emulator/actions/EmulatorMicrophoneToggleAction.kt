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
import com.android.repository.Revision
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.NotificationReceiver
import com.android.tools.idea.streaming.emulator.getEmptyObserver
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager

/** Toggles the microphone input for the emulator. */
internal class EmulatorMicrophoneToggleAction : AbstractEmulatorAction(configFilter = { it.deviceType == DeviceType.AI_GLASSES }) {

  // TODO: Remove emulator version check after 2026-09-01.
  val emulatorSupported =
    ApplicationManager.getApplication().isUnitTestMode ||
      AvdManagerConnection.getDefaultAvdManagerConnection().emulator?.version?.let { it >= Revision(36, 5, 7) } ?: false

  override fun actionPerformed(event: AnActionEvent) {
    if (!emulatorSupported) {
      return
    }
    val emulatorController = getEmulatorController(event) ?: return
    val microphoneInput = emulatorController.microphoneInput ?: return

    emulatorController.setMicrophoneState(MicrophoneState.newBuilder().setRealAudioEnabled(!microphoneInput).build(), getEmptyObserver())
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    val presentation = event.presentation
    if (!emulatorSupported) {
      presentation.isEnabledAndVisible = false
      return
    }
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
  get() = NotificationReceiver.forEmulator(this).microphoneInput.value
