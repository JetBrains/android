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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.uibuilder.model.NlComponentHelper
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.intellij.openapi.Disposable
import com.intellij.psi.PsiFile
import org.jetbrains.android.facet.AndroidFacet
import java.util.function.Consumer


object LargeFontModelsProvider : VisualizationModelsProvider {

  private const val SCALE_LARGER = 1.14f
  private const val SCALE_SMALLER = 1f / SCALE_LARGER
  private const val SCALE_LARGEST = SCALE_LARGER * SCALE_LARGER

  override fun createNlModels(parentDisposable: Disposable, file: PsiFile, facet: AndroidFacet): List<NlModel> {

    if (file.typeOf() != LayoutFileType) {
      return emptyList()
    }

    val virtualFile = file.virtualFile ?: return emptyList()
    val configurationManager = ConfigurationManager.getOrCreateInstance(facet)

    val defaultConfig = configurationManager.getConfiguration(virtualFile)

    val models = mutableListOf<NlModel>()
    models.add(NlModel.create(parentDisposable,
                              "Default",
                              facet,
                              virtualFile,
                              defaultConfig,
                              Consumer<NlComponent> { NlComponentHelper.registerComponent(it) }))

    val smallerFontConfig = Configuration.create(defaultConfig, virtualFile)
    smallerFontConfig.fontScale = SCALE_SMALLER
    val largerFontConfig = Configuration.create(defaultConfig, virtualFile)
    largerFontConfig.fontScale = SCALE_LARGER
    val largestFontConfig = Configuration.create(defaultConfig, virtualFile)
    largestFontConfig.fontScale = SCALE_LARGEST

    models.add(NlModel.create(parentDisposable,
                              "Smaller",
                              facet,
                              virtualFile,
                              smallerFontConfig,
                              Consumer<NlComponent> { NlComponentHelper.registerComponent(it) }))

    models.add(NlModel.create(parentDisposable,
                              "Larger",
                              facet,
                              virtualFile,
                              largerFontConfig,
                              Consumer<NlComponent> { NlComponentHelper.registerComponent(it) }))

    models.add(NlModel.create(parentDisposable,
                              "Largest",
                              facet,
                              virtualFile,
                              largestFontConfig,
                              Consumer<NlComponent> { NlComponentHelper.registerComponent(it) }))
    return models
  }
}