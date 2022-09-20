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
package com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode

import com.android.tools.idea.editors.setupChangeListener
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreview
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationProvider
import com.android.tools.idea.util.runWhenSmartAndSynced
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.DUMB_MODE
import com.intellij.psi.PsiFile

/**
 * A [MultiRepresentationPreview] tailored to the source code files.
 *
 * @param psiFile the file being edited by this editor.
 * @param editor the [Editor] for the file.
 * @param providers list of [PreviewRepresentationProvider] for this file type.
 */
internal class SourceCodePreview(psiFile: PsiFile, textEditor: Editor, providers: Collection<PreviewRepresentationProvider>) :
  MultiRepresentationPreview(psiFile, textEditor, providers) {

  val project = psiFile.project

  init {
    project.messageBus.connect(this).subscribe(DUMB_MODE, object : DumbService.DumbModeListener {
      override fun exitDumbMode() {
        updateRepresentationsAsync()
      }
    })

    project.runWhenSmartAndSynced(parentDisposable = this, callback = { updateRepresentationsAsync() })

    setupChangeListener(project, psiFile, {
      updateRepresentationsAsync()
    }, this)
  }
}