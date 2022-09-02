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
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.ComposePreviewElementInstance
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.ui.AnActionButton
import icons.StudioIcons.Compose.Toolbar.ANIMATION_INSPECTOR

/**
 * Action to open the Compose Animation Preview to analyze animations of a Compose Preview in
 * details.
 *
 * @param dataContextProvider returns the [DataContext] containing the Compose Preview associated
 * information.
 */
internal class AnimationInspectorAction(private val dataContextProvider: () -> DataContext) :
  AnActionButton(
    message("action.animation.inspector.title"),
    message("action.animation.inspector.description"),
    ANIMATION_INSPECTOR
  ) {

  private fun getPreviewElement() =
    dataContextProvider().getData(COMPOSE_PREVIEW_ELEMENT_INSTANCE) as?
      ComposePreviewElementInstance

  override fun updateButton(e: AnActionEvent) {
    super.updateButton(e)
    e.presentation.apply {
      isEnabled = true
      // Only display the animation inspector icon if there are animations to be inspected.
      isVisible = getPreviewElement()?.hasAnimations == true
      description =
        if (isEnabled) message("action.animation.inspector.description")
        else message("action.animation.inspector.unavailable.title")
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    dataContextProvider().getData(COMPOSE_PREVIEW_MANAGER)?.let {
      it.animationInspectionPreviewElementInstance = getPreviewElement()
    }
  }
}
