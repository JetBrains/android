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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.configurations.ConfigurationListener
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.configurations.AdditionalDeviceService
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.ConfigurationMatcher
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.intellij.openapi.Disposable
import com.intellij.psi.PsiFile

private const val EFFECTIVE_FLAGS =
  ConfigurationListener.CFG_ADAPTIVE_SHAPE or
    ConfigurationListener.CFG_UI_MODE or
    ConfigurationListener.CFG_NIGHT_MODE or
    ConfigurationListener.CFG_THEME or
    ConfigurationListener.CFG_TARGET or
    ConfigurationListener.CFG_LOCALE or
    ConfigurationListener.CFG_FONT_SCALE

object WindowSizeModelsProvider : VisualizationModelsProvider {

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

    val devices =
      AdditionalDeviceService.getInstance()?.getWindowSizeDevices() ?: return emptyList()

    for (device in devices) {
      val config = defaultConfig.clone()
      config.setDevice(device, false)
      val betterFile =
        ConfigurationMatcher.getBetterMatch(config, null, null, null, null) ?: virtualFile
      val model =
        NlModel.Builder(parentDisposable, buildTarget, betterFile, config)
          .withComponentRegistrar(NlComponentRegistrar)
          .build()
      model.displaySettings.setTooltip(config.toHtmlTooltip())
      model.displaySettings.setDisplayName(device.displayName)
      models.add(model)

      registerModelsProviderConfigurationListener(model, defaultConfig, config, EFFECTIVE_FLAGS)
    }
    return models
  }
}
