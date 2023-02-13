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

import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.editors.sourcecode.isKotlinFileType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.glance.preview.actions.GlanceIssueNotificationAction
import com.android.tools.idea.preview.FilePreviewElementFinder
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.representation.CommonRepresentationEditorFileType
import com.android.tools.idea.preview.representation.InMemoryLayoutVirtualFile
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationProvider
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

internal class GlanceTileAdapterLightVirtualFile(
  name: String,
  content: String,
  originFileProvider: () -> VirtualFile?
) : InMemoryLayoutVirtualFile(name, content, originFileProvider)

internal class GlanceTilePreviewToolbar(surface: DesignSurface<*>) : ToolbarActionGroups(surface) {

  override fun getNorthGroup(): ActionGroup {
    return DefaultActionGroup()
  }

  override fun getNorthEastGroup(): ActionGroup =
    DefaultActionGroup(listOf(GlanceIssueNotificationAction()))
}

/** Provider of the [PreviewRepresentation] for Glance Tile code primitives. */
class TilePreviewRepresentationProvider(
  private val filePreviewElementFinder: FilePreviewElementFinder<GlancePreviewElement> =
    TilePreviewElementFinder
) : PreviewRepresentationProvider {
  private object GlanceTileEditorFileType :
    CommonRepresentationEditorFileType(
      GlanceTileAdapterLightVirtualFile::class.java,
      LayoutEditorState.Type.GLANCE_WEAR_TILE,
      ::GlanceTilePreviewToolbar
    )

  init {
    DesignerTypeRegistrar.register(GlanceTileEditorFileType)
  }
  /**
   * Checks if the input [psiFile] contains glance tile services and therefore can be provided with
   * the [PreviewRepresentation] of them.
   */
  override suspend fun accept(project: Project, psiFile: PsiFile): Boolean {
    val virtualFile = psiFile.virtualFile

    if (!virtualFile.isKotlinFileType()) {
      return false
    }

    return StudioFlags.GLANCE_TILE_PREVIEW.get() &&
      filePreviewElementFinder.hasPreviewElements(project, virtualFile)
  }

  /** Creates a [TilePreviewRepresentation] for the input [psiFile]. */
  override fun createRepresentation(psiFile: PsiFile): PreviewRepresentation {
    val previewProvider =
      object : PreviewElementProvider<GlancePreviewElement> {
        override suspend fun previewElements(): Sequence<GlancePreviewElement> =
          filePreviewElementFinder
            .findPreviewElements(psiFile.project, psiFile.virtualFile)
            .asSequence()
      }

    return GlancePreviewRepresentation(
      WEAR_TILE_VIEW_ADAPTER,
      psiFile,
      previewProvider,
      WearTilesModelAdapter
    )
  }

  override val displayName = GlancePreviewBundle.message("glance.preview.tile.title")
}
