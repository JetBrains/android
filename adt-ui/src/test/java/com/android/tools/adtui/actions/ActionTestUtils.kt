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
import java.awt.event.InputEvent

/**
 * Helper function to convert action to string for testing purpose. Use [filter] to ignore the actions if needed.
 */
@JvmOverloads
fun prettyPrintActions(action: AnAction, filter: (action: AnAction) -> Boolean = { true }): String {
  val stringBuilder = StringBuilder()
  prettyPrintActions(action, stringBuilder, 0, filter)
  return stringBuilder.toString()
}

/**
 * Run [prettyPrintActions] recursively.
 */
private fun prettyPrintActions(action: AnAction, sb: StringBuilder, depth: Int, filter: (action: AnAction) -> Boolean) {
  val text: String?
  if (action is Separator) {
    text = "------------------------------------------------------"
  }
  else {
    text = action.templatePresentation.text
  }
  if (text != null) {
    for (i in 0 until depth) {
      sb.append("    ")
    }
    sb.append(text).append("\n")
  }
  val group = if (action is DefaultActionGroup) action else null
  if (group != null) {
    // for skipping the Separator of AVD section.
    var skipNext = false

    for (child in group.childActionsOrStubs) {
      if (!filter(child)) {
        // Skip AVD items in tests - these tend to vary from build environment to build environment
        skipNext = true
        continue
      }
      if (skipNext) {
        skipNext = false
        continue
      }
      assert(!skipNext)
      prettyPrintActions(child, sb, depth + 1, filter)
    }
  }
}

/**
 * Create an [AnActionEvent] for testing purpose.
 */
@JvmOverloads
fun createTestActionEvent(action: AnAction, inputEvent: InputEvent? = null, dataContext: DataContext): AnActionEvent {
  return AnActionEvent(inputEvent, dataContext, "", action.templatePresentation, ActionManager.getInstance(), 0)
}
