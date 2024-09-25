/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.naveditor.editor

import com.android.tools.idea.common.editor.DesignerEditorProvider
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.naveditor.model.isAction
import com.android.tools.idea.naveditor.model.isDestination
import com.android.tools.idea.naveditor.model.parentSequence
import com.google.common.collect.ImmutableList
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.navigation.NavigationDomFileDescription

class NavEditorProvider : DesignerEditorProvider(listOf(NavigationFileType)) {
  override fun createDesignEditor(project: Project, file: VirtualFile) = NavEditor(file, project)

  override fun getEditorTypeId() = NAV_EDITOR_ID

  override fun handleCaretChanged(sceneView: SceneView, nodes: ImmutableList<NlComponent>) {
    sceneView.selectionModel.setSelection(
      nodes.mapNotNull { node ->
        node.parentSequence().firstOrNull { it.isAction || it.isDestination }
      }
    )
  }

  private object NavigationFileType : DesignerEditorFileType {
    override fun getSelectionContextToolbar(
      surface: DesignSurface<*>,
      selection: List<NlComponent>,
    ) = surface.actionManager.getToolbarActions(selection)

    override fun isResourceTypeOf(file: PsiFile) =
      file is XmlFile && NavigationDomFileDescription.isNavFile(file)

    override fun getToolbarActionGroups(surface: DesignSurface<*>) = NavToolbarActionGroups(surface)

    override fun isEditable() = true
  }
}
