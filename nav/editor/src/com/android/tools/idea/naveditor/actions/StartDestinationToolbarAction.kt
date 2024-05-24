/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.actions

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.actions.DesignerActions
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.model.isActivity
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.isStartDestination
import com.android.tools.idea.naveditor.model.setAsStartDestination
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction

class StartDestinationToolbarAction private constructor(): AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = false
    executeCallbackIfValidDestination(e) {
      e.presentation.isEnabled = true
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    executeCallbackIfValidDestination(e) {
      WriteCommandAction.runWriteCommandAction(it.model.project) {
        it.setAsStartDestination()
      }
    }
  }

  private fun executeCallbackIfValidDestination(e: AnActionEvent, callback: (NlComponent) -> Unit) {
    val surface = e.getData(DESIGN_SURFACE) as? NavDesignSurface ?: return
    val component = surface.selectionModel.selection.singleOrNull() ?: return
    if (!component.id.isNullOrEmpty() &&
        component.isDestination && component != surface.currentNavigation &&
        !component.isActivity && !component.isStartDestination) {
      callback(component)
    }
  }

  companion object {
    @JvmStatic
    val instance: StartDestinationToolbarAction
      get() = ActionManager.getInstance().getAction(DesignerActions.ACTION_ASSIGN_START_DESTINATION) as StartDestinationToolbarAction
  }
}
