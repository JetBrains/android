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
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace

/**
 * Adds a @Preview annotation when a full @Composable is selected or cursor at @Composable
 * annotation.
 */
class ComposeCreatePreviewActionK2 : PsiUpdateModCommandAction<PsiElement>(PsiElement::class.java) {
  private var targetAnnotationEntry: SmartPsiElementPointer<KtAnnotationEntry>? = null

  override fun getFamilyName() = ComposeBundle.message("create.preview")

  override fun invoke(context: ActionContext, element: PsiElement, updater: ModPsiUpdater) {
    val targetElement = targetAnnotationEntry?.element?.let { updater.getWritable(it) } ?: return
    val project = context.project

    val composableFunction = targetElement.parentOfType<KtFunction>() ?: return
    val previewAnnotationEntry =
      KtPsiFactory(project).createAnnotationEntry("@${COMPOSE_PREVIEW_ANNOTATION_FQN}")

    ShortenReferencesFacility.getInstance()
      .shorten(composableFunction.addAnnotationEntry(previewAnnotationEntry))
  }

  override fun isElementApplicable(element: PsiElement, context: ActionContext): Boolean {
    targetAnnotationEntry = prepareContext(element) ?: return false
    return true
  }

  private fun prepareContext(element: PsiElement): SmartPsiElementPointer<KtAnnotationEntry>? {
    element.parentOfType<KtAnnotationEntry>(withSelf = true)?.let { ktAnnotationEntry ->
      if (analyze(ktAnnotationEntry) { ktAnnotationEntry.isComposableAnnotation() })
        return ktAnnotationEntry.createSmartPointer()
    }

    val editor = element.findExistingEditor() ?: return null
    if (editor.selectionModel.hasSelection()) {
      // Case when user selected few extra blank lines before @Composable annotation.
      val elementAtCaretAfterSpace =
        element.containingFile
          .findElementAt(editor.selectionModel.selectionStart)
          ?.getNextSiblingIgnoringWhitespace()
      return elementAtCaretAfterSpace
        ?.parentOfType<KtFunction>(withSelf = true)
        ?.annotationEntries
        ?.find { analyze(it) { it.fqNameMatches(COMPOSABLE_ANNOTATION_FQ_NAME) } }
        ?.createSmartPointer()
    }
    return null
  }
}
