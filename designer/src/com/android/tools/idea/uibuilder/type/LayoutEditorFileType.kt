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
package com.android.tools.idea.uibuilder.type

import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.uibuilder.editor.DefaultNlToolbarActionGroups
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.wireless.android.sdk.stats.LayoutEditorState

/**
 * Type of file that can be viewed and edited using the Layout Editor.
 *
 * @param paletteMetadataId Optional id to be used for the palette metadata file. For example, if "preferences" is used, the palette
 * filename used for loading the metadata will be "preferences_palette.xml"
 */
abstract class LayoutEditorFileType(private val paletteMetadataId: String? = null) : DesignerEditorFileType {
  val paletteId = paletteMetadataId

  abstract fun getLayoutEditorStateType(): LayoutEditorState.Type

  override fun getToolbarActionGroups(surface: DesignSurface): ToolbarActionGroups =
    DefaultNlToolbarActionGroups(surface as NlDesignSurface)

  override fun isEditable() = true
}
