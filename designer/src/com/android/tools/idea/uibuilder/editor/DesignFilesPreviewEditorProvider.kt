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
package com.android.tools.idea.uibuilder.editor

import com.android.tools.idea.common.editor.DesignerEditorProvider
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.type.AdaptiveIconFileType
import com.android.tools.idea.uibuilder.type.FontFileType
import com.android.tools.idea.uibuilder.type.StateListFileType
import com.android.tools.idea.uibuilder.type.AnimatedVectorFileType
import com.android.tools.idea.uibuilder.type.ZoomableDrawableFileType
import com.google.common.collect.ImmutableList
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Register and accepts types supported by [DesignFilesPreviewEditor] if [StudioFlags.NELE_SPLIT_EDITOR] is enabled.
 */
class DesignFilesPreviewEditorProvider : DesignerEditorProvider(
  if (StudioFlags.NELE_SPLIT_EDITOR.get()) acceptedTypes()
  // Don't register/accept these files if NELE_SPLIT_EDITOR is disabled, since this editor shouldn't be used in this case.
  else emptyList()) {

  override fun createDesignEditor(project: Project, file: VirtualFile) = DesignFilesPreviewEditor(file, project)

  override fun getEditorTypeId() = DESIGN_FILES_PREVIEW_EDITOR_ID
}

fun acceptedTypes(): ImmutableList<DesignerEditorFileType> = ImmutableList.of(AdaptiveIconFileType, StateListFileType, AnimatedVectorFileType,
                                                                              FontFileType, ZoomableDrawableFileType)
