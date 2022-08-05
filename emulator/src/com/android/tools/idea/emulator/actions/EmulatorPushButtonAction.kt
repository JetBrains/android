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
package com.android.tools.idea.emulator.actions

import com.android.emulator.control.KeyboardEvent
import com.android.tools.idea.emulator.EmulatorConfiguration
import com.android.tools.idea.emulator.EmulatorController
import com.android.tools.idea.emulator.createHardwareKeyEvent
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

  override fun buttonPressed(event: AnActionEvent) {
    val emulatorController: EmulatorController = getEmulatorController(event) ?: return
    emulatorController.sendKey(createHardwareKeyEvent(keyName, eventType = KeyboardEvent.KeyEventType.keydown))
  }

  override fun buttonReleased(event: AnActionEvent) {
    val emulatorController: EmulatorController = getEmulatorController(event) ?: return
    emulatorController.sendKey(createHardwareKeyEvent(keyName, eventType = KeyboardEvent.KeyEventType.keyup))
  }

  /**
   * This method is called by the framework but does nothing. Real action happens in
   * the [buttonPressed] and [buttonReleased] methods.
   */
  final override fun actionPerformed(event: AnActionEvent) {
  }
}