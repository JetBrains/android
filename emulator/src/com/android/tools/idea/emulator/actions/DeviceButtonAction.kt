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
import com.android.tools.idea.emulator.EmulatorController
import com.android.tools.idea.emulator.createHardwareKeyEvent
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent

/**
 * Simulates pressing and releasing a button on an Android virtual device.
 */
open class DeviceButtonAction(private val keyName: String) : AbstractEmulatorAction(), CustomComponentAction {

  /**
   * Called when the left mouse button is pressed over the corresponding toolbar button.
   */
  fun buttonPressed(event: AnActionEvent) {
    val emulatorController: EmulatorController = getEmulatorController(event) ?: return
    emulatorController.sendKey(createHardwareKeyEvent(keyName, eventType = KeyboardEvent.KeyEventType.keydown))
  }

  /**
   * Called when the left mouse button is released.
   */
  fun buttonReleased(event: AnActionEvent) {
    val emulatorController: EmulatorController = getEmulatorController(event) ?: return
    emulatorController.sendKey(createHardwareKeyEvent(keyName, eventType = KeyboardEvent.KeyEventType.keyup))
  }

  /**
   * This method is a no-op. Real action happens in the [buttonPressed] and [buttonReleased] methods.
   */
  final override fun actionPerformed(event: AnActionEvent) {}

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return MyActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  }

  private class MyActionButton(
    action: DeviceButtonAction,
    presentation: Presentation,
    place: String,
    minimumSize: Dimension
  ) : ActionButton(action, presentation, place, minimumSize) {

    init {
      // Pressing the SPACE key is the same as clicking the button.
      addKeyListener(object : KeyAdapter() {
        override fun keyPressed(keyEvent: KeyEvent) {
          if (keyEvent.modifiers == 0 && keyEvent.keyCode == KeyEvent.VK_SPACE) {
            val event = AnActionEvent.createFromAnAction(action, keyEvent, myPlace, dataContext)
            action.buttonPressed(event)
          }
        }

        override fun keyReleased(keyEvent: KeyEvent) {
          if (keyEvent.modifiers == 0 && keyEvent.keyCode == KeyEvent.VK_SPACE) {
            val event = AnActionEvent.createFromAnAction(action, keyEvent, myPlace, dataContext)
            action.buttonReleased(event)
          }
        }
      })
    }

    override fun onMousePressed(mouseEvent: MouseEvent) {
      super.onMousePressed(mouseEvent)
      val event = AnActionEvent.createFromAnAction(action, mouseEvent, myPlace, dataContext)
      action.buttonPressed(event)
    }

    override fun onMouseReleased(mouseEvent: MouseEvent) {
      super.onMouseReleased(mouseEvent)
      val event = AnActionEvent.createFromAnAction(action, mouseEvent, myPlace, dataContext)
      action.buttonReleased(event)
    }

    private val action
      get() = myAction as DeviceButtonAction
  }
}