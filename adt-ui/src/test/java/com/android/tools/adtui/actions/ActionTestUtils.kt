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

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import java.awt.event.InputEvent

const val SEPARATOR_TEXT = "------------------------------------------------------"

/**
 * Helper function to convert action to string for testing purpose. Use [filter] to ignore the actions if needed.
 */
@JvmOverloads
fun prettyPrintActions(
  action: AnAction, filter: (action: AnAction) -> Boolean = { true }, presentationFactory: PresentationFactory? = null
): String {
  val stringBuilder = StringBuilder()
  prettyPrintActions(action, stringBuilder, 0, filter, presentationFactory)
  return stringBuilder.toString()
}

/**
 * Run [prettyPrintActions] recursively.
 */
private fun prettyPrintActions(
  action: AnAction, sb: StringBuilder, depth: Int, filter: (action: AnAction) -> Boolean, presentationFactory: PresentationFactory?
) {
  appendActionText(sb, action, presentationFactory, depth)
  (action as? DefaultActionGroup)?.let { group ->
    if (!group.isPopup) {
      // If it is not a popup, the actions in the group would be flatted.
      for (child in group.getChildren(null)) {
        appendActionText(sb, child, presentationFactory, depth)
      }
    }
    else {
      for (child in group.childActionsOrStubs) {
        if (!filter(child)) {
          continue
        }
        prettyPrintActions(child, sb, depth + 1, filter, presentationFactory)
      }
    }
  }
}

private fun appendActionText(sb: StringBuilder, action: AnAction, presentationFactory: PresentationFactory?, depth: Int) {
  val text = action.toText(presentationFactory)
  if (text != null) {
    for (i in 0 until depth) {
      sb.append("    ")
    }
    sb.append(text).append("\n")
  }
}

private fun AnAction.toText(presentationFactory: PresentationFactory?): String? {
  if (this is Separator) {
    return SEPARATOR_TEXT
  }
  val event = createTestActionEvent(this, presentationFactory = presentationFactory)
  update(event)
  // Add a visual representation to selected actions.
  return "${if (Toggleable.isSelected(event.presentation)) "✔ " else ""}${event.presentation.text}"
}

/**
 * Create an [AnActionEvent] for testing purpose.
 */
@JvmOverloads
fun createTestActionEvent(
  action: AnAction,
  inputEvent: InputEvent? = null,
  dataContext: DataContext = DataContext.EMPTY_CONTEXT,
  presentationFactory: PresentationFactory? = null
): AnActionEvent {
  val presentation = presentationFactory?.getPresentation(action) ?: action.templatePresentation.clone()
  return AnActionEvent(inputEvent, dataContext, "", presentation, ActionManager.getInstance(), 0)
}