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

import com.android.tools.compose.COMPOSABLE_ANNOTATION_FQ_NAME
import com.android.tools.compose.isValidPreviewLocation
import com.android.tools.idea.compose.preview.isMultiPreviewAnnotation
import com.android.tools.idea.compose.preview.isPreviewAnnotation
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.fqNameMatches
import com.android.tools.idea.studiobot.icons.AndroidAIPluginIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.toUElement

/** Action to generate a Compose Preview for a target Composable function. */
class GenerateComposePreviewAction :
  GenerateComposePreviewBaseAction(message("action.generate.preview")) {

  override fun isActionFlagEnabled(): Boolean = StudioFlags.COMPOSE_PREVIEW_GENERATE_PREVIEW.get()

  override fun getTargetComposableFunctions(e: AnActionEvent): List<KtNamedFunction> {
    // Check if the function where the caret is located is a valid Composable.
    // Return it in a singleton list if so, otherwise return an empty list.
    val containingFunction = getContainingFunctionAtCaret(e)
    return if (containingFunction?.isValidComposableFunction() == true) listOf(containingFunction)
    else emptyList()
  }

  override fun actionPerformed(e: AnActionEvent) {
    generateComposePreviews(e)
  }

  private fun getContainingFunctionAtCaret(e: AnActionEvent): KtNamedFunction? {
    val caret = e.getData(CommonDataKeys.CARET) ?: return null
    val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return null
    val psiElement = runReadAction { psiFile.findElementAt(caret.offset) } ?: return null
    return psiElement.parentOfType<KtNamedFunction>()
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (
      e.place != ActionPlaces.EDITOR_POPUP &&
        !e.place.contains(ActionPlaces.EDITOR_FLOATING_TOOLBAR)
    ) {
      e.presentation.icon = AndroidAIPluginIcons.GeminiLogo
    }
    e.presentation.text =
      getContainingFunctionAtCaret(e)?.name?.let {
        message("action.generate.preview.function.name", it)
      } ?: message("action.generate.preview")
  }

  /**
   * Whether this [KtNamedFunction] is a valid Composable function, i.e. a function annotated
   * with @Composable that's not yet annotated with a @Preview or a MultiPreview annotation. It also
   * must be in a valid preview location (see [isValidPreviewLocation]).
   */
  private fun KtNamedFunction.isValidComposableFunction(): Boolean {
    if (annotationEntries.none { it.fqNameMatches(COMPOSABLE_ANNOTATION_FQ_NAME) }) return false
    if (!isValidPreviewLocation()) return false
    if (
      annotationEntries.any {
        val uAnnotation = (it.toUElement() as? UAnnotation) ?: return@any false
        return@any uAnnotation.isPreviewAnnotation() || uAnnotation.isMultiPreviewAnnotation()
      }
    )
      return false
    return true
  }
}
