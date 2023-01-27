/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.emulator.control.KeyboardEvent
import com.android.tools.idea.streaming.PushButtonAction
import com.android.tools.idea.streaming.createHardwareKeyEvent
import com.android.tools.idea.streaming.emulator.EmulatorConfiguration
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import java.util.function.Predicate

/**
 * Simulates pressing and releasing a button on an Android virtual device.
 *
 * @param configFilter determines the types of devices the action is applicable to
 */
open class EmulatorPushButtonAction(
  private val keyName: String,
  configFilter: Predicate<EmulatorConfiguration>? = null,
) : AbstractEmulatorAction(configFilter = configFilter), PushButtonAction {

  final override fun buttonPressed(event: AnActionEvent) {
    getEmulatorController(event)?.sendKey(createHardwareKeyEvent(keyName, eventType = KeyboardEvent.KeyEventType.keydown))
  }

  final override fun buttonReleased(event: AnActionEvent) {
    getEmulatorController(event)?.sendKey(createHardwareKeyEvent(keyName, eventType = KeyboardEvent.KeyEventType.keyup))
  }

  final override fun buttonPressedAndReleased(event: AnActionEvent) {
    getEmulatorController(event)?.sendKey(createHardwareKeyEvent(keyName, eventType = KeyboardEvent.KeyEventType.keypress))
  }

  final override fun actionPerformed(event: AnActionEvent) {
    actionPerformedImpl(event)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}