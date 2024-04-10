/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.tools.idea.preview.essentials.PreviewEssentialsModeManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.representation.PREVIEW_ELEMENT_INSTANCE
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import icons.StudioIcons.Compose.Toolbar.ANIMATION_INSPECTOR

/**
 * Action to open the Animation Preview to analyze animations in details.
 *
 * @param defaultModeDescription the description that will be used for the action
 */
class AnimationInspectorAction(
  private val defaultModeDescription: String = message("action.animation.inspector.description")
) :
  DumbAwareAction(
    message("action.animation.inspector.title"),
    defaultModeDescription,
    ANIMATION_INSPECTOR,
  ) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.apply {
      val isEssentialsModeEnabled = PreviewEssentialsModeManager.isEssentialsModeEnabled
      isEnabled = !isEssentialsModeEnabled
      // Only display the animation inspector icon if there are animations to be inspected.
      isVisible = e.dataContext.getData(PREVIEW_ELEMENT_INSTANCE)?.hasAnimations == true
      text = if (isEssentialsModeEnabled) null else message("action.animation.inspector.title")
      description =
        if (isEssentialsModeEnabled)
          message("action.animation.inspector.essentials.mode.description")
        else defaultModeDescription
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val manager = e.dataContext.getData(PreviewModeManager.KEY) ?: return
    val previewElement = e.dataContext.getData(PREVIEW_ELEMENT_INSTANCE) ?: return
    manager.setMode(PreviewMode.AnimationInspection(previewElement))
  }
}
