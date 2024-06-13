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
package com.android.tools.idea.uibuilder.type

import com.android.resources.ResourceFolderType
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.uibuilder.drawable.DrawableActionGroups
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.FileDescriptionUtils

/** Type of file that can be previewed in split editor or Layout Editor preview window. */
abstract class DrawableFileType(private val allowedRootTags: Set<String>) : DesignerEditorFileType {

  override fun isResourceTypeOf(file: PsiFile) =
    file is XmlFile &&
      FileDescriptionUtils.isResourceOfTypeWithRootTag(
        file,
        ResourceFolderType.DRAWABLE,
        allowedRootTags,
      )

  override fun getToolbarActionGroups(surface: DesignSurface<*>): ToolbarActionGroups =
    DrawableActionGroups(surface)
}
