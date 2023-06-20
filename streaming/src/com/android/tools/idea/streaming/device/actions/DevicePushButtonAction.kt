/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.streaming.device.actions

import com.android.tools.idea.streaming.PushButtonAction
import com.android.tools.idea.streaming.device.AKEYCODE_UNKNOWN
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN_AND_UP
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_UP
import com.android.tools.idea.streaming.device.DeviceConfiguration
import com.android.tools.idea.streaming.device.KeyEventMessage
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import java.util.function.Predicate

/**
 * Simulates pressing and releasing a button on an Android device.
 *
 * @param keyCode the code of the button to press
 * @param modifierKeyCode if not AKEYCODE_UNKNOWN, the code of the second button that is pressed
 *     before the first and released after it
 * @param configFilter determines the types of devices the action is applicable to
 */
internal open class DevicePushButtonAction(
  private val keyCode: Int,
  private val modifierKeyCode: Int = AKEYCODE_UNKNOWN,
  configFilter: Predicate<DeviceConfiguration>? = null,
) : AbstractDeviceAction(configFilter), PushButtonAction {

  final override fun buttonPressed(event: AnActionEvent) {
    val deviceController = getDeviceController(event) ?: return
    if (modifierKeyCode != AKEYCODE_UNKNOWN) {
      deviceController.sendControlMessage(KeyEventMessage(ACTION_DOWN, modifierKeyCode, metaState = 0))
    }
    deviceController.sendControlMessage(KeyEventMessage(ACTION_DOWN, keyCode, metaState = 0))
  }

  final override fun buttonReleased(event: AnActionEvent) {
    val deviceController = getDeviceController(event) ?: return
    getDeviceController(event)?.sendControlMessage(KeyEventMessage(ACTION_UP, keyCode, metaState = 0))
    if (modifierKeyCode != AKEYCODE_UNKNOWN) {
      deviceController.sendControlMessage(KeyEventMessage(ACTION_UP, modifierKeyCode, metaState = 0))
    }
  }

  final override fun buttonPressedAndReleased(event: AnActionEvent) {
    val deviceController = getDeviceController(event) ?: return
    if (modifierKeyCode != AKEYCODE_UNKNOWN) {
      deviceController.sendControlMessage(KeyEventMessage(ACTION_DOWN, modifierKeyCode, metaState = 0))
    }
    deviceController.sendControlMessage(KeyEventMessage(ACTION_DOWN_AND_UP, keyCode, metaState = 0))
    if (modifierKeyCode != AKEYCODE_UNKNOWN) {
      deviceController.sendControlMessage(KeyEventMessage(ACTION_UP, modifierKeyCode, metaState = 0))
    }
  }

  final override fun actionPerformed(event: AnActionEvent) {
    actionPerformedImpl(event)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}