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
import com.android.tools.idea.uibuilder.type.AdaptiveIconFileType
import com.android.tools.idea.uibuilder.type.AnimatedStateListFileType
import com.android.tools.idea.uibuilder.type.AnimatedVectorFileType
import com.android.tools.idea.uibuilder.type.AnimatedStateListTempFile
import com.android.tools.idea.uibuilder.type.FontFileType
import com.android.tools.idea.uibuilder.type.StateListFileType
import com.android.tools.idea.uibuilder.type.ZoomableDrawableFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Register and accepts types supported by [DesignFilesPreviewEditor].
 */
class DesignFilesPreviewEditorProvider : DesignerEditorProvider(listOf(AdaptiveIconFileType,
                                                                       StateListFileType,
                                                                       AnimatedStateListFileType,
                                                                       AnimatedStateListTempFile,
                                                                       AnimatedVectorFileType,
                                                                       FontFileType,
                                                                       ZoomableDrawableFileType))
{

  override fun createDesignEditor(project: Project, file: VirtualFile) = DesignFilesPreviewEditor(file, project)

  override fun getEditorTypeId() = DESIGN_FILES_PREVIEW_EDITOR_ID
}
