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
package com.android.tools.compose.intentions

import com.android.tools.compose.COMPOSABLE_ANNOTATION_FQ_NAME
import com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN
import com.android.tools.compose.ComposeBundle
import com.android.tools.compose.isComposableAnnotation
import com.android.tools.idea.kotlin.fqNameMatches
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModNothing
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace

/**
 * Adds a @Preview annotation when a full @Composable is selected or cursor at @Composable
 * annotation.
 */
class ComposeCreatePreviewActionK2 : ModCommandAction {
  override fun getFamilyName() = ComposeBundle.message("create.preview")

  override fun getPresentation(context: ActionContext): Presentation? {
    getComposableAnnotationOnContext(context) ?: return null
    return Presentation.of(getFamilyName())
  }

  override fun generatePreview(context: ActionContext): IntentionPreviewInfo {
    val element = getComposableAnnotationOnContext(context) ?: return IntentionPreviewInfo.EMPTY
    val command = perform(context, element)
    return IntentionPreviewUtils.getModCommandPreview(command, context)
  }

  override fun perform(context: ActionContext): ModCommand {
    val element = getComposableAnnotationOnContext(context) ?: return ModNothing.NOTHING
    return perform(context, element)
  }

  /**
   * A function to find the `@Composable` annotation on the function of the area where the cursor is
   * located or the area of selection. Note that this function is similar to
   * `PsiBasedModCommandAction.getElement()`. The only difference is that this function finds the
   * `@Composable` annotation out of the cursor location, while
   * `PsiBasedModCommandAction.getElement()` searches only the cursor location.
   */
  private fun getComposableAnnotationOnContext(context: ActionContext): KtAnnotationEntry? {
    val offset = context.offset()
    val file = context.file()
    if (!BaseIntentionAction.canModify(file)) return null

    context.element()?.let { if (it.isValid) return it.findSelfOrParentComposableAnnotation() }

    // The right-most PSI of the selected area or of the cursor.
    var rightMostPsiOnContext = file.findElementAt(offset)
    // The left-most PSI of the selected area or of the cursor.
    var leftMostPsiOnContext =
      if (offset > 0) file.findElementAt(offset - 1) else rightMostPsiOnContext

    if (leftMostPsiOnContext == null && rightMostPsiOnContext == null) return null
    if (leftMostPsiOnContext == null) leftMostPsiOnContext = rightMostPsiOnContext
    if (rightMostPsiOnContext == null) rightMostPsiOnContext = leftMostPsiOnContext

    // The common parent of `leftMostPsiOnContext` and `rightMostPsiOnContext`.
    var commonParent = PsiTreeUtil.findCommonParent(leftMostPsiOnContext, rightMostPsiOnContext)

    if (leftMostPsiOnContext != rightMostPsiOnContext) {
      while (rightMostPsiOnContext != null && rightMostPsiOnContext != commonParent) {
        val result = rightMostPsiOnContext.findSelfOrParentComposableAnnotation()
        if (result != null) return result
        rightMostPsiOnContext = rightMostPsiOnContext.parent
      }

      while (leftMostPsiOnContext != null && leftMostPsiOnContext != commonParent) {
        val result = leftMostPsiOnContext.findSelfOrParentComposableAnnotation()
        if (result != null) return result
        leftMostPsiOnContext = leftMostPsiOnContext.parent
      }

      return null
    }

    while (true) {
      if (commonParent == null) return null
      val satisfied = commonParent.findSelfOrParentComposableAnnotation()
      if (satisfied != null) return satisfied
      if (commonParent is PsiFile) return null
      commonParent = commonParent.parent
    }
  }

  private fun PsiElement.findSelfOrParentComposableAnnotation(): KtAnnotationEntry? {
    parentOfType<KtAnnotationEntry>(withSelf = true)?.let { ktAnnotationEntry ->
      if (analyze(ktAnnotationEntry) { ktAnnotationEntry.isComposableAnnotation() })
        return ktAnnotationEntry
    }

    val editor = findExistingEditor() ?: return null
    if (editor.selectionModel.hasSelection()) {
      // Case when user selected few extra blank lines before @Composable annotation.
      val elementAtCaretAfterSpace =
        containingFile
          .findElementAt(editor.selectionModel.selectionStart)
          ?.getNextSiblingIgnoringWhitespace()
      return elementAtCaretAfterSpace
        ?.parentOfType<KtFunction>(withSelf = true)
        ?.annotationEntries
        ?.find { analyze(it) { it.fqNameMatches(COMPOSABLE_ANNOTATION_FQ_NAME) } }
    }
    return null
  }

  private fun perform(context: ActionContext, element: KtAnnotationEntry): ModCommand {
    return ModCommand.psiUpdate(element) { _, updater: ModPsiUpdater ->
      val targetElement = updater.getWritable(element) ?: return@psiUpdate
      invoke(context, targetElement)
    }
  }

  private fun invoke(context: ActionContext, element: KtAnnotationEntry) {
    val project = context.project

    val composableFunction = element.parentOfType<KtFunction>() ?: return
    val previewAnnotationEntry =
      KtPsiFactory(project).createAnnotationEntry("@${COMPOSE_PREVIEW_ANNOTATION_FQN}")

    ShortenReferencesFacility.getInstance()
      .shorten(composableFunction.addAnnotationEntry(previewAnnotationEntry))
  }
}
