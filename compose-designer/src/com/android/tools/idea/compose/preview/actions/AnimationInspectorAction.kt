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

import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_ELEMENT
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.animation.ComposePreviewAnimationManager
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.PreviewElementInstance
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction
import icons.StudioIcons.LayoutEditor.Palette.VIEW_ANIMATOR

/**
 * Action to open the compose animation inspector to analyze animations of a Compose Preview in detail.
 * TODO(b/157939444): update action icon.
 *
 * @param dataContextProvider returns the [DataContext] containing the Compose Preview associated information.
 */
internal class AnimationInspectorAction(private val dataContextProvider: () -> DataContext) :
  ToggleAction(message("action.animation.inspector.title"), message("action.animation.inspector.description"), VIEW_ANIMATOR) {

  private val isSelected: Boolean
    get() = getComposePreviewManager()?.animationInspectionPreviewElementInstanceId != null

  private fun getComposePreviewManager() = dataContextProvider().getData(COMPOSE_PREVIEW_MANAGER)

  override fun isSelected(e: AnActionEvent) = isSelected

  override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
    val modelDataContext = dataContextProvider()
    val manager = modelDataContext.getData(COMPOSE_PREVIEW_MANAGER) ?: return
    manager.animationInspectionPreviewElementInstanceId =
      if (isSelected) (modelDataContext.getData(COMPOSE_PREVIEW_ELEMENT) as? PreviewElementInstance)?.instanceId else null
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val isInteractive = getComposePreviewManager()?.interactivePreviewElementInstanceId != null
    // If interactive mode is enabled for this preview, the animation inspector button should be disabled. Otherwise, only enable the button
    // if the animation inspector is closed, or if it's open for this Compose Preview.
    e.presentation.isEnabled = !isInteractive && (!ComposePreviewAnimationManager.isInspectorOpen() || isSelected(e))
  }
}