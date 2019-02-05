/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.property.testing

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.TimerListener
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.ActionCallback
import java.awt.Component
import java.awt.event.InputEvent
import javax.swing.JComponent

private const val NOT_IMPLEMENTED = "Not implemented"

class FakeActionManager : ActionManager() {
  override fun createActionPopupMenu(place: String?, group: ActionGroup): ActionPopupMenu {
    error(NOT_IMPLEMENTED)
  }

  override fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean): ActionToolbar {
    error(NOT_IMPLEMENTED)
  }

  override fun getAction(actionId: String): AnAction {
    error(NOT_IMPLEMENTED)
  }

  override fun getId(action: AnAction): String {
    error(NOT_IMPLEMENTED)
  }

  override fun registerAction(actionId: String, action: AnAction) {
    error(NOT_IMPLEMENTED)
  }

  override fun registerAction(actionId: String, action: AnAction, pluginId: PluginId?) {
    error(NOT_IMPLEMENTED)
  }

  override fun unregisterAction(actionId: String) {
    error(NOT_IMPLEMENTED)
  }

  override fun getActionIds(idPrefix: String): Array<String> {
    error(NOT_IMPLEMENTED)
  }

  override fun isGroup(actionId: String): Boolean {
    error(NOT_IMPLEMENTED)
  }

  override fun createButtonToolbar(actionPlace: String?, messageActionGroup: ActionGroup): JComponent {
    error(NOT_IMPLEMENTED)
  }

  override fun getActionOrStub(id: String?): AnAction? {
    error(NOT_IMPLEMENTED)
  }

  override fun addTimerListener(delay: Int, listener: TimerListener?) {
    error(NOT_IMPLEMENTED)
  }

  override fun removeTimerListener(listener: TimerListener?) {
    error(NOT_IMPLEMENTED)
  }

  override fun addTransparentTimerListener(delay: Int, listener: TimerListener?) {
    error(NOT_IMPLEMENTED)
  }

  override fun removeTransparentTimerListener(listener: TimerListener?) {
    error(NOT_IMPLEMENTED)
  }

  override fun tryToExecute(action: AnAction,
                            inputEvent: InputEvent,
                            contextComponent: Component?,
                            place: String?,
                            now: Boolean): ActionCallback {
    error(NOT_IMPLEMENTED)
  }

  @Suppress("OverridingDeprecatedMember")
  override fun addAnActionListener(listener: AnActionListener?) {
    error(NOT_IMPLEMENTED)
  }

  @Suppress("OverridingDeprecatedMember")
  override fun removeAnActionListener(listener: AnActionListener?) {
    error(NOT_IMPLEMENTED)
  }

  override fun getKeyboardShortcut(actionId: String): KeyboardShortcut? {
    error(NOT_IMPLEMENTED)
  }
}
