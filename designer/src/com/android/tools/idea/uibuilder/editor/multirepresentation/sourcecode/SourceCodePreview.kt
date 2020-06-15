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

import com.android.tools.idea.common.util.setupChangeListener
import com.android.tools.idea.uibuilder.editor.multirepresentation.MultiRepresentationPreview
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiFile

private const val EP_NAME = "com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode.sourceCodePreviewRepresentationProvider"
/**
 * A [MultiRepresentationPreview] tailored to the source code files.
 */
internal class SourceCodePreview(psiFile: PsiFile) : MultiRepresentationPreview(psiFile, EP_NAME) {
  val project = psiFile.project

  init {
    /*
     * We are not updating representations in the constructor since we might still be in the Dumb mode. That means that some representations
     * might be accepted incorrectly and they might disappear in the Smart mode. On the other hand, some might appear in the Smart mode as
     * well. Thus, validating currentRepresentationName is also not possible and therefore we are simply waiting until the Smart mode to
     * display the correct state.
     *
     * Thus, we update representations list either when we are enter the Smart mode or when we have a file change and we are in Smart mode.
     */
    DumbService.getInstance(project).smartInvokeLater { updateRepresentations() }

    setupChangeListener(project, psiFile, {
      if (!DumbService.isDumb(project)) {
        updateRepresentations()
      }
    }, this)
  }
}