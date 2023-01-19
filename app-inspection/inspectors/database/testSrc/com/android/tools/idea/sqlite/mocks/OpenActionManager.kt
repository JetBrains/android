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

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.TimerListener
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionPopupMenuListener
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.ActionCallback
import java.awt.Component
import java.awt.event.InputEvent
import java.util.function.Function
import javax.swing.JComponent

open class OpenActionManager(private val wrapped: ActionManagerEx) : ActionManagerEx() {
  override fun createActionPopupMenu(place: String, group: ActionGroup): ActionPopupMenu = wrapped.createActionPopupMenu(place, group)
  override fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean, separatorCreator: Function<String, Component>) = wrapped.createActionToolbar(place, group, horizontal, separatorCreator)
  override fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean, decorateButtons: Boolean): ActionToolbar = wrapped.createActionToolbar(place, group, horizontal, decorateButtons)
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
  override fun fireAfterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) = wrapped.fireAfterActionPerformed(action, event, result)
  override fun fireAfterEditorTyping(c: Char, dataContext: DataContext) = wrapped.fireAfterEditorTyping(c, dataContext)
  override fun fireBeforeActionPerformed(action: AnAction, event: AnActionEvent) = wrapped.fireBeforeActionPerformed(action, event)
  override fun fireBeforeEditorTyping(c: Char, dataContext: DataContext) = wrapped.fireBeforeEditorTyping(c, dataContext)
  override fun getActionOrStub(id: String): AnAction?  = wrapped.getActionOrStub(id)
  override fun addTimerListener(listener: TimerListener)  = wrapped.addTimerListener(listener)
  override fun removeTimerListener(listener: TimerListener)  = wrapped.removeTimerListener(listener)
  override fun tryToExecute(action: AnAction, inputEvent: InputEvent?, contextComponent: Component?, place: String?, now: Boolean): ActionCallback = wrapped.tryToExecute(action, inputEvent, contextComponent, place, now)
  override val isActionPopupStackEmpty: Boolean = wrapped.isActionPopupStackEmpty
  override val lastPreformedActionId: String? = wrapped.lastPreformedActionId
  override val prevPreformedActionId: String? = wrapped.prevPreformedActionId
  override val registrationOrderComparator: Comparator<String> = wrapped.registrationOrderComparator
  override fun addActionPopupMenuListener(listener: ActionPopupMenuListener, parentDisposable: Disposable) = wrapped.addActionPopupMenuListener(listener, parentDisposable)
  override fun addAnActionListener(listener: AnActionListener?)  = wrapped.addAnActionListener(listener)
  override fun removeAnActionListener(listener: AnActionListener?)  = wrapped.removeAnActionListener(listener)
  override fun getKeyboardShortcut(actionId: String): KeyboardShortcut?  = wrapped.getKeyboardShortcut(actionId)
  override fun getPluginActions(pluginId: PluginId): Array<String> = wrapped.getPluginActions(pluginId)
}