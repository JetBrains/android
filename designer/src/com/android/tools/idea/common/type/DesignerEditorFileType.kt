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
package com.android.tools.idea.common.type

import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.psi.PsiFile

/**
 * Type of file that can be viewed using a designer editor. If this type of file can also be edited
 * using the editor, implementers should override [isEditable] to make it return true.
 */
interface DesignerEditorFileType {

  /** Whether a given file can be classified as this type. */
  fun isResourceTypeOf(file: PsiFile): Boolean

  /**
   * Returns the toolbar actions that should be present when viewing/editing this type of file.
   *
   * TODO(b/120429365): this function should be moved to another abstraction layer.
   */
  fun getToolbarActionGroups(surface: DesignSurface<*>): ToolbarActionGroups

  /** Whether this type of file can be edited using a designer editor. */
  fun isEditable() = false

  /** Returns the toolbar actions that should be present for the given selection. */
  fun getSelectionContextToolbar(
    surface: DesignSurface<*>,
    selection: List<NlComponent>,
  ): DefaultActionGroup = surface.actionManager.getToolbarActions(selection)
}

/**
 * Default [DesignerEditorFileType] that does not match any resource type and returns the default
 * toolbar action groups.
 */
object DefaultDesignerFileType : DesignerEditorFileType {
  override fun isResourceTypeOf(file: PsiFile) = false

  override fun getToolbarActionGroups(surface: DesignSurface<*>) = ToolbarActionGroups(surface)
}

fun PsiFile.typeOf(): DesignerEditorFileType =
  DesignerTypeRegistrar.registeredTypes.find { it.isResourceTypeOf(this) }
    ?: DefaultDesignerFileType
