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

import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRound
import com.android.resources.ScreenSize
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.tools.idea.avdmanager.AvdScreenData
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.uibuilder.model.NlComponentHelper
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.VisibleForTesting
import kotlin.math.sqrt

data class WindowSizeData(val id: String,
                          val name: String,
                          val width: Int,
                          val height: Int,
                          val density: Density,
                          val orientation: ScreenOrientation)

val PREDEFINED_WINDOW_SIZES_DEFINITIONS = mutableListOf(
  WindowSizeData("window_size_small", "Small", 412, 892, Density.XHIGH, ScreenOrientation.PORTRAIT),
  WindowSizeData("window_size_medium", "Medium", 1024, 640, Density.XHIGH, ScreenOrientation.LANDSCAPE),
  WindowSizeData("window_size_large", "Large", 1280, 800, Density.XHIGH, ScreenOrientation.LANDSCAPE),
  WindowSizeData("window_size_x-large", "X-Large", 1600, 900, Density.XHIGH, ScreenOrientation.LANDSCAPE),
)

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

    val devices = deviceCaches.getOrPut(configurationManager) {
      val windowSizeDevices: List<Device> = createWindowDevices()
      Disposer.register(configurationManager) { deviceCaches.remove(configurationManager) }
      windowSizeDevices
    }

    for (device in devices) {
      val config = defaultConfig.clone()
      config.setDevice(device, false)
      val builder = NlModel.builder(facet, virtualFile, config)
        .withParentDisposable(parentDisposable)
        .withModelDisplayName(device.displayName)
        .withModelTooltip(config.toHtmlTooltip())
        .withComponentRegistrar { NlComponentHelper.registerComponent(it) }
      models.add(builder.build())
    }
    return models
  }

  private fun createWindowDevices(): List<Device> =
    PREDEFINED_WINDOW_SIZES_DEFINITIONS.map { windowSizeDef ->
      Device.Builder().apply {
        setTagId("")
        setId(windowSizeDef.id)
        setName(windowSizeDef.name)
        setManufacturer("")
        addSoftware(Software())
        addState(State().apply { isDefaultState = true })
      }.build().also { device ->
        device.defaultState.apply {
          orientation = windowSizeDef.orientation
          hardware = Hardware().apply {
            screen = Screen().apply {
              xDimension = windowSizeDef.width
              yDimension = windowSizeDef.height
              pixelDensity = windowSizeDef.density

              val dpi = pixelDensity.dpiValue.toDouble()
              val width = windowSizeDef.width / dpi
              val height = windowSizeDef.height / dpi
              diagonalLength = sqrt(width * width + height * height)
              size = ScreenSize.getScreenSize(diagonalLength)
              ratio = AvdScreenData.getScreenRatio(windowSizeDef.width, windowSizeDef.height)
              screenRound = ScreenRound.NOTROUND
              chin = 0
            }
          }
        }
      }
    }
}
