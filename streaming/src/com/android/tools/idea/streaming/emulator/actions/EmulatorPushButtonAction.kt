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

import com.android.emulator.control.KeyboardEvent.KeyEventType
import com.android.tools.idea.streaming.core.PushButtonAction
import com.android.tools.idea.streaming.emulator.EmulatorConfiguration
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.streaming.emulator.sendKeyEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import java.util.function.Predicate

/**
 * Simulates pressing and releasing a button on an Android virtual device.
 *
 * @param keyName the name of the button to press
 * @param modifierKeyName if not null, the name of the second button that is pressed before
 *     the first and released after it
 * @param configFilter determines the types of devices the action is applicable to
 * @param skinOverridesConfigFilter presence of a skin button with the same key name overrides hiding by [configFilter]
 */
open class EmulatorPushButtonAction(
  private val keyName: String,
  private val modifierKeyName: String? = null,
  configFilter: Predicate<EmulatorConfiguration>? = null,
  private val skinOverridesConfigFilter: Boolean = false,
) : AbstractEmulatorAction(configFilter = configFilter), PushButtonAction {

  final override fun buttonPressed(event: AnActionEvent) {
    val emulatorController = getEmulatorController(event) ?: return
    if (modifierKeyName != null) {
      emulatorController.sendKeyEvent(modifierKeyName, eventType = KeyEventType.keydown)
    }
    emulatorController.sendKeyEvent(keyName, eventType = KeyEventType.keydown)
  }

  final override fun buttonReleased(event: AnActionEvent) {
    val emulatorController = getEmulatorController(event) ?: return
    emulatorController.sendKeyEvent(keyName, eventType = KeyEventType.keyup)
    if (modifierKeyName != null) {
      emulatorController.sendKeyEvent(modifierKeyName, eventType = KeyEventType.keyup)
    }
  }

  final override fun buttonPressedAndReleased(event: AnActionEvent) {
    val emulatorController = getEmulatorController(event) ?: return
    if (modifierKeyName != null) {
      emulatorController.sendKeyEvent(modifierKeyName, eventType = KeyEventType.keydown)
    }
    emulatorController.sendKeyEvent(keyName, eventType = KeyEventType.keypress)
    if (modifierKeyName != null) {
      emulatorController.sendKeyEvent(modifierKeyName, eventType = KeyEventType.keyup)
    }
  }

  final override fun actionPerformed(event: AnActionEvent) {
    actionPerformedImpl(event)
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    if (skinOverridesConfigFilter) {
      // Make the action visible if it is hidden due to configuration filter but has a key matching
      // one of the skin buttons.
      val presentation = event.presentation
      if (!presentation.isVisible) {
        val emulatorView = getEmulatorView(event) ?: return
        val emulatorController = emulatorView.emulator
        if (emulatorController.connectionState == ConnectionState.CONNECTED) {
          val skin = emulatorController.getSkin(emulatorView.currentPosture?.posture) ?: return
          if (skin.layout.buttons.find { it.keyName == keyName } != null) {
            presentation.isEnabledAndVisible = true
          }
        }
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}