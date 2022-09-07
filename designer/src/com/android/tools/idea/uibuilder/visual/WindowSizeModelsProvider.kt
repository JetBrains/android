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

import com.android.sdklib.devices.Device
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.configurations.AdditionalDeviceService
import com.android.tools.idea.configurations.ConfigurationListener
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.ConfigurationMatcher
import com.android.tools.idea.uibuilder.model.NlComponentHelper
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.VisibleForTesting

private const val PORTRAIT_DEVICE_KEYWORD = "phone"

private const val EFFECTIVE_FLAGS = ConfigurationListener.CFG_ADAPTIVE_SHAPE or
  ConfigurationListener.CFG_DEVICE_STATE or
  ConfigurationListener.CFG_UI_MODE or
  ConfigurationListener.CFG_NIGHT_MODE or
  ConfigurationListener.CFG_THEME or
  ConfigurationListener.CFG_TARGET or
  ConfigurationListener.CFG_LOCALE or
  ConfigurationListener.CFG_FONT_SCALE

object WindowSizeModelsProvider : VisualizationModelsProvider {

  @VisibleForTesting
  val deviceCaches = mutableMapOf<ConfigurationManager, List<Device>>()

  override fun createNlModels(parentDisposable: Disposable, file: PsiFile, facet: AndroidFacet): List<NlModel> {
    if (file.typeOf() != LayoutFileType) {
      return emptyList()
    }

    val virtualFile = file.virtualFile ?: return emptyList()

    val configurationManager = ConfigurationManager.getOrCreateInstance(facet)
    val defaultConfig = configurationManager.getConfiguration(virtualFile)

    val models = mutableListOf<NlModel>()

    val devices = AdditionalDeviceService.getInstance()?.getWindowSizeDevices() ?: return emptyList()

    for (device in devices) {
      val config = defaultConfig.clone()
      config.setDevice(device, false)
      val betterFile = ConfigurationMatcher.getBetterMatch(config, null, null, null, null) ?: virtualFile
      val model = NlModel.builder(facet, betterFile, config)
        .withParentDisposable(parentDisposable)
        .withModelDisplayName(device.displayName)
        .withModelTooltip(config.toHtmlTooltip())
        .withComponentRegistrar(NlComponentRegistrar)
        .build()
      models.add(model)

      registerModelsProviderConfigurationListener(model, defaultConfig, config, EFFECTIVE_FLAGS)
    }
    return models
  }
}
