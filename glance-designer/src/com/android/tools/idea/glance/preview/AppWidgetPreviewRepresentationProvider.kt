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

import com.android.tools.idea.common.actions.IssueNotificationAction
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.editors.sourcecode.isKotlinFileType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationProvider
import com.android.tools.idea.uibuilder.editor.multirepresentation.devkit.CommonRepresentationEditorFileType
import com.android.tools.idea.uibuilder.editor.multirepresentation.devkit.FakeLightVirtualFile
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

internal class GlanceAppWidgetAdapterLightVirtualFile(
  name: String,
  content: String,
  originFileProvider: () -> VirtualFile?
) : FakeLightVirtualFile(name, content, originFileProvider)

internal class GlanceAppWidgetPreviewToolbar(surface: DesignSurface<*>) :
  ToolbarActionGroups(surface) {

  override fun getNorthGroup(): ActionGroup {
    return DefaultActionGroup()
  }

  override fun getNorthEastGroup(): ActionGroup =
    DefaultActionGroup().apply { add(IssueNotificationAction.getInstance()) }
}

/** Provider of the [PreviewRepresentation] for Glance App Widget code primitives. */
class AppWidgetPreviewRepresentationProvider(
  private val filePreviewElementFinder: FilePreviewElementFinder<GlancePreviewElement> =
    AppWidgetPreviewElementFinder
) : PreviewRepresentationProvider {

  private object GlanceAppWidgetEditorFileType :
    CommonRepresentationEditorFileType(
      GlanceAppWidgetAdapterLightVirtualFile::class.java,
      LayoutEditorState.Type.UNKNOWN_TYPE,
      ::GlanceAppWidgetPreviewToolbar
    )

  init {
    DesignerTypeRegistrar.register(GlanceAppWidgetEditorFileType)
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

    return StudioFlags.GLANCE_APP_WIDGET_PREVIEW.get() &&
      filePreviewElementFinder.hasPreviewElements(project, virtualFile)
  }

  /** Creates a [AppWidgetPreviewRepresentation] for the input [psiFile]. */
  override fun createRepresentation(psiFile: PsiFile): PreviewRepresentation {
    val previewProvider =
      object : PreviewElementProvider<GlancePreviewElement> {
        override suspend fun previewElements(): Sequence<GlancePreviewElement> =
          filePreviewElementFinder
            .findPreviewElements(psiFile.project, psiFile.virtualFile)
            .asSequence()
      }

    return GlancePreviewRepresentation(psiFile, previewProvider, AppWidgetModelAdapter)
  }

  override val displayName = GlancePreviewBundle.message("glance.preview.appwidget.title")
}
