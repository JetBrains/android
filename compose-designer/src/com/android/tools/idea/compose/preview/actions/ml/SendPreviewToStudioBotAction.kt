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
package com.android.tools.idea.compose.preview.actions.ml

import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.compose.preview.actions.ml.utils.Blob
import com.android.tools.idea.compose.preview.actions.ml.utils.ContextualEditorBalloon.Companion.contextualEditorBalloon
import com.android.tools.idea.compose.preview.actions.ml.utils.transformAndShowDiff
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.containingFile
import com.android.tools.idea.preview.representation.PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.studiobot.MimeType
import com.android.tools.idea.studiobot.ModelType
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.studiobot.prompts.Prompt
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.createSmartPointer
import com.intellij.psi.util.parentOfType
import icons.StudioIcons
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.jetbrains.kotlin.psi.KtFunction

// TODO: create a second class to send only a portion of the preview as the image context, e.g.
//  highlighting a toolbar when the user right-clicks it and ask to fix something related.
class SendPreviewToStudioBotAction : AnAction(message("action.send.preview.to.gemini")) {

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = false
    val studioBot = StudioBot.getInstance()
    if (!studioBot.isAvailable()) {
      // Studio Bot is unavailable. Don't display the action.
      return
    }
    val project = e.project ?: return
    if (!studioBot.isContextAllowed(project)) {
      // The user has not chosen to share context with Studio Bot. Don't display the action.
      return
    }
    e.getData(VIRTUAL_FILE)?.let {
      if (StudioBot.getInstance().aiExcludeService(project).isFileExcluded(it)) {
        // The file is excluded, so it can't be used by AI services. Don't display the action.
        return@update
      }
    }
    e.presentation.isEnabledAndVisible = true
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val surface = e.getData(DESIGN_SURFACE) as? NlDesignSurface ?: return
    val sceneManager =
      surface.selectionModel.primary?.model?.let { surface.getSceneManager(it) }
        ?: surface.sceneViewAtMousePosition?.sceneManager as? LayoutlibSceneManager
    val previewInstance =
      sceneManager?.model?.dataContext?.getData(PREVIEW_ELEMENT_INSTANCE) ?: return
    val filePointer = previewInstance.containingFile?.createSmartPointer() ?: return
    // TODO: improve code extraction logic. The following extracts the containing preview function.
    //  That won't work, for example, if most of the preview implementation is in a different
    //  function.
    val previewCode =
      previewInstance.previewBody?.element?.parentOfType<KtFunction>()?.text ?: return
    val imageBytes = sceneManager.getImageBytes() ?: return

    showBalloon(previewCode, filePointer, imageBytes, sceneManager, e.dataContext)
  }

  private fun showBalloon(
    previewCode: String,
    filePointer: SmartPsiElementPointer<PsiFile>,
    imageBytes: ByteArray,
    diffDisposable: Disposable,
    dataContext: DataContext,
  ) {
    contextualEditorBalloon {
        register(
          name = message("circle.to.fix.balloon.title"),
          icon = StudioIcons.Compose.Toolbar.RUN_CONFIGURATION,
          placeholderText = message("circle.to.fix.balloon.placeholder"),
        ) { userQuery ->
          transformAndShowDiff(
            buildPrompt(filePointer, previewCode, Blob(imageBytes, MimeType.PNG), userQuery),
            filePointer,
            diffDisposable,
            ModelType.EXPERIMENTAL_LONG_CONTEXT,
          )
        }
      }
      .show(dataContext)
  }

  private fun LayoutlibSceneManager.getImageBytes(): ByteArray? {
    val renderedImage = renderResult?.renderedImage?.copy ?: return null
    val byteArrayOutputStream = ByteArrayOutputStream(8192)
    ImageIO.write(renderedImage, "png", byteArrayOutputStream)
    return byteArrayOutputStream.toByteArray()
  }

  /**
   * Takes the code of a Compose Preview function, its corresponding image, and a custom
   * instruction. Builds a prompt asking for the transformed function code.
   */
  private fun buildPrompt(
    filePointer: SmartPsiElementPointer<PsiFile>,
    previewFunctionCode: String,
    blob: Blob?,
    query: String,
  ): Prompt {
    val defaultPrompt =
      """
      |You are an expert Android programmer knowledgeable in Kotlin and Java.
      |You follow all the best coding practices.
      """
        .trimMargin()
    return com.android.tools.idea.studiobot.prompts.buildPrompt(filePointer.project) {
      userMessage {
        // TODO: This was supposed to be a systemMessage, but the current model (Vision) doesn't
        //  support multi-turn
        text(defaultPrompt, listOf())
        text("This is the code corresponding to the following Compose Preview:", listOf())
        filePointer.element?.let {
          code(previewFunctionCode, MimeType.KOTLIN, listOf(it.virtualFile))
        }
        blob?.let { blob(data = it.data, mimeType = it.mimeType, filesUsed = emptyList()) }
        text(
          """
          |The response must modify the code above in order to achieve the following: $query
          |The response must contain the entire function code, not only the modified parts.
          |The response must only include the modified code, not any other piece of text.
          """
            .trimMargin(),
          listOf(),
        )
      }
    }
  }
}
