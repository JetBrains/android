/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.wear.preview

import com.android.SdkConstants
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.editors.sourcecode.isSourceFileType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.FilePreviewElementFinder
import com.android.tools.idea.preview.FilePreviewElementProvider
import com.android.tools.idea.preview.actions.CommonPreviewToolbar
import com.android.tools.idea.preview.representation.CommonRepresentationEditorFileType
import com.android.tools.idea.preview.representation.InMemoryLayoutVirtualFile
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationProvider
import com.android.tools.idea.wear.preview.WearPreviewBundle.message
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

internal class WearTileAdapterLightVirtualFile(
  name: String,
  content: String,
  originFile: VirtualFile,
) : InMemoryLayoutVirtualFile(name, content, originFile)

/** Provider of the [PreviewRepresentation] for Wear Tile code primitives. */
class WearTilePreviewRepresentationProvider(
  private val filePreviewElementFinder: FilePreviewElementFinder<PsiWearTilePreviewElement> =
    WearTilePreviewElementFinder
) : PreviewRepresentationProvider {

  private object WearTileEditorFileType :
    CommonRepresentationEditorFileType(
      WearTileAdapterLightVirtualFile::class.java,
      LayoutEditorState.Type.WEAR_TILE,
      ::CommonPreviewToolbar,
    )

  init {
    DesignerTypeRegistrar.register(WearTileEditorFileType)
  }

  /**
   * Checks if the input [psiFile] contains wear tile previews and therefore can be provided with
   * the [PreviewRepresentation] of them.
   */
  override suspend fun accept(project: Project, psiFile: PsiFile): Boolean {
    val virtualFile = psiFile.virtualFile
    if (!virtualFile.isSourceFileType()) return false
    if (DumbService.isDumb(project)) return false

    return StudioFlags.WEAR_TILE_PREVIEW.get() &&
      filePreviewElementFinder.hasPreviewElements(project, virtualFile)
  }

  /** Creates a [WearTilePreviewRepresentation] for the input [psiFile]. */
  override suspend fun createRepresentation(psiFile: PsiFile): PreviewRepresentation {
    return WearTilePreviewRepresentation(
      SdkConstants.CLASS_TILE_SERVICE_VIEW_ADAPTER,
      psiFile,
      { psiFilePointer -> FilePreviewElementProvider(psiFilePointer, filePreviewElementFinder) },
      WearTilePreviewElementModelAdapter(),
    )
  }

  override val displayName = message("wear.tile.preview.title")
}
