/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.npw.actions

import com.android.tools.idea.npw.assetstudio.wizard.GenerateIconsModel
import com.android.tools.idea.npw.assetstudio.wizard.NewVectorAssetStep
import com.android.tools.idea.npw.toUrl
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.android.tools.idea.wizard.model.ModelWizard
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.io.File
import java.net.URL
import org.jetbrains.android.facet.AndroidFacet

private const val VECTOR_DRAWABLE_API_LEVEL = 21

/**
 * Action to invoke the Vector Asset Studio. This will allow the user to generate icons using SVGs.
 */
class NewVectorAssetAction :
  AndroidAssetStudioAction("Vector Asset", "Open Vector Asset Studio to create an image asset") {
  override fun createWizard(
    facet: AndroidFacet,
    template: NamedModuleTemplate,
    resFolder: File,
  ): ModelWizard? {
    val wizardBuilder = ModelWizard.Builder()
    wizardBuilder.addStep(
      NewVectorAssetStep(GenerateIconsModel(facet, "vectorWizard", template, resFolder), facet)
    )
    return wizardBuilder.build()
  }

  override fun getWizardMinimumSize(): Dimension {
    return JBUI.size(700, 540)
  }

  override fun getWizardPreferredSize(): Dimension {
    return wizardMinimumSize
  }

  override fun getHelpUrl(): URL {
    return toUrl("http://developer.android.com/tools/help/vector-asset-studio.html")
  }
}
