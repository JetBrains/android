// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.actions

import com.android.tools.idea.npw.assetstudio.wizard.GenerateIconsModel
import com.android.tools.idea.npw.assetstudio.wizard.NewImageAssetStep
import com.android.tools.idea.ui.wizard.WizardUtils
import com.android.tools.idea.wizard.model.ModelWizard
import com.intellij.util.ui.JBUI
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Dimension
import java.net.URL

/**
 * Action to invoke the Image Asset Wizard that allows user to generate various kinds of Android icons.
 */
class NewImageAssetAction : AndroidAssetStudioAction("Image Asset", "Open Asset Studio to create an image asset") {

  override fun createWizard(facet: AndroidFacet): ModelWizard {
    val wizardBuilder = ModelWizard.Builder()
    wizardBuilder.addStep(NewImageAssetStep(GenerateIconsModel(facet, "imageWizard"), facet))
    return wizardBuilder.build()
  }

  override fun getWizardMinimumSize(): Dimension {
    return JBUI.size(800, 600)
  }

  override fun getWizardPreferredSize(): Dimension {
    return JBUI.size(1020, 680)
  }

  override fun getHelpUrl(): URL? {
    return WizardUtils.toUrl("https://developer.android.com/r/studio-ui/image-asset-studio.html")
  }
}
