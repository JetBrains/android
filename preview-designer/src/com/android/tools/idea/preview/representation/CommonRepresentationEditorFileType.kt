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
package com.android.tools.idea.preview.representation

import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.psi.PsiFile

/**
 * A common implementation of [LayoutEditorFileType] that can be used in creating
 * [com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationProvider]s. An object of this class serves as a mapping
 * between the [virtualFileClass] and different LayoutEditor features specific for the file types. The mapping mechanism is implemented via
 * [LayoutEditorFileType.isResourceTypeOf] method.
 * [layoutEditorStateType] is used for analytics (metrics).
 * [toolbarConstructor] should return a [ToolbarActionGroups] that will be displayed in the top of the preview for the [virtualFileClass].
 */
open class CommonRepresentationEditorFileType(
  private val virtualFileClass: Class<out InMemoryLayoutVirtualFile>,
  private val layoutEditorStateType: LayoutEditorState.Type,
  private val toolbarConstructor: (surface: DesignSurface<*>) -> ToolbarActionGroups
) : LayoutEditorFileType() {
  override fun getLayoutEditorStateType() = layoutEditorStateType

  override fun isResourceTypeOf(file: PsiFile) = virtualFileClass.isInstance(file.virtualFile)

  override fun getToolbarActionGroups(surface: DesignSurface<*>) = toolbarConstructor(surface)

  override fun getSelectionContextToolbar(surface: DesignSurface<*>, selection: List<NlComponent>): DefaultActionGroup =
    DefaultActionGroup()
}