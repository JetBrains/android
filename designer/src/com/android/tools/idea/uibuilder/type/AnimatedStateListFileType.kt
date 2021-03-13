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

import com.android.SdkConstants
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.statelist.StateListActionGroups
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile

object AnimatedStateListFileType : DrawableFileType(setOf(SdkConstants.TAG_ANIMATED_SELECTOR)) {
  override fun getToolbarActionGroups(surface: DesignSurface) = StateListActionGroups(surface)
}

const val TEMP_ANIMATED_SELECTOR_FOLDER = "drawable-temp"

/**
 * A temp Animated Vector drawable file which is created for previewing transitions in animated selector file.
 */
object AnimatedStateListTempFile : DrawableFileType(setOf()) {
  override fun isResourceTypeOf(file: PsiFile): Boolean {
    return file is XmlFile &&
           ApplicationManager.getApplication().runReadAction(Computable { file.parent?.name == TEMP_ANIMATED_SELECTOR_FOLDER })
  }

  override fun getToolbarActionGroups(surface: DesignSurface): ToolbarActionGroups {
    return StateListActionGroups(surface)
  }
}
