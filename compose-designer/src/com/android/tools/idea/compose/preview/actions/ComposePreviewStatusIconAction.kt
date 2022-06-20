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

import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.editors.fast.fastPreviewManager
import com.android.tools.idea.uibuilder.scene.hasRenderErrors
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.AnimatedIcon
import org.jetbrains.annotations.VisibleForTesting

/**
 * [AnAction] that can be used to show an icon according to the Compose Preview status
 */
internal class ComposePreviewStatusIconAction(private val sceneView: SceneView?) : AnAction() {
  override fun update(e: AnActionEvent) {
    val composePreviewManager = e.getData(COMPOSE_PREVIEW_MANAGER) ?: return
    val project = e.project ?: return
    val previewStatus = composePreviewManager.status()
    val fastPreviewEnabled = project.fastPreviewManager.isEnabled
    val fastPreviewAutoDisabled = project.fastPreviewManager.isAutoDisabled
    e.presentation.apply {
      isEnabled = false
      disabledIcon = when {
        // loading
        previewStatus.interactiveMode.isStartingOrStopping() || previewStatus.isRefreshing ||
          project.fastPreviewManager.isCompiling -> AnimatedIcon.Default()
        // errors
        project.needsBuild || previewStatus.hasSyntaxErrors || previewStatus.hasRuntimeErrors ||
          sceneView?.hasRenderErrors() == true -> AllIcons.General.InspectionsWarning
        // ok
        else -> AllIcons.General.InspectionsOK
      }

      // don't show when fast preview is auto-disabled or when out of date.
      isVisible = fastPreviewEnabled || (!previewStatus.isOutOfDate && !fastPreviewAutoDisabled)

      // TODO(b/232716935) remove this 'if' statement once the errors
      //  panel is replaced by a cached image of the preview.
      if (disabledIcon == AllIcons.General.InspectionsWarning) {
        isVisible = false
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {}
}
