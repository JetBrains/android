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
import com.android.tools.idea.configurations.ConfigurationMatcher
import com.android.tools.idea.rendering.Locale
import com.android.tools.idea.uibuilder.model.NlComponentHelper
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.psi.PsiFile
import org.jetbrains.android.facet.AndroidFacet
import java.util.function.Consumer

data class CustomConfiguration(val name: String, val config: Configuration)

/**
 * This class provides the [NlModel]s with custom [Configuration] for [VisualizationForm].<br>
 * The custom [Configuration] is added by [AddCustomConfigurationAction].
 */
class CustomModelsProvider(private val configurationSetListener: ConfigurationSetListener): VisualizationModelsProvider {

  private val _customConfigurations = mutableListOf<CustomConfiguration>()
  val customConfigurations: List<CustomConfiguration> = _customConfigurations

  fun addConfiguration(config: CustomConfiguration) {
    _customConfigurations.add(config)
    configurationSetListener.onCurrentConfigurationSetUpdated()
  }

  fun removeConfiguration(config: CustomConfiguration) {
    _customConfigurations.remove(config)
    configurationSetListener.onCurrentConfigurationSetUpdated()
  }

  override fun createActions(file: PsiFile, facet: AndroidFacet): ActionGroup {
    val addAction = AddCustomConfigurationAction(file, facet, this)
    return DefaultActionGroup(addAction)
  }

  override fun createNlModels(parentDisposable: Disposable, file: PsiFile, facet: AndroidFacet): List<NlModel> {
    if (file.typeOf() != LayoutFileType) {
      return emptyList()
    }

    val currentFile = file.virtualFile ?: return emptyList()
    val configurationManager = ConfigurationManager.getOrCreateInstance(facet)
    val currentFileConfig = configurationManager.getConfiguration(currentFile)

    val models = mutableListOf<NlModel>()

    // Default layout file. (Based on current configuration in Layout Editor)
    models.add(NlModel.create(parentDisposable,
                              "Default (Current File)",
                              facet,
                              currentFile,
                              currentFileConfig,
                              Consumer<NlComponent> { NlComponentHelper.registerComponent(it) }))

    // Custom Configurations
    for (customConfig in customConfigurations) {
      val config = customConfig.config
      val betterFile = ConfigurationMatcher.getBetterMatch(currentFileConfig,
                                                           config.device,
                                                           config.deviceState?.name,
                                                           config.locale,
                                                           config.target) ?: currentFile
      models.add(NlModel.create(parentDisposable,
                                customConfig.name,
                                facet,
                                betterFile,
                                config,
                                Consumer<NlComponent> { NlComponentHelper.registerComponent(it) }))
    }
    return models
  }
}
