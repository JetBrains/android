/*
 * Copyright (C) 2023 The Android Open Source Project
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
@file:JvmName("Configurations")
package com.android.tools.configurations

import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import com.android.sdklib.AndroidCoordinate
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.State
import kotlin.math.hypot

/**
 * Extension methods for [Configuration].
 */
@JvmOverloads
fun Configuration.updateScreenSize(
  @AndroidCoordinate xDimension: Int,
  @AndroidCoordinate yDimension: Int,
  original: Device? = this.cachedDevice
) {
  val deviceBuilder =
    if (original != null) Device.Builder(original) else return // doesn't copy tag id
  deviceBuilder.setTagId(original.tagId)

  deviceBuilder.setName("Custom")
  deviceBuilder.setId(Configuration.CUSTOM_DEVICE_ID)
  val device = deviceBuilder.build()
  for (state in device.allStates) {
    val screen = state.hardware.screen
    screen.xDimension = xDimension
    screen.yDimension = yDimension

    val dpi = screen.pixelDensity.dpiValue.toDouble()
    val width = xDimension / dpi
    val height = yDimension / dpi
    val diagonalLength = hypot(width, height)

    screen.diagonalLength = diagonalLength
    screen.size = ScreenSize.getScreenSize(diagonalLength)

    screen.ratio = ScreenRatio.create(xDimension, yDimension)

    screen.screenRound = device.defaultHardware.screen.screenRound
    screen.chin = device.defaultHardware.screen.chin
  }

  // Change the orientation of the device depending on the shape of the canvas
  val newState: State? =
    if (xDimension > yDimension)
      device.allStates.singleOrNull { it.orientation == ScreenOrientation.LANDSCAPE }
    else device.allStates.singleOrNull { it.orientation == ScreenOrientation.PORTRAIT }
  this.setEffectiveDevice(device, newState)
}