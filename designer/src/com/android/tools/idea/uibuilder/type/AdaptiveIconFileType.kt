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

import com.android.resources.Density
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.uibuilder.adaptiveicon.AdaptiveIconActionGroups
import com.android.tools.idea.uibuilder.model.CUSTOM_DENSITY_ID
import com.android.tools.idea.uibuilder.model.overrideConfigurationDensity
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.drawable.fileDescriptions.AdaptiveIconDomFileDescription

object AdaptiveIconFileType : DesignerEditorFileType {
  override fun isResourceTypeOf(file: PsiFile) = file is XmlFile && AdaptiveIconDomFileDescription.isAdaptiveIcon(file)

  override fun getToolbarActionGroups(surface: DesignSurface) = AdaptiveIconActionGroups(surface)

  override fun setTypePrerequisites(model: NlModel) {
    // Set the default density to XXXHDPI
    val device = model.configuration.device
    if (device != null && CUSTOM_DENSITY_ID != device.id) {
      model.overrideConfigurationDensity(Density.XXXHIGH)
    }
  }
}
