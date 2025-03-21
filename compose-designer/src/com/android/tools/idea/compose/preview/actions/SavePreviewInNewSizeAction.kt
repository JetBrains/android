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

import com.android.resources.ScreenOrientation
import com.android.tools.configurations.Configuration
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.compose.preview.analytics.ComposeResizeToolingUsageTracker
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.preview.config.PARAMETER_DEVICE
import com.android.tools.preview.config.PARAMETER_HEIGHT_DP
import com.android.tools.preview.config.PARAMETER_WIDTH_DP
import com.google.wireless.android.sdk.stats.ResizeComposePreviewEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAwareAction
import java.util.Locale
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

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

    val previewElementDefinition =
      previewElement.previewElementDefinition?.element as? KtAnnotationEntry ?: return
    val previewMethod = previewElement.previewBody?.element?.parent as? KtFunction ?: return

    val (widthDp, heightDp) = getDimensions(configuration)
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
        val newAnnotation =
          createPreviewAnnotation(
            configuration,
            showDecorations,
            ktPsiFactory,
            previewElementDefinition,
          )
        ShortenReferencesFacility.getInstance()
          .shorten(previewMethod.addAnnotationEntry(newAnnotation))
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
      val (widthDp, heightDp) = getDimensions(configuration)
      e.presentation.text = message("action.save.preview.current.size.title", widthDp, heightDp)
      e.presentation.description =
        message("action.save.preview.current.size.description", widthDp, heightDp)
      e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
    }
  }

  /**
   * Creates a new `@Preview` annotation entry, copying existing parameters from the original
   * annotation and adding/updating the size parameters based on the current configuration and
   * resizing mode.
   */
  private fun createPreviewAnnotation(
    configuration: Configuration,
    showDecorations: Boolean,
    ktPsiFactory: KtPsiFactory,
    originalAnnotation: KtAnnotationEntry,
  ): KtAnnotationEntry {
    val newAnnotation = ktPsiFactory.createAnnotationEntry(originalAnnotation.text)
    val argumentList = newAnnotation.getOrCreateValueArgumentList(ktPsiFactory)

    if (showDecorations) {
      // Device Resizing: In this case, the resizing operation changes the dimensions of the
      // simulated device.
      newAnnotation.removeArgument(PARAMETER_DEVICE)
      argumentList.addArgument(
        ktPsiFactory.createArgument("$PARAMETER_DEVICE = \"${createDeviceSpec(configuration)}\"")
      )
    } else {
      //  The user is effectively resizing the Composable's content area.  This is achieved by
      // directly modifying the width and height properties
      var (widthDp, heightDp) = getDimensions(configuration)
      if (
        widthDp < heightDp && configuration.deviceState!!.orientation == ScreenOrientation.LANDSCAPE
      ) {
        var temp = widthDp
        widthDp = heightDp
        heightDp = temp
      }

      newAnnotation.removeArgument(PARAMETER_WIDTH_DP)
      argumentList.addArgument(ktPsiFactory.createArgument("$PARAMETER_WIDTH_DP = $widthDp"))
      newAnnotation.removeArgument(PARAMETER_HEIGHT_DP)
      argumentList.addArgument(ktPsiFactory.createArgument("$PARAMETER_HEIGHT_DP = $heightDp"))
    }

    return newAnnotation
  }

  private fun KtAnnotationEntry.removeArgument(name: String) {
    (valueArguments.find { it.getArgumentName()?.asName?.identifier == name } as? KtValueArgument)
      ?.let { valueArgumentList?.removeArgument(it) }
  }

  private fun createDeviceSpec(configuration: Configuration): String {
    val deviceState = configuration.deviceState!!
    val orientation = deviceState.orientation.name.lowercase(Locale.getDefault())
    val screen = deviceState.hardware.screen
    val dpi = screen.pixelDensity.dpiValue

    val (widthDp, heightDp) = getDimensions(configuration)

    return "spec:width=${widthDp}dp,height=${heightDp}dp,dpi=$dpi,orientation=$orientation"
  }

  /** Calculates the width and height in DP based on the current device state. */
  private fun getDimensions(configuration: Configuration): Pair<Int, Int> {
    val deviceState = configuration.deviceState!!
    val screen = deviceState.hardware.screen
    val density = screen.pixelDensity.dpiValue

    val widthPx = screen.xDimension
    val heightPx = screen.yDimension

    val widthDp = (widthPx * (Coordinates.DEFAULT_DENSITY / density)).toInt()
    val heightDp = (heightPx * (Coordinates.DEFAULT_DENSITY / density)).toInt()

    return Pair(widthDp, heightDp)
  }

  private fun KtAnnotationEntry.getOrCreateValueArgumentList(psiFactory: KtPsiFactory) =
    valueArgumentList ?: (add(psiFactory.createCallArguments("()")) as KtValueArgumentList)
}
