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
package com.android.tools.idea.uibuilder.type

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRound
import com.android.resources.ScreenSize
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.avdmanager.AvdScreenData
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.intellij.openapi.vfs.VirtualFile
import kotlin.math.sqrt

private const val PREVIEW_CONFIG_X_DIMENSION = 2000
private const val PREVIEW_CONFIG_Y_DIMENSION = 2000
private val PREVIEW_CONFIG_DENSITY = Density.XXXHIGH

private var deviceAndStateCached: Pair<Device, State>? = null

/**
 * A helper function to provide the [Configuration] for a [VirtualFile]. For example, a drawable file may want to use a custom
 * [Configuration]; a layout file may just use the [Configuration] provided by [ConfigurationManager.getConfiguration].
 */
fun VirtualFile.getConfiguration(configurationManager: ConfigurationManager): Configuration {
  val psiFile = AndroidPsiUtils.getPsiFileSafely(configurationManager.project, this)
  return when (psiFile?.typeOf()) {
    is AdaptiveIconFileType, is DrawableFileType -> configurationManager.getPreviewConfig()
    else -> configurationManager.getConfiguration(this)
  }
}

/**
 * Create specific configuration and device for preview resources (e.g. drawable and vector)
 * The dimension of device is [PREVIEW_CONFIG_X_DIMENSION] x [PREVIEW_CONFIG_Y_DIMENSION].
 * The density is [PREVIEW_CONFIG_DENSITY] if it is compatible with the project api level, otherwise [Density.DPI_560] is used.
 */
fun ConfigurationManager.getPreviewConfig(): Configuration {
  val configurationManager = this
  val config = Configuration.create(configurationManager, null, FolderConfiguration())

  val cached = deviceAndStateCached
  if (cached != null) {
    config.setEffectiveDevice(cached.first, cached.second)
    return config
  }

  val targetApiLevel = configurationManager.target?.version?.apiLevel ?: 1
  val targetDensity = if (targetApiLevel >= PREVIEW_CONFIG_DENSITY.since()) PREVIEW_CONFIG_DENSITY else Density.DPI_560

  val device = Device.Builder().apply {
    setTagId("")
    setName("Custom")
    setId(Configuration.CUSTOM_DEVICE_ID)
    setManufacturer("")
    addSoftware(Software())
    addState(State().apply {
      isDefaultState = true
      hardware = Hardware()
    })
  }.build()

  val state = device.defaultState
  state.apply {
    orientation = ScreenOrientation.SQUARE
    hardware = Hardware().apply {
      screen = Screen().apply {
        xDimension = PREVIEW_CONFIG_X_DIMENSION
        yDimension = PREVIEW_CONFIG_Y_DIMENSION
        pixelDensity = targetDensity

        val dpi = pixelDensity.dpiValue.toDouble()
        val width = PREVIEW_CONFIG_X_DIMENSION / dpi
        val height = PREVIEW_CONFIG_Y_DIMENSION / dpi
        diagonalLength = sqrt(width * width + height * height)
        size = ScreenSize.getScreenSize(diagonalLength)
        ratio = AvdScreenData.getScreenRatio(PREVIEW_CONFIG_X_DIMENSION, PREVIEW_CONFIG_Y_DIMENSION)
        screenRound = ScreenRound.NOTROUND
        chin = 0
      }
    }
  }

  deviceAndStateCached = device to state
  config.setEffectiveDevice(device, state)
  return config
}
