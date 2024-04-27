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
package com.android.tools.idea.preview.actions

import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.representation.PREVIEW_ELEMENT_INSTANCE
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import icons.StudioIcons.Compose.Toolbar.INTERACTIVE_PREVIEW

/**
 * Action that controls when to enable the Interactive mode.
 *
 * @param isEssentialsModeEnabled returns true if Essentials Mode is enabled. The action is disabled
 *   when Essentials Mode is enabled.
 * @param essentialsModeDescription the description that will be used when the action is disabled
 *   due to Essentials Mode.
 */
class EnableInteractiveAction(
  private val isEssentialsModeEnabled: () -> Boolean,
  private val essentialsModeDescription: String =
    message("action.interactive.essentials.mode.description.default"),
) :
  DumbAwareAction(
    message("action.interactive.title"),
    message("action.interactive.description"),
    INTERACTIVE_PREVIEW
  ) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val isEssentialsModeEnabled = isEssentialsModeEnabled()
    e.presentation.isEnabled = !isEssentialsModeEnabled
    e.presentation.text = if (isEssentialsModeEnabled) null else message("action.interactive.title")
    e.presentation.description =
      if (isEssentialsModeEnabled) essentialsModeDescription
      else message("action.interactive.description")
  }

  override fun actionPerformed(e: AnActionEvent) {
    val modelDataContext = e.dataContext
    val manager = modelDataContext.getData(PreviewModeManager.KEY) ?: return
    val previewElement = modelDataContext.getData(PREVIEW_ELEMENT_INSTANCE) ?: return

    manager.setMode(PreviewMode.Interactive(previewElement))
  }
}
