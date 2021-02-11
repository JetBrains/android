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
package com.android.tools.idea.sqlite.mocks

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

open class OpenActionManager(private val wrapped: ActionManager) : ActionManager() {
  override fun createActionPopupMenu(place: String, group: ActionGroup): ActionPopupMenu = wrapped.createActionPopupMenu(place, group)
  override fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean): ActionToolbar = wrapped.createActionToolbar(place, group, horizontal)
  override fun getAction(actionId: String): AnAction = wrapped.getAction(actionId)
  override fun getId(action: AnAction): String?  = wrapped.getId(action)
  override fun registerAction(actionId: String, action: AnAction)  = wrapped.registerAction(actionId, action)
  override fun registerAction(actionId: String, action: AnAction, pluginId: PluginId?)  = wrapped.registerAction(actionId, action, pluginId)
  override fun unregisterAction(actionId: String)  = wrapped.unregisterAction(actionId)
  override fun replaceAction(actionId: String, newAction: AnAction)  = wrapped.replaceAction(actionId, newAction)
  override fun getActionIds(idPrefix: String): Array<String>  = wrapped.getActionIds(idPrefix)
  override fun getActionIdList(idPrefix: String): MutableList<String>  = wrapped.getActionIdList(idPrefix)
  override fun isGroup(actionId: String): Boolean  = wrapped.isGroup(actionId)
  override fun createButtonToolbar(actionPlace: String, messageActionGroup: ActionGroup): JComponent = wrapped.createButtonToolbar(actionPlace, messageActionGroup)
  override fun getActionOrStub(id: String): AnAction?  = wrapped.getActionOrStub(id)
  override fun addTimerListener(delay: Int, listener: TimerListener)  = wrapped.addTimerListener(delay, listener)
  override fun removeTimerListener(listener: TimerListener)  = wrapped.removeTimerListener(listener)
  override fun tryToExecute(action: AnAction, inputEvent: InputEvent, contextComponent: Component?, place: String?, now: Boolean): ActionCallback = wrapped.tryToExecute(action, inputEvent, contextComponent, place, now)
  override fun addAnActionListener(listener: AnActionListener?)  = wrapped.addAnActionListener(listener)
  override fun removeAnActionListener(listener: AnActionListener?)  = wrapped.removeAnActionListener(listener)
  override fun getKeyboardShortcut(actionId: String): KeyboardShortcut?  = wrapped.getKeyboardShortcut(actionId)
}