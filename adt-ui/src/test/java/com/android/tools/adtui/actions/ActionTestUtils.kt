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
@file:JvmName("ActionTestUtils")
package com.android.tools.adtui.actions

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
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAwareWithCallbacks
import com.intellij.openapi.actionSystem.ex.ActionUtil.performDumbAwareUpdate
import com.intellij.openapi.actionSystem.impl.SimpleDataContext.getProjectContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.ComponentUtil.findParentByCondition
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.InputEvent.CTRL_DOWN_MASK
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.CHAR_UNDEFINED
import java.awt.event.KeyEvent.KEY_RELEASED
import java.awt.event.KeyEvent.VK_E
import javax.swing.JPanel

/** Executes an action. */
fun executeAction(actionId: String, source: Component? = null, project: Project? = null, place: String = ActionPlaces.TOOLBAR,
                  modifiers: Int = CTRL_DOWN_MASK, extra: DataSnapshotProvider? = null) {
  val action = ActionManager.getInstance().getAction(actionId)
  executeAction(action, source, project, place = place, modifiers = modifiers, extra = extra)
}

/** Executes an action. */
fun executeAction(action: AnAction, source: Component? = null, project: Project? = null, place: String = ActionPlaces.TOOLBAR,
                  modifiers: Int = CTRL_DOWN_MASK, extra: DataSnapshotProvider? = null) {
  val event = createTestEvent(source, project, place = place, modifiers = modifiers, extra = extra)
  executeAction(action, event)
}

/** Executes an action. */
fun executeAction(action: AnAction, event: AnActionEvent) {
  performDumbAwareUpdate(action, event, true)
  assertThat(event.presentation.isEnabledAndVisible).isTrue()
  performActionDumbAwareWithCallbacks(action, event)
}

/** Calls update on the action and returns [Presentation]. */
fun updateAndGetActionPresentation(actionId: String, source: Component? = null, project: Project? = null,
                                   place: String = ActionPlaces.KEYBOARD_SHORTCUT, extra: DataSnapshotProvider? = null): Presentation {
  val action = ActionManager.getInstance().getAction(actionId)
  return updateAndGetActionPresentation(action, source, project, place = place, extra = extra)
}

/** Calls update on the action and returns [Presentation]. */
fun updateAndGetActionPresentation(action: AnAction, source: Component? = null, project: Project? = null,
                                   place: String = ActionPlaces.KEYBOARD_SHORTCUT, extra: DataSnapshotProvider? = null): Presentation {
  val event = createTestEvent(source, project, place, presentation = action.templatePresentation.clone(), extra = extra)
  return updateAndGetActionPresentation(action, event)
}

/** Calls update on the action and returns [Presentation]. */
fun updateAndGetActionPresentation(action: AnAction, event: AnActionEvent): Presentation {
  performDumbAwareUpdate(action, event, false)
  return event.presentation
}

/** Creates a [AnActionEvent] for testing an action. */
fun createTestEvent(source: Component? = null, project: Project? = null, place: String = ActionPlaces.KEYBOARD_SHORTCUT,
                    modifiers: Int = CTRL_DOWN_MASK, presentation: Presentation = Presentation(),
                    extra: DataSnapshotProvider? = null): AnActionEvent {
  val inputEvent = KeyEvent(source ?: JPanel(), KEY_RELEASED, System.currentTimeMillis(), modifiers, VK_E, CHAR_UNDEFINED)
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
    DataSnapshotProvider { sink -> sink.uiDataSnapshot(this@toDataSnapshotProvider) }

const val SEPARATOR_TEXT = "------------------------------------------------------"

/**
 * Helper function to convert action to string for testing purpose. Use [filter] to ignore the actions if needed.
 */
@JvmOverloads
fun prettyPrintActions(
  action: AnAction, filter: (action: AnAction) -> Boolean = { true }, dataContext: DataContext = EMPTY_CONTEXT
): String {
  val stringBuilder = StringBuilder()
  prettyPrintActions(action, stringBuilder, 0, filter, dataContext)
  return stringBuilder.toString()
}

/** Runs [prettyPrintActions] recursively. */
private fun prettyPrintActions(
  action: AnAction, sb: StringBuilder, depth: Int, filter: (action: AnAction) -> Boolean, dataContext: DataContext
) {
  appendActionText(sb, action, depth, dataContext)
  (action as? DefaultActionGroup)?.let { group ->
    if (!group.isPopup) {
      // If it is not a popup, the actions in the group would be flatted.
      for (child in group.getChildren(ActionManager.getInstance())) {
        appendActionText(sb, child, depth, dataContext)
      }
    }
    else {
      for (child in group.childActionsOrStubs) {
        if (!filter(child)) {
          continue
        }
        prettyPrintActions(child, sb, depth + 1, filter, dataContext)
      }
    }
  }
}

private fun appendActionText(sb: StringBuilder, action: AnAction, depth: Int, dataContext: DataContext) {
  val text = action.toText(dataContext)
  for (i in 0 until depth) {
    sb.append("    ")
  }
  sb.append(text).append("\n")
}

private fun AnAction.toText(dataContext: DataContext): String {
  if (this is Separator) {
    return SEPARATOR_TEXT
  }
  val event = createTestActionEvent(this, dataContext = dataContext)
  update(event)
  // Add a visual representation to selected actions.
  return "${if (Toggleable.isSelected(event.presentation)) "✔ " else ""}${event.presentation.text}"
}

/** Creates an [AnActionEvent] for testing purpose. */
@JvmOverloads
fun createTestActionEvent(action: AnAction, inputEvent: InputEvent? = null, dataContext: DataContext = EMPTY_CONTEXT): AnActionEvent {
  val presentation = action.templatePresentation.clone()
  return AnActionEvent.createEvent(dataContext, presentation, "", ActionUiKind.NONE, inputEvent)
}

/** Finds an action within the [DefaultActionGroup] that uses [text] as display text. */
suspend fun DefaultActionGroup.findActionByText(text: String): AnAction? {
  return allChildActionsOrStubs()
    .find {
      val testEvent = TestActionEvent.createTestEvent()
      readAction { it.update(testEvent) }
      (testEvent.presentation.text ?: it.templateText) == text
    }
}

/** Returns all the [AnAction] contains in the [DefaultActionGroup] and any sub-groups. */
private fun DefaultActionGroup.allChildActionsOrStubs(): Collection<AnAction> {
  return childActionsOrStubs
    .flatMap {
      if (it is DefaultActionGroup)
        it.allChildActionsOrStubs().asSequence()
      else sequenceOf(it)
    }
}
