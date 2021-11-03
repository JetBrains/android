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

import com.android.resources.Density
import com.android.resources.ScreenRound
import com.android.sdklib.devices.Device
import com.android.tools.idea.compose.preview.pickers.properties.DeviceConfig
import com.android.tools.idea.compose.preview.pickers.properties.DimUnit
import com.android.tools.idea.compose.preview.pickers.properties.Orientation
import com.android.tools.idea.compose.preview.pickers.properties.Shape
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

internal class DeviceUtilsKtTest {

  @Test
  fun deviceToDeviceConfig() {
    val device = DeviceConfig().createDeviceInstance()
    val screen = device.defaultHardware.screen

    screen.xDimension = 1080
    screen.yDimension = 2280
    screen.pixelDensity = Density.HIGH

    val modifiedConfig = device.toDeviceConfig()
    assertEquals(DimUnit.px, modifiedConfig.dimensionUnit)
    assertEquals(Orientation.portrait, modifiedConfig.orientation)
    assertEquals(1080, modifiedConfig.width)
    assertEquals(2280, modifiedConfig.height)
    assertEquals(240, modifiedConfig.density)
    assertEquals(Shape.Normal, modifiedConfig.shape)

    screen.screenRound = ScreenRound.ROUND
    val roundConfig = device.toDeviceConfig()
    assertEquals(Shape.Round, roundConfig.shape)

    screen.chin = 10
    val squareConfig = device.toDeviceConfig()
    assertEquals(Shape.Square, squareConfig.shape)
  }

  @Test
  fun parseDeviceSpecs() {
    val device1 = emptyList<Device>().findOrParseFromDefinition("spec:shape=Normal,width=100,height=200,unit=px,dpi=300")
    assertNotNull(device1)
    val screen1 = device1.defaultHardware.screen
    assertEquals(100, screen1.xDimension)
    assertEquals(200, screen1.yDimension)
    assertEquals(320, screen1.pixelDensity.dpiValue) // Adjusted Density bucket
    assertEquals(0.69, (screen1.diagonalLength * 100).toInt() / 100.0)

    val device2 = emptyList<Device>().findOrParseFromDefinition("spec:shape=Normal,width=100,height=200,unit=dp,dpi=300")
    assertNotNull(device2)
    val screen2 = device2.defaultHardware.screen
    assertEquals(188, screen2.xDimension)
    assertEquals(375, screen2.yDimension)
    assertEquals(320, screen2.pixelDensity.dpiValue) // Adjusted Density bucket
    assertEquals(1.31, (screen2.diagonalLength * 100).toInt() / 100.0)
  }

  @Test
  fun findByIdAndName() {
    val existingDevices = buildMockDevices()

    val deviceByName = existingDevices.findOrParseFromDefinition("name:name0")
    val screen0 = deviceByName!!.defaultHardware.screen
    assertEquals("name0", deviceByName.displayName)
    assertEquals(1080, screen0.xDimension)
    assertEquals(1920, screen0.yDimension)
    assertEquals(320, screen0.pixelDensity.dpiValue)

    val deviceById = existingDevices.findOrParseFromDefinition("id:id1")
    val screen1 = deviceById!!.defaultHardware.screen
    assertEquals("id1", deviceById.id)
    assertEquals(1080, screen1.xDimension)
    assertEquals(1920, screen1.yDimension)
    assertEquals(480, screen1.pixelDensity.dpiValue)
  }
}

private fun buildMockDevices(): List<Device> {
  // Assign it to name if even, otherwise as an id
  var nameOrIdCount = 0
  return listOf<DeviceConfig>(
    DeviceConfig(width = 1080, height = 1920, dimUnit = DimUnit.px, density = 320, shape = Shape.Normal),
    DeviceConfig(width = 1080, height = 1920, dimUnit = DimUnit.px, density = 480, shape = Shape.Normal),
    DeviceConfig(width = 1080, height = 2280, dimUnit = DimUnit.px, density = 480, shape = Shape.Normal),
    DeviceConfig(width = 600, height = 600, dimUnit = DimUnit.px, density = 480, shape = Shape.Round)
  ).map {
    Device.Builder(it.createDeviceInstance()).also { builder ->
      if (nameOrIdCount % 2 == 0) {
        builder.setName("name${nameOrIdCount++}")
      }
      else {
        builder.setId("id${nameOrIdCount++}")
      }
    }.build()
  }
}