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
import icons.StudioIcons

class StartDestinationToolbarAction private constructor(): AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    // FIXME: This action should be disabled when the selected component cannot be a start destination
    //        But the toolbar of navigation editor is not updated after disable it.
    //        Thus we keep it enabled in navigation editor and do nothing when selected component is not illegible.
    e.presentation.isEnabled = (e.getData(DESIGN_SURFACE) as? NavDesignSurface != null)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val surface = e.getRequiredData(DESIGN_SURFACE) as NavDesignSurface
    val component = surface.selectionModel.selection.singleOrNull() ?: return
    if (!component.id.isNullOrEmpty() &&
        component.isDestination && component != surface.currentNavigation &&
        !component.isActivity && !component.isStartDestination) {
      surface.selectionModel.selection.firstOrNull()?.let {
        WriteCommandAction.runWriteCommandAction(it.model.project) {
          it.setAsStartDestination()
        }
      }
    }
  }

  companion object {
    @JvmStatic
    val instance: StartDestinationToolbarAction
      get() = ActionManager.getInstance().getAction(DesignerActions.ACTION_ASSIGN_START_DESTINATION) as StartDestinationToolbarAction
  }
}
