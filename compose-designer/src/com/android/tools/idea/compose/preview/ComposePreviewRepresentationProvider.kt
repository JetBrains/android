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


import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

/**
 * A [PreviewRepresentationProvider] coupled with [ComposePreviewRepresentation].
 */
class ComposePreviewRepresentationProvider(
  private val filePreviewElementProvider: () -> FilePreviewElementFinder = ::defaultFilePreviewElementFinder
) : PreviewRepresentationProvider {
  private val LOG = Logger.getInstance(ComposePreviewRepresentationProvider::class.java)

  /**
   * Checks if the input [virtualFile] contains compose previews and therefore can be provided with the [PreviewRepresentation] of them.
   */
  override fun accept(project: Project, virtualFile: VirtualFile): Boolean {
    if (!StudioFlags.COMPOSE_PREVIEW.get() || !virtualFile.isKotlinFileType()) {
      return false
    }

    val hasPreviewMethods = filePreviewElementProvider().hasPreviewMethods(project, virtualFile)
    if (LOG.isDebugEnabled) {
      LOG.debug("${virtualFile.path} hasPreviewMethods=${hasPreviewMethods}")
    }

    return hasPreviewMethods
  }

  /**
   * Creates a [ComposePreviewRepresentation] for the input [psiFile].
   */
  override fun createRepresentation(psiFile: PsiFile) : ComposePreviewRepresentation {
    val previewProvider = object : PreviewElementProvider {
      override val previewElements: List<PreviewElement>
        get() = if (DumbService.isDumb(psiFile.project))
          emptyList()
        else
          filePreviewElementProvider().findPreviewMethods(psiFile.project, psiFile.virtualFile)
    }
    return ComposePreviewRepresentation(psiFile, previewProvider)
  }

  override val displayName = message("representation.name")

}