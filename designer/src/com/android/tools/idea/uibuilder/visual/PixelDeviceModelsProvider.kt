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

import com.android.sdklib.devices.Device
import com.android.tools.configurations.ConfigurationListener
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.ConfigurationMatcher
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile

/** We predefined some pixel devices for now. */
@VisibleForTesting
val DEVICES_TO_DISPLAY =
  listOf(
    "Pixel 3",
    "Pixel 3 XL",
    "Pixel 3a",
    "Pixel 3a XL",
    "Pixel 2",
    "Pixel 2 XL",
    "Pixel",
    "Pixel XL",
    "Pixel C",
  )

private const val EFFECTIVE_FLAGS =
  ConfigurationListener.CFG_ADAPTIVE_SHAPE or
    ConfigurationListener.CFG_DEVICE_STATE or
    ConfigurationListener.CFG_UI_MODE or
    ConfigurationListener.CFG_NIGHT_MODE or
    ConfigurationListener.CFG_THEME or
    ConfigurationListener.CFG_TARGET or
    ConfigurationListener.CFG_LOCALE or
    ConfigurationListener.CFG_FONT_SCALE

/** This class provides the [NlModel]s with predefined pixel devices for [VisualizationForm]. */
object PixelDeviceModelsProvider : VisualizationModelsProvider {

  @VisibleForTesting val deviceCaches = mutableMapOf<ConfigurationManager, List<Device>>()

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
    val pixelDevices =
      deviceCaches.getOrElse(configurationManager) {
        val deviceList = ArrayList<Device>()
        for (name in DEVICES_TO_DISPLAY) {
          configurationManager.devices
            .firstOrNull { device -> name == device.displayName }
            ?.let { deviceList.add(it) }
        }
        deviceCaches[configurationManager] = deviceList
        Disposer.register(configurationManager) { deviceCaches.remove(configurationManager) }
        deviceList
      }

    assert(pixelDevices.isNotEmpty())

    val models = mutableListOf<NlModel>()
    val defaultConfig = configurationManager.getConfiguration(virtualFile)

    for (device in pixelDevices) {
      val config = defaultConfig.clone()
      config.setDevice(device, false)
      val betterFile =
        ConfigurationMatcher.getBetterMatch(config, null, null, null, null) ?: virtualFile
      val model =
        NlModel.Builder(parentDisposable, buildTarget, betterFile, config)
          .withComponentRegistrar(NlComponentRegistrar)
          .build()
      model.setTooltip(config.toHtmlTooltip())
      model.setDisplayName(device.displayName)
      models.add(model)

      registerModelsProviderConfigurationListener(model, defaultConfig, config, EFFECTIVE_FLAGS)
    }
    return models
  }
}
