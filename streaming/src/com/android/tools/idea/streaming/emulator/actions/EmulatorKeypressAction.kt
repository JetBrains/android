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

import com.android.tools.idea.streaming.emulator.EmulatorConfiguration
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.createKeyboardEvent
import com.intellij.openapi.actionSystem.AnActionEvent
import java.util.function.Predicate

/**
 * Common superclass for toolbar actions that send a keypress event to the Emulator.
 *
 * @param configFilter determines the types of devices the action is applicable to
 */
abstract class EmulatorKeypressAction(
  private val keyName: String,
  configFilter: Predicate<EmulatorConfiguration>? = null,
) : AbstractEmulatorAction(configFilter = configFilter) {

  override fun actionPerformed(event: AnActionEvent) {
    val emulatorController: EmulatorController = getEmulatorController(event) ?: return
    emulatorController.sendKey(createKeyboardEvent(keyName))
  }
}