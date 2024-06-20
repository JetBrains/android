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

import com.android.tools.configurations.ConfigurationListener
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.configurations.ConfigurationForFile
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.psi.PsiFile

private const val EFFECTIVE_FLAGS =
  ConfigurationListener.CFG_ADAPTIVE_SHAPE or
    ConfigurationListener.CFG_DEVICE or
    ConfigurationListener.CFG_DEVICE_STATE or
    ConfigurationListener.CFG_UI_MODE or
    ConfigurationListener.CFG_NIGHT_MODE or
    ConfigurationListener.CFG_THEME or
    ConfigurationListener.CFG_TARGET or
    ConfigurationListener.CFG_LOCALE

object LargeFontModelsProvider : VisualizationModelsProvider {

  // scale factors here matches the framework.
  @VisibleForTesting
  val SCALE_TO_DISPLAY_NAME_PAIRS =
    mutableListOf(
      (1f to "Default (100%)"),
      (0.85f to "Small (85%)"),
      (1.15f to "Large (115%)"),
      (1.3f to "Largest (130%)"),
    )

  override fun createNlModels(
    parentDisposable: Disposable,
    file: PsiFile,
    buildTarget: BuildTargetReference,
  ): List<NlModel> {

    if (file.typeOf() != LayoutFileType) {
      return emptyList()
    }

    val virtualFile = file.virtualFile ?: return emptyList()
    val configurationManager = ConfigurationManager.getOrCreateInstance(buildTarget.module)

    val defaultConfig = configurationManager.getConfiguration(virtualFile)

    val models = mutableListOf<NlModel>()

    for ((scale, displayName) in SCALE_TO_DISPLAY_NAME_PAIRS) {
      val fontConfig = ConfigurationForFile.create(defaultConfig, virtualFile)
      fontConfig.fontScale = scale
      val fontModel =
        NlModel.Builder(parentDisposable, buildTarget, virtualFile, fontConfig)
          .withComponentRegistrar(NlComponentRegistrar)
          .build()
      fontModel.setTooltip(fontConfig.toHtmlTooltip())
      fontModel.setDisplayName(displayName)
      models.add(fontModel)

      registerModelsProviderConfigurationListener(
        fontModel,
        defaultConfig,
        fontConfig,
        EFFECTIVE_FLAGS,
      )
    }

    return models
  }
}
