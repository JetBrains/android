/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.idea.actions.SCENE_VIEW
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.preview.analytics.ComposeResizeToolingUsageTracker
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.preview.applyTo
import com.android.tools.preview.config.getDefaultPreviewDevice
import com.google.wireless.android.sdk.stats.ResizeComposePreviewEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/** Action to revert the size of a resized Compose Preview to its original size. */
class RevertToOriginalSizeAction :
  DumbAwareAction(message("action.revert.preview.original.size.title"), "", AllIcons.Actions.Undo) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val sceneManager = e.getData(SCENE_VIEW)?.sceneManager as? LayoutlibSceneManager ?: return
    val previewElement = e.dataContext.previewElement() ?: return

    doRevert(sceneManager, previewElement)
  }

  private fun doRevert(
    sceneManager: LayoutlibSceneManager,
    previewElement: PsiComposePreviewElementInstance,
  ) {
    val configuration = sceneManager.model.configuration

    sceneManager.sceneRenderConfiguration.clearOverrideRenderSize = true
    // This will trigger [ConfigurationResizeListener] which will rerender with original sizes
    previewElement.applyTo(configuration) { it.settings.getDefaultPreviewDevice() }

    val showDecorations = previewElement.displaySettings.showDecoration
    val mode =
      if (showDecorations) ResizeComposePreviewEvent.ResizeMode.DEVICE_RESIZE
      else ResizeComposePreviewEvent.ResizeMode.COMPOSABLE_RESIZE
    ComposeResizeToolingUsageTracker.logResizeReverted(sceneManager.designSurface, mode)
  }

  override fun update(e: AnActionEvent) {
    val sceneManager = e.getData(SCENE_VIEW)?.sceneManager as? LayoutlibSceneManager
    val isResized = sceneManager?.isResized == true
    e.presentation.isEnabledAndVisible = StudioFlags.COMPOSE_PREVIEW_RESIZING.get() && isResized
  }
}
