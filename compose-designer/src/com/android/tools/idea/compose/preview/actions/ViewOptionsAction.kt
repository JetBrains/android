/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.android.tools.idea.compose.preview.findComposePreviewManagersForContext
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.RenderSettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project
import icons.StudioIcons

private class ToggleAutoBuildAction :
  ToggleAction(
    message("action.auto.build.title"),
    message("action.auto.build.description"), null) {

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isEnabledAndVisible = StudioFlags.COMPOSE_PREVIEW_AUTO_BUILD.get()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    findComposePreviewManagersForContext(e.dataContext).forEach { it.isAutoBuildEnabled = state }
  }

  override fun isSelected(e: AnActionEvent): Boolean = findComposePreviewManagersForContext(
    e.dataContext)
    .any { it.isAutoBuildEnabled }
}

private class ToggleShowDecorationAction(private val project: Project) :
  ToggleAction(
    message("action.show.decorations.title"),
    message("action.show.decorations.description"), null) {

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val settings = RenderSettings.getProjectSettings(project)

    if (settings.showDecorations != state) {
      // We also persist the settings to the RenderSettings
      settings.showDecorations = state
      findComposePreviewManagersForContext(e.dataContext).forEach { it.refresh() }
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean = RenderSettings.getProjectSettings(project).showDecorations
}

internal class ViewOptionsAction(project: Project) : DropDownAction(
  message("action.view.options.title"), null,
  StudioIcons.Common.VISIBILITY_INLINE) {
  init {
    add(ToggleShowDecorationAction(project))
    add(ToggleAutoBuildAction())
  }
}