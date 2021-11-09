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
package com.android.tools.idea.compose.preview.pickers.properties.utils

import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRound
import com.android.resources.ScreenSize
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.tools.idea.avdmanager.AvdScreenData
import com.android.tools.idea.compose.preview.pickers.properties.DeviceConfig
import com.android.tools.idea.compose.preview.pickers.properties.DimUnit
import com.android.tools.idea.compose.preview.pickers.properties.MutableDeviceConfig
import com.android.tools.idea.compose.preview.pickers.properties.Orientation
import com.android.tools.idea.compose.preview.pickers.properties.Shape
import com.android.tools.idea.compose.preview.pickers.properties.toMutableConfig
import com.android.tools.idea.configurations.Configuration
import com.intellij.openapi.diagnostic.Logger
import kotlin.math.sqrt

/** Prefix used by device specs to find devices by id. */
internal const val DEVICE_BY_ID_PREFIX = "id:"

/** Prefix used by device specs to find devices by name. */
internal const val DEVICE_BY_NAME_PREFIX = "name:"

/** Prefix used by device specs to create devices by hardware specs. */
internal const val DEVICE_BY_SPEC_PREFIX = "spec:"

internal fun Device.toDeviceConfig(): DeviceConfig {
  val config = MutableDeviceConfig().apply { dimUnit = DimUnit.px }
  val deviceState = this.defaultState
  val screen = deviceState.hardware.screen
  config.width = screen.xDimension
  config.height = screen.yDimension
  config.dpi = screen.pixelDensity.dpiValue
  if (screen.screenRound == ScreenRound.ROUND) {
    config.shape = if (screen.chin != 0) Shape.Square else Shape.Round
  }
  else {
    config.shape = Shape.Normal
  }
  return config
}

internal fun DeviceConfig.createDeviceInstance(): Device {
  val deviceConfig = if (this !is MutableDeviceConfig) {
    this.toMutableConfig()
  }
  else {
    this
  }
  val customDevice = Device.Builder().apply {
    setTagId("")
    setName("Custom")
    setId(Configuration.CUSTOM_DEVICE_ID)
    setManufacturer("")
    addSoftware(Software())
    addState(State().apply { isDefaultState = true })
  }.build()
  customDevice.defaultState.apply {
    orientation = when (deviceConfig.orientation) {
      Orientation.landscape -> ScreenOrientation.LANDSCAPE
      Orientation.portrait -> ScreenOrientation.PORTRAIT
    }
    hardware = Hardware().apply {
      screen = Screen().apply {
        deviceConfig.dimUnit = DimUnit.px // Transforms dimension to Pixels
        xDimension = deviceConfig.width
        yDimension = deviceConfig.height
        pixelDensity = AvdScreenData.getScreenDensity(null, false, deviceConfig.dpi.toDouble(), yDimension)
        diagonalLength =
          sqrt((1.0 * xDimension * xDimension) + (1.0 * yDimension * yDimension)) / pixelDensity.dpiValue
        screenRound = when (deviceConfig.shape) {
          Shape.Round,
          Shape.Chin -> ScreenRound.ROUND
          else -> ScreenRound.NOTROUND
        }
        chin = if (deviceConfig.shape == Shape.Chin) 30 else 0
        size = ScreenSize.getScreenSize(diagonalLength)
        ratio = AvdScreenData.getScreenRatio(xDimension, yDimension)
      }
    }
  }
  return customDevice
}

/**
 * Based on [deviceDefinition], returns a [Device] from the collection that matches the name or id, if it's a custom spec, returns a created
 * custom [Device].
 *
 * @see createDeviceInstance
 */
internal fun Collection<Device>.findOrParseFromDefinition(
  deviceDefinition: String,
  logger: Logger = Logger.getInstance(MutableDeviceConfig::class.java)
): Device? {
  return when {
    deviceDefinition.isBlank() -> null
    deviceDefinition.startsWith(DEVICE_BY_SPEC_PREFIX) -> {
      val deviceBySpec = DeviceConfig.toMutableDeviceConfigOrNull(deviceDefinition)?.createDeviceInstance()
      if (deviceBySpec == null) {
        logger.warn("Unable to parse device configuration: $deviceDefinition")
      }
      return deviceBySpec
    }
    else -> findByIdOrName(deviceDefinition, logger)
  }
}

internal fun Collection<Device>.findByIdOrName(
  deviceDefinition: String,
  logger: Logger = Logger.getInstance(MutableDeviceConfig::class.java)
): Device? {
  val availableDevices = this
  return when {
    deviceDefinition.isBlank() -> null
    deviceDefinition.startsWith(DEVICE_BY_ID_PREFIX) -> {
      val id = deviceDefinition.removePrefix(DEVICE_BY_ID_PREFIX)
      val deviceById = availableDevices.firstOrNull { it.id == id }
      if (deviceById == null) {
        logger.warn("Unable to find device with id '$id'")
      }
      return deviceById
    }
    deviceDefinition.startsWith(DEVICE_BY_NAME_PREFIX) -> {
      val name = deviceDefinition.removePrefix(DEVICE_BY_NAME_PREFIX)
      val deviceByName = availableDevices.firstOrNull { it.displayName == name }
      if (deviceByName == null) {
        logger.warn("Unable to find device with name '$name'")
      }
      return deviceByName
    }
    else -> {
      logger.warn("Unsupported device definition: $this")
      null
    }
  }
}