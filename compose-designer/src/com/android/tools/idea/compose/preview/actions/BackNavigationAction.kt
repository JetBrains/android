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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.compose.preview.InteractivePreviewNavigationController
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.actions.findPreviewManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import icons.StudioIcons

/** Action to simulate a back press while in interactive preview. */
class BackNavigationAction : DumbAwareAction(message("action.navigate.back"), null, StudioIcons.Emulator.Toolbar.BACK) {

  override fun actionPerformed(e: AnActionEvent) {
    val interactivePreviewNavigationController = e.dataContext.getData(InteractivePreviewNavigationController.KEY) ?: return
    interactivePreviewNavigationController.backPressCompleted()
  }

  override fun update(e: AnActionEvent) {
    val isInteractive = e.getData(PreviewModeManager.KEY)?.mode?.value is PreviewMode.Interactive
    if (!isInteractive) {
      return
    }
    val interactivePreviewNavigationController = e.dataContext.getData(InteractivePreviewNavigationController.KEY) ?: return
    e.presentation.isVisible =
      interactivePreviewNavigationController.canPerformBackNavigation() && StudioFlags.COMPOSE_INTERACTIVE_PREVIEW_PREDICTIVE_BACK.get()

    val canBack = interactivePreviewNavigationController.canBackPress()
    e.presentation.isEnabled = canBack
    if (!canBack) {
      e.presentation.description = message("action.navigate.back.button.disabled.tooltip")
    } else {
      e.presentation.description = null
    }
  }

  /** BGT is needed when calling [findPreviewManager] because it accesses the VirtualFile */
  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
