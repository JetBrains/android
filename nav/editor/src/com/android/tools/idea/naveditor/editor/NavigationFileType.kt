/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.resources.ResourceFolderType
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.XmlDesignerEditorFileType
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.navigation.NavigationDomFileDescription

internal object NavigationFileType : XmlDesignerEditorFileType {
  override val resourceFolderType: ResourceFolderType = ResourceFolderType.NAVIGATION

  override fun getSelectionContextToolbar(surface: DesignSurface<*>, selection: List<NlComponent>) =
    surface.actionManager.getToolbarActions(selection)

  override fun isResourceTypeOf(file: PsiFile) =
    file is XmlFile && NavigationDomFileDescription.isNavFile(file)

  override fun getToolbarActionGroups(surface: DesignSurface<*>) = NavToolbarActionGroups(surface)

  override fun isEditable() = true
}
