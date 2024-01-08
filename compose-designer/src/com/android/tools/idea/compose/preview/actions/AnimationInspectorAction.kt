/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.android.tools.idea.compose.preview.PreviewMode
import com.android.tools.idea.compose.preview.essentials.ComposePreviewEssentialsModeManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAwareAction
import icons.StudioIcons.Compose.Toolbar.ANIMATION_INSPECTOR

/**
 * Action to open the Compose Animation Preview to analyze animations of a Compose Preview in
 * details.
 *
 * @param dataContextProvider returns the [DataContext] containing the Compose Preview associated
 *   information.
 */
class AnimationInspectorAction(private val dataContextProvider: () -> DataContext) :
  DumbAwareAction(
    message("action.animation.inspector.title"),
    message("action.animation.inspector.description"),
    ANIMATION_INSPECTOR
  ) {

  private fun getPreviewElement() = dataContextProvider().getData(COMPOSE_PREVIEW_ELEMENT_INSTANCE)

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.apply {
      val isEssentialsModeEnabled = ComposePreviewEssentialsModeManager.isEssentialsModeEnabled
      isEnabled = !isEssentialsModeEnabled
      // Only display the animation inspector icon if there are animations to be inspected.
      isVisible = getPreviewElement()?.hasAnimations == true
      text = if (isEssentialsModeEnabled) null else message("action.animation.inspector.title")
      description =
        if (isEssentialsModeEnabled)
          message("action.animation.inspector.essentials.mode.description")
        else message("action.animation.inspector.description")
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val manager = dataContextProvider().getData(COMPOSE_PREVIEW_MANAGER) ?: return
    val previewElement = getPreviewElement() ?: return
    manager.setMode(PreviewMode.AnimationInspection(previewElement))
  }
}
