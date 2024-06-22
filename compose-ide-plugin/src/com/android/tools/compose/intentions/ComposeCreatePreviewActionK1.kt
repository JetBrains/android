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
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace

/**
 * Adds a @Preview annotation when a full @Composable is selected or cursor at @Composable
 * annotation.
 */
class ComposeCreatePreviewActionK1 : IntentionAction {
  override fun startInWriteAction() = true

  override fun getText() = ComposeBundle.message("create.preview")

  override fun getFamilyName() = ComposeBundle.message("create.preview")

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    return when {
      file == null || editor == null -> false
      !file.isWritable || file !is KtFile -> false
      else -> getComposableAnnotationEntry(editor, file) != null
    }
  }

  private fun getComposableAnnotationEntry(editor: Editor, file: PsiFile): KtAnnotationEntry? {
    if (editor.selectionModel.hasSelection()) {
      val elementAtCaret =
        file.findElementAt(editor.selectionModel.selectionStart)?.parentOfType<KtAnnotationEntry>()
      if (elementAtCaret?.isComposableAnnotation() == true) {
        return elementAtCaret
      } else {
        // Case when user selected few extra blank lines before @Composable annotation.
        val elementAtCaretAfterSpace =
          file
            .findElementAt(editor.selectionModel.selectionStart)
            ?.getNextSiblingIgnoringWhitespace()
        return (elementAtCaretAfterSpace as? KtFunction)?.annotationEntries?.find {
          it.fqNameMatches(COMPOSABLE_ANNOTATION_FQ_NAME)
        }
      }
    } else {
      return file
        .findElementAt(editor.caretModel.offset)
        ?.parentOfType<KtAnnotationEntry>()
        ?.takeIf { it.isComposableAnnotation() }
    }
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    if (editor == null || file == null) return
    val composableAnnotationEntry = getComposableAnnotationEntry(editor, file) ?: return
    val composableFunction = composableAnnotationEntry.parentOfType<KtFunction>() ?: return
    val previewAnnotationEntry =
      KtPsiFactory(project).createAnnotationEntry("@${COMPOSE_PREVIEW_ANNOTATION_FQN}")

    ShortenReferencesFacility.getInstance()
      .shorten(composableFunction.addAnnotationEntry(previewAnnotationEntry))
  }
}
