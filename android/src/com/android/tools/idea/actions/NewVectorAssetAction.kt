/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.actions

import com.android.tools.idea.npw.assetstudio.wizard.GenerateIconsModel
import com.android.tools.idea.npw.assetstudio.wizard.NewVectorAssetStep
import com.android.tools.idea.projectsystem.CapabilityNotSupported
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.ui.wizard.WizardUtils
import com.android.tools.idea.wizard.model.ModelWizard
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Dimension
import java.net.URL

private const val VECTOR_DRAWABLE_API_LEVEL = 21

/**
 * Action to invoke the Vector Asset Studio. This will allow the user to generate icons using SVGs.
 */
class NewVectorAssetAction : AndroidAssetStudioAction("Vector Asset", "Open Vector Asset Studio to create an image asset") {

  override fun createWizard(facet: AndroidFacet): ModelWizard? {
    val module = facet.module
    val status = module.getModuleSystem().canGeneratePngFromVectorGraphics()
    if (status is CapabilityNotSupported) {
      val androidModel = facet.androidModel
      if (androidModel != null) {
        val minSdkVersion = androidModel.minSdkVersion

        if (minSdkVersion == null || minSdkVersion.apiLevel < VECTOR_DRAWABLE_API_LEVEL) {
          Messages.showErrorDialog(module.project, status.message, status.title)
          return null
        }
      }
    }

    val wizardBuilder = ModelWizard.Builder()
    wizardBuilder.addStep(NewVectorAssetStep(GenerateIconsModel(facet, "vectorWizard"), facet))
    return wizardBuilder.build()
  }

  override fun getWizardMinimumSize(): Dimension {
    return JBUI.size(700, 500)
  }

  override fun getWizardPreferredSize(): Dimension {
    return wizardMinimumSize
  }

  override fun getHelpUrl(): URL? {
    return WizardUtils.toUrl("http://developer.android.com/tools/help/vector-asset-studio.html")
  }
}
