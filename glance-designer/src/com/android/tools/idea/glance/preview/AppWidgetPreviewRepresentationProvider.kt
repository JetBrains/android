/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.glance.preview

import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.editors.sourcecode.isKotlinFileType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.FilePreviewElementFinder
import com.android.tools.idea.preview.FilePreviewElementProvider
import com.android.tools.idea.preview.actions.CommonPreviewToolbar
import com.android.tools.idea.preview.representation.CommonRepresentationEditorFileType
import com.android.tools.idea.preview.representation.InMemoryLayoutVirtualFile
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationProvider
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

internal class GlanceAppWidgetAdapterLightVirtualFile(
  name: String,
  content: String,
  originFile: VirtualFile,
) : InMemoryLayoutVirtualFile(name, content, originFile)

/** Provider of the [PreviewRepresentation] for Glance App Widget code primitives. */
class AppWidgetPreviewRepresentationProvider(
  private val filePreviewElementFinder: FilePreviewElementFinder<PsiGlancePreviewElement> =
    AppWidgetPreviewElementFinder
) : PreviewRepresentationProvider {

  private object GlanceAppWidgetEditorFileType :
    CommonRepresentationEditorFileType(
      GlanceAppWidgetAdapterLightVirtualFile::class.java,
      LayoutEditorState.Type.GLANCE_APP_WIDGET,
      ::CommonPreviewToolbar,
    )

  init {
    DesignerTypeRegistrar.register(GlanceAppWidgetEditorFileType)
  }

  /**
   * Checks if the input [psiFile] contains glance app widget services and therefore can be provided
   * with the [PreviewRepresentation] of them.
   */
  override suspend fun accept(project: Project, psiFile: PsiFile): Boolean {
    if (DumbService.isDumb(project)) return false
    val virtualFile = psiFile.virtualFile
    if (!virtualFile.isKotlinFileType()) return false

    return StudioFlags.GLANCE_APP_WIDGET_PREVIEW.get() &&
      filePreviewElementFinder.hasPreviewElements(project, virtualFile)
  }

  /** Creates a [AppWidgetPreviewRepresentation] for the input [psiFile]. */
  override suspend fun createRepresentation(psiFile: PsiFile): PreviewRepresentation {
    return GlancePreviewRepresentation(
      APP_WIDGET_VIEW_ADAPTER,
      psiFile,
      { psiFilePointer -> FilePreviewElementProvider(psiFilePointer, filePreviewElementFinder) },
      AppWidgetModelAdapter,
    )
  }

  override val displayName = GlancePreviewBundle.message("glance.preview.appwidget.title")
}
