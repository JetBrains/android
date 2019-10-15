/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

/**
 * Sets up a change listener for the given [psiFile]. When the file changes, [refreshPreview] will be called if any [PreviewElement] has
 * been affected by the change. [sourceCodeChanged] will be called if there was a potential source code change but the [PreviewElement]s
 * remained the same.
 *
 * The [previewElementProvider] should provide the last valid list of [PreviewElement]s.
 *
 * The given [parentDisposable] will be used to set the life cycle of the listener. When disposed, the listener will be disposed too.
 */
fun setupChangeListener(
  project: Project,
  psiFile: PsiFile,
  previewElementProvider: () -> List<PreviewElement>,
  refreshPreview: () -> Unit,
  sourceCodeChanged: () -> Unit,
  parentDisposable: Disposable) {
  PsiDocumentManager.getInstance(project).getDocument(psiFile)!!.addDocumentListener(object : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      if (event.isWholeTextReplaced) {
        refreshPreview()
        return
      }

      val currentPreviewElements = previewElementProvider()
      val isPreviewElementChange = currentPreviewElements.mapNotNull {
        TextRange.create(it.previewElementDefinitionPsi?.range ?: return@mapNotNull null)
      }.any {
        it.contains(event.offset)
      }

      if (isPreviewElementChange) {
        refreshPreview()
      }
      else {
        sourceCodeChanged()
      }
    }
  }, parentDisposable)
}