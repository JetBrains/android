/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.compose.preview.actions

import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.compose.preview.analytics.ComposeResizeToolingUsageTracker
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.getDimensionsInDp
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.compose.preview.util.toPreviewAnnotationText
import com.android.tools.idea.flags.StudioFlags
import com.google.wireless.android.sdk.stats.ResizeComposePreviewEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

/** Action to save the current size of a resized Compose Preview. */
class SavePreviewInNewSizeAction : DumbAwareAction("", "", AllIcons.Actions.MenuSaveall) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val sceneManager = e.getSceneManagerInFocusMode() ?: return
    val previewElement = sceneManager.model.dataProvider?.previewElement() ?: return

    val configuration = sceneManager.model.configuration
    val showDecorations = previewElement.displaySettings.showDecoration
    val ktPsiFactory = KtPsiFactory(project)

    val previewMethod = previewElement.previewBody?.element?.parent as? KtFunction ?: return

    val (widthDp, heightDp) = getDimensionsInDp(configuration)
    val mode =
      if (showDecorations) ResizeComposePreviewEvent.ResizeMode.DEVICE_RESIZE
      else ResizeComposePreviewEvent.ResizeMode.COMPOSABLE_RESIZE
    val dpi = configuration.deviceState!!.hardware.screen.pixelDensity.dpiValue
    ComposeResizeToolingUsageTracker.logResizeSaved(
      DESIGN_SURFACE.getData(e.dataContext),
      mode,
      widthDp,
      heightDp,
      dpi,
    )

    WriteCommandAction.runWriteCommandAction(
      project,
      "Save Resized Preview",
      null,
      {
        val targetFile = previewMethod.containingFile as? KtFile ?: return@runWriteCommandAction

        // Use KtFile.addImport to ensure the @Preview import is present
        targetFile.addImport(FqName(COMPOSE_PREVIEW_ANNOTATION_FQN))

        val newAnnotationText = toPreviewAnnotationText(previewElement, configuration)
        val newAnnotationEntry = ktPsiFactory.createAnnotationEntry(newAnnotationText)

        ShortenReferencesFacility.getInstance()
          .shorten(previewMethod.addAnnotationEntry(newAnnotationEntry))
      },
      previewMethod.containingFile,
    )
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    val sceneManager = e.getSceneManagerInFocusMode() ?: return
    val configuration = sceneManager.model.configuration
    e.presentation.isEnabledAndVisible =
      StudioFlags.COMPOSE_PREVIEW_RESIZING.get() && sceneManager.isResized

    if (e.presentation.isEnabledAndVisible) {
      val (widthDp, heightDp) = getDimensionsInDp(configuration)
      e.presentation.text = message("action.save.preview.current.size.title", widthDp, heightDp)
      e.presentation.description =
        message("action.save.preview.current.size.description", widthDp, heightDp)
      e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
    }
  }
}
