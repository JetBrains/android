/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.emulator.control.InputEvent
import com.android.emulator.control.XrCommand
import com.android.sdklib.deviceprovisioner.DeviceType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent

/** Shows the taskbar on an XR AVD. */
class EmulatorXrShowTaskbarAction : AbstractEmulatorAction(configFilter = { it.deviceType == DeviceType.XR }) {

  override fun actionPerformed(event: AnActionEvent) {
    val emulator = getEmulatorController(event) ?: return
    val message = InputEvent.newBuilder().setXrCommand(XrCommand.newBuilder().setAction(XrCommand.Action.SHOW_TASKBAR))
    emulator.getOrCreateInputEventSender().onNext(message.build())
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
