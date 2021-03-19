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

import com.android.tools.idea.common.actions.ActionButtonWithToolTipDescription
import com.android.tools.idea.compose.preview.findComposePreviewManagersForContext
import com.android.tools.idea.compose.preview.message
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import icons.StudioIcons
import javax.swing.JComponent

internal class AnimationInteractiveSwitchAction :
  ToggleAction("", "", StudioIcons.Compose.Toolbar.ANIMATION_INSPECTOR),
  CustomComponentAction {

  override fun isSelected(e: AnActionEvent) =
    findComposePreviewManagersForContext(e.dataContext).any { it.animationInspectionPreviewElementInstance != null }

  override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
    findComposePreviewManagersForContext(e.dataContext).forEach {
      if (isSelected) {
        it.animationInspectionPreviewElementInstance = it.interactivePreviewElementInstance
      } else {
        it.interactivePreviewElementInstance = it.animationInspectionPreviewElementInstance
      }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    with(e.presentation) {
      isEnabled = findComposePreviewManagersForContext(e.dataContext).any {
        it.animationInspectionPreviewElementInstance?.hasAnimations == true || it.interactivePreviewElementInstance?.hasAnimations == true
      }
      isVisible = findComposePreviewManagersForContext(e.dataContext).any {
        it.animationInspectionPreviewElementInstance != null || it.interactivePreviewElementInstance != null
      }
      val selected = isSelected(e)
      description = if (isEnabled && !selected) message("action.animation.inspector.description") else ""
      text =
        if (isEnabled) {
          if (selected) message("action.stop.animation.inspector.title") else message("action.animation.inspector.title")
        } else message("action.animation.inspector.unavailable.title")
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return ActionButtonWithToolTipDescription(this, presentation, place)
  }
}