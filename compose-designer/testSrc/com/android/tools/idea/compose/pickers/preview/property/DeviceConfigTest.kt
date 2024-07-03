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
package com.android.tools.idea.compose.pickers.preview.property

import com.android.tools.preview.config.DeviceConfig
import com.android.tools.preview.config.DimUnit
import com.android.tools.preview.config.MutableDeviceConfig
import com.android.tools.preview.config.Orientation
import com.android.tools.preview.config.Shape
import com.android.tools.preview.config.toMutableConfig
import kotlin.test.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class DeviceConfigTest {
  @Test
  fun parseTest() {
    var config = parseDeviceSpec(null)
    assertNull(config)

    config = parseDeviceSpec("spec:shape=Normal,width=120,height=240,unit=px,dpi=480")
    assertNotNull(config)
    assertNull(config.deviceId)
    assertEquals(120f, config.width)
    assertEquals(240f, config.height)
    assertEquals(DimUnit.px, config.dimUnit)
    assertEquals(480, config.dpi)
    assertEquals(Orientation.portrait, config.orientation)

    config = parseDeviceSpec("spec:shape=Round,width=240,height=120,unit=px,dpi=480")
    assertNotNull(config)
    assertNull(config.deviceId)
    assertEquals(Orientation.landscape, config.orientation)
    assertEquals(Shape.Round, config.shape)

    // Additional parameters ignored, should be handled by Inspections
    assertNotNull(
      parseDeviceSpec("spec:id=myId,shape=Round,width=240,height=120,unit=px,dpi=480,foo=bar")
    )

    // Invalid values in known parameters
    assertNull(parseDeviceSpec("spec:shape=Round,width=invalid,height=1920,unit=px,dpi=invalid"))

    // Missing required parameters
    assertNull(parseDeviceSpec("spec:shape=Round,width=240,height=120"))
  }

  @Test
  fun parseTestDeviceSpecLanguage() {
    var config =
      parseDeviceSpec(
        "spec:width=1080.1px,height=1920.2px,dpi=320,isRound=true,chinSize=50.3px,orientation=landscape"
      )
    assertNotNull(config)
    assertEquals(1080.1f, config.width)
    assertEquals(1920.2f, config.height)
    assertEquals(320, config.dpi)
    assertTrue(config.isRound)
    assertEquals(50.3f, config.chinSize)
    assertEquals(DimUnit.px, config.dimUnit)
    assertEquals(Orientation.landscape, config.orientation)

    config = parseDeviceSpec("spec:width=200.4dp,height=300.5dp,chinSize=10.6dp")
    assertNotNull(config)
    assertEquals(200.4f, config.width)
    assertEquals(300.5f, config.height)
    assertEquals(420, config.dpi)
    assertTrue(config.isRound)
    assertEquals(10.6f, config.chinSize)
    assertEquals(DimUnit.dp, config.dimUnit)

    // Verify default values
    config = parseDeviceSpec("spec:width=300dp,height=200dp")
    assertNotNull(config)
    assertEquals(420, config.dpi)
    assertFalse(config.isRound)
    assertEquals(0f, config.chinSize)
    assertEquals(Orientation.landscape, config.orientation) // orientation implied
    config = parseDeviceSpec("spec:width=100dp,height=200dp")
    assertNotNull(config)
    assertEquals(Orientation.portrait, config.orientation) // orientation implied

    // Width & height required
    assertNull(parseDeviceSpec("spec:width=100dp"))
    assertNull(parseDeviceSpec("spec:height=100dp"))

    // Width & height should have matching units
    assertNull(parseDeviceSpec("spec:width=100dp,height=1920px"))

    // Width, height & chinSize (when present) should have matching units
    assertNull(parseDeviceSpec("spec:width=100dp,height=200dp,chinSize=200px"))
    assertNull(parseDeviceSpec("spec:width=100px,height=200px,chinSize=200dp"))

    // Old syntax has no effect, these types of issues should be highlighted by Inspections
    assertEquals(DimUnit.px, parseDeviceSpec("spec:width=1080px,height=1920px,unit=dp")!!.dimUnit)
  }

  @Test
  fun modificationsTest() {
    val config = MutableDeviceConfig()
    assertEquals(411f, config.width)
    assertEquals(891f, config.height)

    config.dimUnit = DimUnit.px
    assertEquals(1078.875f, config.width)
    assertEquals(2338.875f, config.height)

    // We change the dpi, which should result in different dimensions in dp (half pixel density =
    // twice as the length on each dimension)
    config.dpi = config.dpi / 2
    config.dimUnit = DimUnit.dp
    assertEquals(822f, config.width)
    assertEquals(1782f, config.height)

    assertEquals(Orientation.portrait, config.orientation)

    val temp = config.width
    config.width = config.height
    config.height = temp
    // Have to manually change the orientation
    assertEquals(Orientation.portrait, config.orientation)

    val tempW = config.width
    val tempH = config.height

    // After orientation change, width and height should remain the same
    config.orientation = Orientation.landscape
    assertEquals(tempW, config.width)
    assertEquals(tempH, config.height)
  }

  @Test
  fun deviceSpecLanguageString() {
    // For usage with DeviceSpec Language
    var config =
      DeviceConfig(
        width = 100f,
        height = 200f,
        dimUnit = DimUnit.dp,
        dpi = 300,
        shape = Shape.Round,
        chinSize = 40f,
      )
    assertEquals(
      "spec:width=100dp,height=200dp,dpi=300,isRound=true,chinSize=40dp",
      config.deviceSpec(),
    )

    // Orientation change is reflected as a parameter with the DeviceSpec Language
    assertEquals(
      "spec:width=100dp,height=200dp,dpi=300,isRound=true,chinSize=40dp,orientation=landscape",
      config.toMutableConfig().apply { orientation = Orientation.landscape }.deviceSpec(),
    )

    // Implied Orientation of width/height is not reflected in spec
    config = DeviceConfig(width = 200f, height = 100f, orientation = Orientation.landscape)
    assertEquals("spec:width=200dp,height=100dp", config.deviceSpec())
    config = DeviceConfig(width = 200f, height = 100f, orientation = Orientation.portrait)
    assertEquals("spec:width=200dp,height=100dp,orientation=portrait", config.deviceSpec())

    // Parameters with default values are not shown (except for width & height)
    // Default dpi not shown
    config =
      DeviceConfig(
        width = 100f,
        height = 200f,
        dimUnit = DimUnit.dp,
        dpi = 420,
        shape = Shape.Round,
        chinSize = 40f,
      )
    assertEquals("spec:width=100dp,height=200dp,isRound=true,chinSize=40dp", config.deviceSpec())

    // Default chinSize not shown
    config =
      DeviceConfig(
        width = 100f,
        height = 200f,
        dimUnit = DimUnit.dp,
        dpi = 420,
        shape = Shape.Round,
        chinSize = 0f,
      )
    assertEquals("spec:width=100dp,height=200dp,isRound=true", config.deviceSpec())

    // Default isRound not shown, chinSize is dependent on device being round
    config =
      DeviceConfig(
        width = 100f,
        height = 200f,
        dimUnit = DimUnit.dp,
        dpi = 420,
        shape = Shape.Normal,
        chinSize = 40f,
      )
    assertEquals("spec:width=100dp,height=200dp", config.deviceSpec())

    // For DeviceSpec Language, one decimal for floating point supported
    assertEquals(
      "spec:width=123.5dp,height=567.9dp",
      DeviceConfig(width = 123.45f, height = 567.89f).deviceSpec(),
    )
  }

  @Test
  fun testReferenceDevicesIdInjection() {
    assertEquals("_device_class_phone", parseDeviceSpec("spec:width=411dp,height=891dp")!!.deviceId)
    assertEquals(
      "_device_class_foldable",
      parseDeviceSpec("spec:width=673dp,height=841dp")!!.deviceId,
    )
    assertEquals(
      "_device_class_tablet",
      parseDeviceSpec("spec:width=1280dp,height=800dp,dpi=240")!!.deviceId,
    )
    assertEquals(
      "_device_class_desktop",
      parseDeviceSpec("spec:width=1920dp,height=1080dp,dpi=160")!!.deviceId,
    )
  }
}

private fun parseDeviceSpec(deviceSpec: String?): DeviceConfig? {
  return DeviceConfig.toDeviceConfigOrNull(deviceSpec, emptyList())
}
