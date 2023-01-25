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

import com.android.tools.idea.streaming.PushButtonAction
import com.android.tools.idea.streaming.device.actions.DevicePushButtonAction
import com.android.tools.idea.streaming.emulator.actions.EmulatorPushButtonAction
import com.android.tools.idea.streaming.emulator.actions.getEmulatorController
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

internal abstract class StreamingPushButtonAction(
  virtualDeviceAction: EmulatorPushButtonAction,
  physicalDeviceAction: DevicePushButtonAction
) : AbstractStreamingAction<EmulatorPushButtonAction, DevicePushButtonAction>(virtualDeviceAction, physicalDeviceAction),
    PushButtonAction, DumbAware {

  override fun buttonPressed(event: AnActionEvent) {
    if (getEmulatorController(event) == null) {
      physicalDeviceAction.buttonPressed(event)
    }
    else {
      virtualDeviceAction.buttonPressed(event)
    }
  }

  override fun buttonReleased(event: AnActionEvent) {
    if (getEmulatorController(event) == null) {
      physicalDeviceAction.buttonReleased(event)
    }
    else {
      virtualDeviceAction.buttonReleased(event)
    }
  }

  override fun buttonPressedAndReleased(event: AnActionEvent) {
    if (getEmulatorController(event) == null) {
      physicalDeviceAction.buttonPressedAndReleased(event)
    }
    else {
      virtualDeviceAction.buttonPressedAndReleased(event)
    }
  }
}