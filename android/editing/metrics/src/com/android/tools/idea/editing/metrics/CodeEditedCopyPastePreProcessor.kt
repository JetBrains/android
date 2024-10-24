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

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * [CopyPastePreProcessor] that does nothing other than setting [CodeEditingAction.UserPaste] when
 * the user pastes.
 */
class CodeEditedCopyPastePreProcessor : CopyPastePreProcessor {
  override fun preprocessOnCopy(
    file: PsiFile,
    startOffsets: IntArray,
    endOffsets: IntArray,
    text: String,
  ): String? = null

  override fun preprocessOnPaste(
    project: Project,
    file: PsiFile,
    editor: Editor,
    text: String,
    rawText: RawText?,
  ) =
    text.also {
      CodeEditedMetricsService.getInstance().setCodeEditingAction(CodeEditingAction.UserPaste)
    }
}
