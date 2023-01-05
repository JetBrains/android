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
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_DOWN_AND_UP
import com.android.tools.idea.streaming.device.AndroidKeyEventActionType.ACTION_UP
import com.android.tools.idea.streaming.device.DeviceConfiguration
import com.android.tools.idea.streaming.device.KeyEventMessage
import com.intellij.openapi.actionSystem.AnActionEvent
import java.util.function.Predicate

/**
 * Simulates pressing and releasing a button on an Android device.
 */
internal open class DevicePushButtonAction(
  private val keyCode: Int,
  configFilter: Predicate<DeviceConfiguration>? = null,
) : AbstractDeviceAction(configFilter), PushButtonAction {

  final override fun buttonPressed(event: AnActionEvent) {
    getDeviceController(event)?.sendControlMessage(KeyEventMessage(ACTION_DOWN, keyCode, metaState = 0))
  }

  final override fun buttonReleased(event: AnActionEvent) {
    getDeviceController(event)?.sendControlMessage(KeyEventMessage(ACTION_UP, keyCode, metaState = 0))
  }

  final override fun buttonPressedAndReleased(event: AnActionEvent) {
    getDeviceController(event)?.sendControlMessage(KeyEventMessage(ACTION_DOWN_AND_UP, keyCode, metaState = 0))
  }

  final override fun actionPerformed(event: AnActionEvent) {
    actionPerformedImpl(event)
  }
}