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
package com.android.tools.preview.config

import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRound
import com.android.sdklib.devices.Device
import kotlin.test.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DeviceUtilsKtTest {

  @Test
  fun deviceToDeviceConfig() {
    val device = MutableDeviceConfig().createDeviceInstance()
    val screen = device.defaultHardware.screen

    screen.xDimension = 1080
    screen.yDimension = 2280
    screen.pixelDensity = Density.HIGH

    // Make it Round
    screen.screenRound = ScreenRound.ROUND

    // Give it a chin
    screen.chin = 10

    val roundChinConfig = device.toDeviceConfig()
    assertTrue(roundChinConfig.isRound)
    assertEquals(Shape.Round, roundChinConfig.shape) // Not using Shape.Chin anymore
    assertEquals(10f, roundChinConfig.chinSize) // ChinSize is reflected properly

    // Make width higher than height, should not affect orientation
    screen.xDimension = 2300
    // ScreenOrientation affects orientation
    device.defaultState.orientation = ScreenOrientation.LANDSCAPE
    val landscapeConfig = device.toDeviceConfig()
    assertEquals(Orientation.landscape, landscapeConfig.orientation)
    assertEquals(2300f, landscapeConfig.width)
    assertEquals(2280f, landscapeConfig.height)

    // On legacy DeviceSpec, width and height are swapped to reflect orientation
    assertEquals(
      "spec:width=2300px,height=2280px,dpi=240,isRound=true,chinSize=10px",
      landscapeConfig.deviceSpec()
    )
  }

  @Test
  fun deviceInstanceOrientation() {
    var device: Device? = null
    val screenProvider = { device!!.defaultHardware.screen }
    val orientationProvider = { device!!.defaultState.orientation }

    device = deviceFromDeviceSpec("spec:width=100px,height=200px")
    assertEquals(100, screenProvider().xDimension)
    assertEquals(200, screenProvider().yDimension)
    assertEquals(ScreenOrientation.PORTRAIT, orientationProvider())

    device = deviceFromDeviceSpec("spec:width=300px,height=200px")
    assertEquals(300, screenProvider().xDimension)
    assertEquals(200, screenProvider().yDimension)
    assertEquals(
      ScreenOrientation.LANDSCAPE,
      orientationProvider()
    ) // Orientation implied from dimensions

    device = deviceFromDeviceSpec("spec:width=100px,height=200px,orientation=portrait")
    assertEquals(100, screenProvider().xDimension)
    assertEquals(200, screenProvider().yDimension)
    assertEquals(ScreenOrientation.PORTRAIT, orientationProvider())
  }

  @Test
  fun deviceInstanceRoundAndChin() {
    // From DeviceConfig
    var screen =
      DeviceConfig(
        width = 100f,
        height = 100f,
        dimUnit = DimUnit.px,
        shape = Shape.Round,
        chinSize = 20f
      )
        .createDeviceInstance()
        .defaultHardware
        .screen
    assertEquals(ScreenRound.ROUND, screen.screenRound)
    assertEquals(20, screen.chin)

    // From DeviceSpec Language
    screen =
      deviceFromDeviceSpec("spec:width=100px,height=200px,isRound=true,chinSize=50px")!!
        .defaultHardware
        .screen
    assertEquals(ScreenRound.ROUND, screen.screenRound)
    assertEquals(50, screen.chin)
  }

  @Test
  fun deviceInstanceWithDifferentDimensionUnit() {
    val device1 = deviceFromDeviceSpec("spec:width=100px,height=200px,dpi=310")
    assertNotNull(device1)
    val screen1 = device1.defaultHardware.screen
    assertEquals(100, screen1.xDimension)
    assertEquals(200, screen1.yDimension)
    assertEquals(320, screen1.pixelDensity.dpiValue) // Adjusted Density bucket
    assertEquals(0.69, (screen1.diagonalLength * 100).toInt() / 100.0, 0.0)

    val device2 = deviceFromDeviceSpec("spec:width=100dp,height=200dp,dpi=310")
    assertNotNull(device2)
    val screen2 = device2.defaultHardware.screen

    // Note: these dimensions are calculated with the closest Density bucket for dpi=310: XHDPI
    // (320)
    assertEquals(200, screen2.xDimension)
    assertEquals(400, screen2.yDimension)
    assertEquals(320, screen2.pixelDensity.dpiValue) // Adjusted Density bucket
    assertEquals(1.39, (screen2.diagonalLength * 100).toInt() / 100.0, 0.0)
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
    assertEquals(540, screen1.xDimension)
    assertEquals(960, screen1.yDimension)
    assertEquals(640, screen1.pixelDensity.dpiValue)
    assertEquals(ScreenOrientation.PORTRAIT, deviceById.defaultState.orientation)

    // Device parameters should be the same as 'id1' with a different orientation
    val deviceByParentId =
      existingDevices.findOrParseFromDefinition("spec:parent=id1,orientation=landscape")
    val screen2 = deviceByParentId!!.defaultHardware.screen
    // Devices defined by 'spec' are always Custom devices
    assertEquals("Custom", deviceByParentId.id)
    assertEquals(540, screen2.xDimension)
    assertEquals(960, screen2.yDimension)
    assertEquals(640, screen2.pixelDensity.dpiValue)
    assertEquals(ScreenOrientation.LANDSCAPE, deviceByParentId.defaultState.orientation)
  }
}

private fun deviceFromDeviceSpec(deviceDefinition: String): Device? =
  emptyList<Device>().findOrParseFromDefinition(deviceDefinition)

private fun buildMockDevices(): List<Device> {
  // Assign it to name if even, otherwise as an id
  var nameOrIdCount = 0
  return listOf(
    DeviceConfig(
      width = 1080f,
      height = 1920f,
      dimUnit = DimUnit.px,
      dpi = 320,
      shape = Shape.Normal
    ),
    DeviceConfig(
      width = 540f,
      height = 960f,
      dimUnit = DimUnit.px,
      dpi = 640,
      shape = Shape.Normal
    ),
    DeviceConfig(
      width = 1080f,
      height = 2280f,
      dimUnit = DimUnit.px,
      dpi = 480,
      shape = Shape.Normal
    ),
    DeviceConfig(
      width = 600f,
      height = 600f,
      dimUnit = DimUnit.px,
      dpi = 480,
      shape = Shape.Round
    )
  )
    .map {
      Device.Builder(it.createDeviceInstance())
        .also { builder ->
          if (nameOrIdCount % 2 == 0) {
            builder.setName("name${nameOrIdCount++}")
          } else {
            builder.setId("id${nameOrIdCount++}")
          }
        }
        .build()
    }
}
