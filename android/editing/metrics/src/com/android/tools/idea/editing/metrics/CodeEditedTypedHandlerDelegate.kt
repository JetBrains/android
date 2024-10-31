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
package com.android.tools.idea.editing.metrics

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * [TypedHandlerDelegate] that intercepts typing actions and sets the appropriate
 * [CodeEditingAction].
 */
class CodeEditedTypedHandlerDelegate : TypedHandlerDelegate() {
  override fun beforeClosingParenInserted(
    c: Char,
    project: Project,
    editor: Editor,
    file: PsiFile,
  ): Result {
    CodeEditedMetricsService.getInstance()
      .setCodeEditingAction(CodeEditingAction.PairedEnclosureInserted(c.toString()))
    return super.beforeClosingParenInserted(c, project, editor, file)
  }

  override fun beforeClosingQuoteInserted(
    quote: CharSequence,
    project: Project,
    editor: Editor,
    file: PsiFile,
  ): Result {
    CodeEditedMetricsService.getInstance()
      .setCodeEditingAction(CodeEditingAction.PairedEnclosureInserted(quote.toString()))
    return super.beforeClosingQuoteInserted(quote, project, editor, file)
  }

  override fun beforeCharTyped(
    c: Char,
    project: Project,
    editor: Editor,
    file: PsiFile,
    fileType: FileType,
  ): Result {
    CodeEditedMetricsService.getInstance().setCodeEditingAction(CodeEditingAction.Typing)
    return super.beforeCharTyped(c, project, editor, file, fileType)
  }

  override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    CodeEditedMetricsService.getInstance().clearCodeEditingAction()
    return super.charTyped(c, project, editor, file)
  }
}
