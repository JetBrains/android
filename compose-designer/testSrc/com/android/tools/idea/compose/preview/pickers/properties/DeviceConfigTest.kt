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
package com.android.tools.idea.compose.preview.pickers.properties

import com.android.tools.idea.flags.StudioFlags
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class DeviceConfigTest {

  @Test
  fun parseTest() {
    var config = DeviceConfig.toDeviceConfigOrNull(null)
    assertNull(config)

    config = DeviceConfig.toDeviceConfigOrNull("spec:shape=Normal,width=120,height=240,unit=px,dpi=480")
    assertNotNull(config)
    assertEquals(120, config.width)
    assertEquals(240, config.height)
    assertEquals(DimUnit.px, config.dimUnit)
    assertEquals(480, config.dpi)
    assertEquals(Orientation.portrait, config.orientation)

    config = DeviceConfig.toDeviceConfigOrNull("spec:shape=Round,width=240,height=120,unit=px,dpi=480")
    assertNotNull(config)
    assertEquals(Orientation.landscape, config.orientation)
    assertEquals(Shape.Round, config.shape)

    config = DeviceConfig.toDeviceConfigOrNull("spec:shape=Round,width=invalid,height=1920,unit=px,dpi=invalid")
    assertNull(config)
  }

  @Test
  fun parseTestLegacy() {
    var config = DeviceConfig.toDeviceConfigOrNull("spec:Normal;120w;240h;px;480dpi")
    assertNotNull(config)
    assertEquals(120, config.width)
    assertEquals(240, config.height)
    assertEquals(DimUnit.px, config.dimUnit)
    assertEquals(480, config.dpi)
    assertEquals(Orientation.portrait, config.orientation)

    config = DeviceConfig.toDeviceConfigOrNull("spec:Round;240w;120h;px;480dpi")
    assertNotNull(config)
    assertEquals(Orientation.landscape, config.orientation)
    assertEquals(Shape.Round, config.shape)

    config = DeviceConfig.toDeviceConfigOrNull("spec:Round;invalid;1920;px;invalid")
    assertNull(config)
  }

  @Test
  fun parseTestDeviceSpecLanguage() {
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.override(true)
    var config = DeviceConfig.toDeviceConfigOrNull("spec:width=1080px,height=1920px,dpi=320,isRound=true,chinSize=50px")
    assertNotNull(config)
    assertEquals(1080, config.width)
    assertEquals(1920, config.height)
    assertEquals(320, config.dpi)
    assertTrue(config.isRound)
    assertEquals(50, config.chinSize)
    assertEquals(DimUnit.px, config.dimUnit)

    config = DeviceConfig.toDeviceConfigOrNull("spec:width=200dp,height=300dp,chinSize=10dp")
    assertNotNull(config)
    assertEquals(200, config.width)
    assertEquals(300, config.height)
    assertEquals(480, config.dpi)
    assertTrue(config.isRound)
    assertEquals(10, config.chinSize)
    assertEquals(DimUnit.dp, config.dimUnit)

    // Width & height required
    assertNull(DeviceConfig.toDeviceConfigOrNull("spec:width=100dp"))
    assertNull(DeviceConfig.toDeviceConfigOrNull("spec:height=100dp"))

    // Width & height should have matching units
    assertNull(DeviceConfig.toDeviceConfigOrNull("spec:width=100dp,height=1920px"))

    // Width, height & chinSize (when present) should have matching units
    assertNull(DeviceConfig.toDeviceConfigOrNull("spec:width=100dp,height=200dp,chinSize=200px"))
    assertNull(DeviceConfig.toDeviceConfigOrNull("spec:width=100px,height=200px,chinSize=200dp"))

    // Old syntax has no effect, these types of issues should be highlighted by Inspections
    assertEquals(DimUnit.px, DeviceConfig.toDeviceConfigOrNull("spec:width=1080px,height=1920px,unit=dp")!!.dimUnit)
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.clearOverride()
  }

  @Test
  fun modificationsTest() {
    val config = MutableDeviceConfig()
    assertEquals(1080, config.width)
    assertEquals(1920, config.height)

    config.dimUnit = DimUnit.dp
    assertEquals(360, config.width)
    assertEquals(640, config.height)

    // We change the dpi, which should result in different dimensions in pixels (half pixel density = half pixels on each dimension)
    config.dpi = config.dpi / 2
    config.dimUnit = DimUnit.px
    assertEquals(540, config.width)
    assertEquals(960, config.height)

    assertEquals(Orientation.portrait, config.orientation)

    val temp = config.width
    config.width = config.height
    config.height = temp
    assertEquals(Orientation.landscape, config.orientation)
  }

  @Test
  fun testDeviceSpecString() {
    // Example of DeviceSpec string when new DeviceSpec Language is NOT in use, chinSize ignored
    var config = DeviceConfig(width = 100, height = 200, dimUnit = DimUnit.dp, dpi = 300, shape = Shape.Round, chinSize = 40)
    assertEquals("spec:shape=Round,width=100,height=200,unit=dp,dpi=300", config.deviceSpec())

    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.override(true)
    // For usage with DeviceSpec Language, note that parameters with default values are not shown (except for width & height)
    config = DeviceConfig(width = 100, height = 200, dimUnit = DimUnit.dp, dpi = 300, shape = Shape.Round, chinSize = 40)
    assertEquals("spec:width=100dp,height=200dp,dpi=300,isRound=true,chinSize=40dp", config.deviceSpec())

    // Default dpi not shown
    config = DeviceConfig(width = 100, height = 200, dimUnit = DimUnit.dp, dpi = 480, shape = Shape.Round, chinSize = 40)
    assertEquals("spec:width=100dp,height=200dp,isRound=true,chinSize=40dp", config.deviceSpec())

    // Default chinSize not shown
    config = DeviceConfig(width = 100, height = 200, dimUnit = DimUnit.dp, dpi = 480, shape = Shape.Round, chinSize = 0)
    assertEquals("spec:width=100dp,height=200dp,isRound=true", config.deviceSpec())

    // Default isRound not shown, chinSize is dependent on device being round
    config = DeviceConfig(width = 100, height = 200, dimUnit = DimUnit.dp, dpi = 480, shape = Shape.Normal, chinSize = 40)
    assertEquals("spec:width=100dp,height=200dp", config.deviceSpec())
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.clearOverride()
  }
}