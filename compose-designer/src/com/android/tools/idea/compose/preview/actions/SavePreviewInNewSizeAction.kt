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
import com.android.tools.configurations.Configuration
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.analytics.ComposeResizeToolingUsageTracker
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.getDimensionsInDp
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.compose.preview.util.toPreviewAnnotationText
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.preview.PreviewElement
import com.google.wireless.android.sdk.stats.ResizeComposePreviewEvent
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Saves the current preview state (dimensions, decorations etc.) by creating a new `@Preview`
 * annotation with a unique name for the same composable function.
 *
 * @param dispatcher on which we are going to launch switching to the new preview.
 */
class SavePreviewInNewSizeAction(val dispatcher: CoroutineDispatcher = Dispatchers.Default) :
  DumbAwareAction("", "", AllIcons.Actions.MenuSaveall) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val sceneManager = e.getSceneManagerInFocusMode() ?: return
    val previewElement = sceneManager.model.dataProvider?.previewElement() ?: return

    val configuration = sceneManager.model.configuration
    val showDecorations = previewElement.displaySettings.showDecoration
    val ktPsiFactory = KtPsiFactory(project)

    val previewMethod = previewElement.previewBody?.element?.parent as? KtFunction ?: return
    val deviceState = configuration.deviceState ?: error("Device state should not be null")

    val (widthDp, heightDp) = getDimensionsInDp(configuration)
    val mode =
      if (showDecorations) ResizeComposePreviewEvent.ResizeMode.DEVICE_RESIZE
      else ResizeComposePreviewEvent.ResizeMode.COMPOSABLE_RESIZE
    val dpi = deviceState.hardware.screen.pixelDensity.dpiValue
    ComposeResizeToolingUsageTracker.logResizeSaved(
      DESIGN_SURFACE.getData(e.dataContext),
      mode,
      widthDp,
      heightDp,
      dpi,
    )

    val nameForNewPreview = createNewName(configuration)
    setUpSwitchingToNewPreview(e, nameForNewPreview)

    WriteCommandAction.runWriteCommandAction(
      project,
      "Save Resized Preview",
      null,
      {
        val targetFile = previewMethod.containingFile as? KtFile ?: return@runWriteCommandAction

        // Use KtFile.addImport to ensure the @Preview import is present
        targetFile.addImport(FqName(COMPOSE_PREVIEW_ANNOTATION_FQN))

        val newAnnotationText =
          toPreviewAnnotationText(previewElement, configuration, nameForNewPreview)
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
      e.dataContext.getData(RESIZE_PANEL_INSTANCE_KEY)?.hasBeenResized == true

    if (e.presentation.isEnabledAndVisible) {
      val (widthDp, heightDp) = getDimensionsInDp(configuration)
      e.presentation.text = message("action.save.preview.current.size.title", widthDp, heightDp)
      e.presentation.description =
        message("action.save.preview.current.size.description", widthDp, heightDp)
      e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
    }
  }

  /**
   * Sets up the switching to the newly created preview after the action is performed. It observes
   * the flow of all preview elements and, once the new preview with the given name is found,
   * switches the [PreviewModeManager] to [PreviewMode.Focus] on that preview.
   */
  private fun setUpSwitchingToNewPreview(e: AnActionEvent, nameForNewPreview: String) {
    val allPreviewFlow = e.dataContext.getData(PreviewFlowManager.KEY)
    val modeManager = e.dataContext.getData(PreviewModeManager.KEY)
    val composeManger = e.dataContext.getData(COMPOSE_PREVIEW_MANAGER)

    if (allPreviewFlow != null && modeManager != null && composeManger != null) {
      // Switch to the new preview
      composeManger.createCoroutineScope().launch(dispatcher) {
        // Take the first emission from the flow, it should be the list after our change, so we use
        // drop(1).
        val currentPreviews = allPreviewFlow.allPreviewElementsFlow.drop(1).first()

        val newPreview =
          currentPreviews.asCollection().find { preview ->
            preview.originalNameFromParameter() == nameForNewPreview
          }

        newPreview?.let { modeManager.setMode(PreviewMode.Focus(it)) }
      }
    }
  }

  private fun PreviewElement<*>.originalNameFromParameter() =
    displaySettings.parameterName?.substringBefore(" - ")?.trim()
}

/**
 * Returns the [LayoutlibSceneManager] associated with the current [AnActionEvent].
 *
 * If the current mode is [PreviewMode.Focus], it retrieves the scene manager from the
 * [DESIGN_SURFACE]. Returns `null` if the mode is not Focus or if the scene manager cannot be
 * found.
 */
internal fun AnActionEvent.getSceneManagerInFocusMode(): LayoutlibSceneManager? {
  val mode = getData(PreviewModeManager.KEY)?.mode?.value ?: return null
  if (mode is PreviewMode.Focus) {
    return getData(DESIGN_SURFACE)?.sceneManagers?.firstOrNull() as? LayoutlibSceneManager
  }
  return null
}

/**
 * Generates a new name for the saved preview. If the device is a standard device, use its display
 * name. Otherwise, use the dimensions in dp.
 */
private fun createNewName(configuration: Configuration): String {
  val targetDevice =
    configuration.device
      ?: error("Device should not be null, because it's required for rendering preview")

  if (targetDevice.id != Configuration.CUSTOM_DEVICE_ID) {
    return targetDevice.displayName
  }
  val (widthDp, heightDp) = getDimensionsInDp(configuration)

  return "${widthDp}x${heightDp}dp"
}
