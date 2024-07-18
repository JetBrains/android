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

import com.android.tools.adtui.status.REFRESH_BUTTON
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.actions.ActionButtonWithToolTipDescription
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.editors.shortcuts.asString
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.PreviewInvalidationManager
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.rendering.tokens.requestBuildArtifactsForRendering
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.util.ui.JBUI

/**
 * [AnAction] that triggers a compilation of the current module. The build will automatically
 * trigger a refresh of the surface. The action visibility is controlled by the
 * [PreviewStatus.hasRefreshIcon]
 */
class ForceCompileAndRefreshActionForNotification private constructor() :
  AnAction(
    message("action.build.and.refresh.title"),
    message("action.build.and.refresh.description"),
    REFRESH_BUTTON,
  ),
  RightAlignedToolbarAction,
  CustomComponentAction {

  /** Actions calling [DataContext.findPreviewManager] in the update method, must run in BGT */
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  companion object {
    private const val ACTION_ID =
      "Android.Designer.CommonActions.ForceCompileAndRefreshActionForNotification"

    @JvmStatic
    fun getInstance(): ForceCompileAndRefreshActionForNotification =
      ActionManager.getInstance().getAction(ACTION_ID)
        as ForceCompileAndRefreshActionForNotification
  }

  override fun actionPerformed(e: AnActionEvent) {
    val surface = e.getData(DESIGN_SURFACE)
    if (surface == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    // The PreviewInvalidationManager will avoid refreshing its corresponding preview if it detects
    // that nothing has changed. But we want to always force a refresh when this button is
    // pressed.
    e.dataContext.findPreviewManager(PreviewInvalidationManager.KEY)?.invalidate()

    if (!requestBuildForSurface(surface)) {
      // If there are no models in the surface, we can not infer which models we should trigger
      // the build for. The fallback is to find the virtual file for the editor and trigger that.
      LangDataKeys.VIRTUAL_FILE.getData(e.dataContext)?.let {
        surface.project.requestBuildArtifactsForRendering(it)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val isRefreshing =
      e.dataContext.findPreviewManager(PREVIEW_VIEW_MODEL_STATUS)?.let {
        e.updateSession.compute(this, "Check Preview Status", ActionUpdateThread.EDT) {
          it.isRefreshing
        }
      } ?: false
    presentation.isEnabled = !isRefreshing
    templateText?.let {
      presentation.setText("$it${getBuildAndRefreshShortcut().asString()}", false)
    }

    val project = e.project ?: return
    getStatusInfo(project, e.dataContext)?.let { e.presentation.isVisible = it.hasRefreshIcon }
  }

  override fun createCustomComponent(presentation: Presentation, place: String) =
    ActionButtonWithToolTipDescription(this, presentation, place).apply {
      border = JBUI.Borders.empty(1, 2)
    }

  /**
   * Helper method for action wrappers depending on [ForceCompileAndRefreshActionForNotification] to
   * have the same style
   */
  fun createCustomComponent(action: AnAction, presentation: Presentation, place: String) =
    ActionButtonWithToolTipDescription(action, presentation, place).apply {
      border = JBUI.Borders.empty(1, 2)
    }

  private fun requestBuildForSurface(surface: DesignSurface<*>) =
    surface.models
      .map { it.virtualFile }
      .distinct()
      .also { surface.project.requestBuildArtifactsForRendering(it) }
      .isNotEmpty()
}
