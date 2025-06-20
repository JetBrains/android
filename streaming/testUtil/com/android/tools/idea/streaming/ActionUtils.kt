/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT
import com.intellij.openapi.actionSystem.DataSnapshotProvider
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAwareWithCallbacks
import com.intellij.openapi.actionSystem.ex.ActionUtil.performDumbAwareUpdate
import com.intellij.openapi.actionSystem.impl.SimpleDataContext.getProjectContext
import com.intellij.openapi.project.Project
import com.intellij.ui.ComponentUtil.findParentByCondition
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.CTRL_DOWN_MASK
import java.awt.event.KeyEvent.KEY_RELEASED
import java.awt.event.KeyEvent.VK_E

/** Executes an action related to device streaming. */
fun executeAction(actionId: String, source: Component, project: Project? = null, place: String = ActionPlaces.TOOLBAR,
                  modifiers: Int = CTRL_DOWN_MASK, extra: DataSnapshotProvider? = null) {
  val action = ActionManager.getInstance().getAction(actionId)
  executeAction(action, source, project, place = place, modifiers = modifiers, extra = extra)
}

/** Executes an action related to device streaming. */
fun executeAction(action: AnAction, source: Component, project: Project? = null, place: String = ActionPlaces.TOOLBAR,
                  modifiers: Int = CTRL_DOWN_MASK, extra: DataSnapshotProvider? = null) {
  val event = createTestEvent(source, project, place = place, modifiers = modifiers, extra = extra)
  executeAction(action, event)
}

fun executeAction(action: AnAction, event: AnActionEvent) {
  performDumbAwareUpdate(action, event, true)
  assertThat(event.presentation.isEnabledAndVisible).isTrue()
  performActionDumbAwareWithCallbacks(action, event)
}

fun updateAndGetActionPresentation(actionId: String, source: Component, project: Project? = null,
                                   place: String = ActionPlaces.KEYBOARD_SHORTCUT, extra: DataSnapshotProvider? = null): Presentation {
  val action = ActionManager.getInstance().getAction(actionId)
  return updateAndGetActionPresentation(action, source, project, place = place, extra = extra)
}

fun updateAndGetActionPresentation(action: AnAction, source: Component, project: Project? = null,
                                   place: String = ActionPlaces.KEYBOARD_SHORTCUT, extra: DataSnapshotProvider? = null): Presentation {
  val event = createTestEvent(source, project, place, presentation = action.templatePresentation.clone(), extra = extra)
  return updateAndGetActionPresentation(action, event)
}

fun updateAndGetActionPresentation(action: AnAction, event: AnActionEvent): Presentation {
  performDumbAwareUpdate(action, event, false)
  return event.presentation
}

fun createTestEvent(source: Component, project: Project? = null, place: String = ActionPlaces.KEYBOARD_SHORTCUT,
                    modifiers: Int = CTRL_DOWN_MASK, presentation: Presentation = Presentation(),
                    extra: DataSnapshotProvider? = null): AnActionEvent {
  val inputEvent = KeyEvent(source, KEY_RELEASED, System.currentTimeMillis(), modifiers, VK_E, CHAR_UNDEFINED)
  val rootContext = extra.toDataContext(project?.let { getProjectContext(it) } ?: EMPTY_CONTEXT)
  val dataContext = createDataContext(source, rootContext)
  return AnActionEvent.createEvent(dataContext, presentation, place, ActionUiKind.NONE, inputEvent)
}

private fun createDataContext(component: Component?, rootContext: DataContext): DataContext {
  val c = findParentByCondition(component) { it is UiDataProvider } ?: return rootContext
  val parentContext = createDataContext(c.parent, rootContext)
  return (c as UiDataProvider).toDataSnapshotProvider().toDataContext(parentContext)
}

private fun DataSnapshotProvider?.toDataContext(parent: DataContext): DataContext =
    this?.let { CustomizedDataContext.withSnapshot(parent, this) } ?: parent

private fun UiDataProvider.toDataSnapshotProvider(): DataSnapshotProvider =
    DataSnapshotProvider { sink -> sink.uiDataSnapshot(this@UiDataProvider) }
