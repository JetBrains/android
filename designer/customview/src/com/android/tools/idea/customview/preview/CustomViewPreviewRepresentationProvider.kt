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
package com.android.tools.idea.customview.preview

import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

/**
 * A [PreviewRepresentationProvider] coupled with [CustomViewPreviewRepresentation].
 */
internal class CustomViewPreviewRepresentationProvider : PreviewRepresentationProvider {

  /**
   * Checks if the input [virtualFile] contains custom views and therefore can be provided with the [PreviewRepresentation] of them.
   */
  override fun accept(project: Project, virtualFile: VirtualFile): Boolean {
    if (!virtualFile.hasSourceFileExtension()) {
      return false
    }

    return PsiManager.getInstance(project).findFile(virtualFile)!!.hasViewSuccessor()
  }

  /**
   * Creates a [CustomViewPreviewRepresentation] for the input [psiFile].
   */
  override fun createRepresentation(psiFile: PsiFile) : CustomViewPreviewRepresentation {
    return CustomViewPreviewRepresentation(psiFile)
  }

  override val displayName = "Custom views"

}
