/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.streaming

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
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
 * An action represented by a push button that may do two different things when the button is
 * pressed and when the button is released. Classes implementing this interface should not do
 * anything in the `actionPerformed` method since it is never called. The [buttonPressed] and
 * [buttonReleased] methods are used instead.
 */
interface PushButtonAction : CustomComponentAction {

  /**
   * Called when the left mouse button is pressed over the corresponding toolbar button.
   */
  fun buttonPressed(event: AnActionEvent)

  /**
   * Called when the left mouse button is released.
   */
  fun buttonReleased(event: AnActionEvent)

  /**
   * Called when the action is invoked by a keyboard shortcut.
   */
  fun buttonPressedAndReleased(event: AnActionEvent)

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return MyActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  }

  /**
   * Implementations of this interface must call this method from their `actionPerformed` methods.
   */
  fun actionPerformedImpl(event: AnActionEvent) {
    val inputEvent = event.inputEvent as? KeyEvent ?: return
    if (inputEvent.keyCode != KeyEvent.VK_SPACE) {
      // The action was triggered by a keyboard shortcut.
      buttonPressedAndReleased(event)
    }
  }

  private class MyActionButton(
    action: PushButtonAction,
    presentation: Presentation,
    place: String,
    minimumSize: Dimension
  ) : ActionButton(action as AnAction, presentation, place, minimumSize) {

    init {
      // Pressing the SPACE key is the same as clicking the button.
      addKeyListener(object : KeyAdapter() {
        override fun keyPressed(keyEvent: KeyEvent) {
          if (keyEvent.modifiersEx == 0 && keyEvent.keyCode == KeyEvent.VK_SPACE) {
            val event = AnActionEvent.createFromAnAction(myAction, keyEvent, myPlace, dataContext)
            action.buttonPressed(event)
          }
        }

        override fun keyReleased(keyEvent: KeyEvent) {
          if (keyEvent.modifiersEx == 0 && keyEvent.keyCode == KeyEvent.VK_SPACE) {
            val event = AnActionEvent.createFromAnAction(myAction, keyEvent, myPlace, dataContext)
            action.buttonReleased(event)
          }
        }
      })
    }

    override fun onMousePressed(mouseEvent: MouseEvent) {
      super.onMousePressed(mouseEvent)
      val event = AnActionEvent.createFromAnAction(myAction, mouseEvent, myPlace, dataContext)
      action.buttonPressed(event)
    }

    override fun onMouseReleased(mouseEvent: MouseEvent) {
      super.onMouseReleased(mouseEvent)
      val event = AnActionEvent.createFromAnAction(myAction, mouseEvent, myPlace, dataContext)
      action.buttonReleased(event)
    }

    private val action
      get() = myAction as PushButtonAction
  }
}