/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.compose.preview.findComposePreviewManagersForContext
import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.android.tools.idea.compose.preview.fast.FastPreviewSurface
import com.android.tools.idea.editors.fast.ManualDisabledReason
import com.android.tools.idea.editors.fast.fastPreviewManager
import com.android.tools.idea.editors.powersave.PreviewPowerSaveManager
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.EditorNotifications

/**
 * Action that toggles the Fast Preview state.
 */
class ToggleFastPreviewAction: AnAction(null, null, null) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val fastPreviewManager = project.fastPreviewManager
    val newState = !fastPreviewManager.isEnabled
    if (newState) {
      fastPreviewManager.enable()

      // Automatically refresh when re-enabling
      findComposePreviewManagersForContext(e.dataContext)
        .filterIsInstance<FastPreviewSurface>()
        .forEach { it.requestFastPreviewRefreshAsync() }
    } else fastPreviewManager.disable(ManualDisabledReason)
    // We have change the state of Fast Preview ,update notifications
    EditorNotifications.getInstance(project).updateAllNotifications()
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    if (!StudioFlags.COMPOSE_FAST_PREVIEW.get()) {
      // No Fast Preview available
      e.presentation.isEnabledAndVisible = false
      return
    }

    val presentation = e.presentation
    val project = e.project
    if (project == null) {
      presentation.isEnabledAndVisible = false
      return
    }
    if (PreviewPowerSaveManager.isInPowerSaveMode) {
      presentation.description = message("action.preview.fast.refresh.disabled.in.power.save.description")
      presentation.isEnabled = false
    }
    else {
      presentation.description = message("action.preview.fast.refresh.toggle.description")
      presentation.isEnabled = true
    }

    presentation.text = if (project.fastPreviewManager.isEnabled)
      message("action.preview.fast.refresh.disable.title")
    else
      message("action.preview.fast.refresh.enable.title")
  }
}