/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.intellij.execution.actions.ClearConsoleAction
import com.intellij.execution.actions.ConsoleActionsPostProcessor
import com.intellij.execution.ui.ConsoleView
import com.intellij.ide.actions.CutAction
import com.intellij.ide.actions.PasteAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.xdebugger.impl.actions.PauseAction

class AppInsightsConsoleActionsPostProcessor : ConsoleActionsPostProcessor() {

  override fun postProcessPopupActions(
    console: ConsoleView,
    actions: Array<AnAction>,
  ): Array<AnAction> {
    return actions
      .flatten()
      .filter {
        when (it) {
          // ConsoleView is view-only. Remove actions that manipulate text
          is ClearConsoleAction,
          is CutAction,
          is PasteAction,
          // ConsoleView is not a continuous output console. We don't need to pause output
          is PauseAction -> false
          else -> true
        }
      }
      .toTypedArray()
  }

  private fun Array<AnAction>.flatten(): Array<AnAction> {
    val actions = mutableListOf<AnAction>()

    forEach { action ->
      when (action) {
        is DefaultActionGroup -> {
          actions.addAll(action.getActionsFromGroup())
        }
        is AnAction -> actions.add(action)
      }
    }
    return actions.toTypedArray()
  }

  private fun DefaultActionGroup.getActionsFromGroup(): Array<AnAction> {
    return getChildren(ActionManager.getInstance()).flatten()
  }
}
