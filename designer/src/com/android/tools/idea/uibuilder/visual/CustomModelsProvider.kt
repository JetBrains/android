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

import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.android.resources.UiMode
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
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import org.jetbrains.android.facet.AndroidFacet
import java.util.WeakHashMap
import java.util.function.Consumer

data class NamedConfiguration(val name: String, val config: Configuration)

/**
 * The name with attributes which are used to create [Configuration].
 * Note that [name] never be null. If there is no given name then the name is treated as empty string.
 *
 * The initial values of properties are given for serializing and deserializing by [VisualizationToolSettings].
 */
data class CustomConfigurationAttribute(var name: String = "",
                                        var deviceId: String? = null,
                                        var apiLevel: Int? = null,
                                        var orientation: ScreenOrientation? = null,
                                        var localeString: String? = null,
                                        var theme: String? = null,
                                        var uiMode: UiMode? = null,
                                        var nightMode: NightMode? = null)

/**
 * This class provides the [NlModel]s with custom [Configuration] for [VisualizationForm].<br>
 * The custom [Configuration] is added by [AddCustomConfigurationAction].
 */
class CustomModelsProvider(private val configurationSetListener: ConfigurationSetListener): VisualizationModelsProvider {
  private val _configurationsAttributes = VisualizationToolSettings.getInstance().globalState.customConfigurationAttributes.toMutableList()
  val configurationAttributes: List<CustomConfigurationAttribute>
    get() = _configurationsAttributes

  /**
   * Map for recording ([Configuration], [CustomConfigurationAttribute]) pairs. Which is used for removing [CustomConfigurationAttribute].
   * We use [WeakHashMap] here to avoid leaking [Configuration].
   */
  private val configurationToConfigurationAttributesMap = WeakHashMap<Configuration, CustomConfigurationAttribute>()

  fun addCustomConfigurationAttributes(config: CustomConfigurationAttribute) {
    _configurationsAttributes.add(config)
    VisualizationToolSettings.getInstance().globalState.customConfigurationAttributes = _configurationsAttributes
    configurationSetListener.onCurrentConfigurationSetUpdated()
  }

  fun removeCustomConfigurationAttributes(model: NlModel) {
    val config = configurationToConfigurationAttributesMap[model.configuration] ?: return
    _configurationsAttributes.remove(config)
    VisualizationToolSettings.getInstance().globalState.customConfigurationAttributes = _configurationsAttributes
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
    for (attributes in configurationAttributes) {
      val customConfig = attributes.toNamedConfiguration(currentFileConfig) ?: continue

      val config = customConfig.config
      val betterFile = ConfigurationMatcher.getBetterMatch(currentFileConfig,
                                                           config.device,
                                                           config.deviceState?.name,
                                                           config.locale,
                                                           config.target) ?: currentFile

      val model = NlModel.create(parentDisposable,
                                 customConfig.name,
                                 facet,
                                 betterFile,
                                 config,
                                 Consumer<NlComponent> { NlComponentHelper.registerComponent(it) })
      models.add(model)
      Disposer.register(model, config)
      configurationToConfigurationAttributesMap[config] = attributes
    }
    return models
  }
}

private fun CustomConfigurationAttribute.toNamedConfiguration(defaultConfig: Configuration): NamedConfiguration? {
  val configurationManager = defaultConfig.configurationManager
  val device = if (deviceId != null) configurationManager.getDeviceById(deviceId!!) else return null
  val target = configurationManager.targets.firstOrNull { it.version.apiLevel == apiLevel } ?: return null
  val state = device?.defaultState?.deepCopy()
  state?.let {
    // The state name is used for finding better match of orientation, and it should be the same as ScreenOrientation.shortDisplayValue.
    // When the name is null, the default device orientation will be used. Here when orientation happens to be null, we want to keep the
    // previous state name rather than using a default orientation.
    orientation?.let { state.name = it.shortDisplayValue }
    state.orientation = orientation
  }

  val newConfig = Configuration.create(defaultConfig, defaultConfig.file!!)
  newConfig.setEffectiveDevice(device, state)
  newConfig.target = target
  newConfig.locale = if (localeString != null) Locale.create(localeString!!) else configurationManager.locale
  newConfig.setTheme(theme)
  newConfig.nightMode = nightMode ?: defaultConfig.nightMode
  newConfig.uiMode = uiMode ?: defaultConfig.uiMode
  // When the custom configuration has empty name, show its tooltips instead of leave it blank.
  return NamedConfiguration(if (name != "") name else newConfig.toTooltips(), newConfig)
}
