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
package com.android.tools.idea.compose.preview

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenRound
import com.android.resources.ScreenSize
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.Wallpaper
import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.preview.PreviewConfiguration
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.SingleComposePreviewElementInstance
import com.android.tools.preview.applyConfigurationForTest
import com.android.tools.res.FrameworkOverlay
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun buildState(
  name: String,
  screenWidthPx: Int,
  screenHeightPx: Int,
  density: Density = Density.MEDIUM,
  screenRound: ScreenRound? = null,
): State =
  State().apply {
    this.name = name
    orientation = ScreenOrientation.PORTRAIT
    hardware =
      Hardware().apply {
        screen =
          Screen().apply {
            xDimension = screenWidthPx
            yDimension = screenHeightPx
            pixelDensity = density

            xdpi = pixelDensity.dpiValue.toDouble()
            ydpi = pixelDensity.dpiValue.toDouble()

            val widthDp = screenWidthPx.toDouble() * 160 / density.dpiValue
            val heightDp = screenHeightPx.toDouble() * 160 / density.dpiValue
            diagonalLength = sqrt(widthDp * widthDp + heightDp * heightDp) / 160
            size = ScreenSize.getScreenSize(diagonalLength)
            ratio = ScreenRatio.create(xDimension, yDimension)
            this.screenRound = screenRound ?: ScreenRound.NOTROUND
            chin = 0
          }
      }
  }

private fun buildDevice(
  name: String,
  id: String = name,
  tagId: String? = null,
  manufacturer: String = "Google",
  software: List<Software> = listOf(Software()),
  states: List<State> = listOf(buildState("default", 1000, 2000).apply { isDefaultState = true }),
): Device =
  Device.Builder()
    .apply {
      setId(id)
      setTagId(tagId)
      setName(name)
      setManufacturer(manufacturer)
      addAllSoftware(software)
      addAllState(states)
    }
    .build()

private val defaultDevice = buildDevice("default", "DEFAULT")
private val pixel4Device =
  buildDevice(
    "Pixel 4",
    "pixel_4",
    states =
      listOf(
        buildState("Portrait", 1000, 2000).apply { isDefaultState = true },
        buildState("Landscape", 1000, 2000),
      ),
  )
private val nexus7Device = buildDevice("Nexus 7", "Nexus 7")
private val nexus10Device = buildDevice("Nexus 10", "Nexus 10")
private val roundWearOsDevice =
  buildDevice(
    "Wear OS Round Device",
    "wearos_round",
    "android-wear",
    states =
      listOf(
        buildState("default", 1000, 100, screenRound = ScreenRound.ROUND).apply {
          isDefaultState = true
        }
      ),
  )
private val deviceWithCustomDensity =
  buildDevice(
    name = "Device with custom density",
    id = "device_with_custom_density",
    states =
      listOf(buildState("default", 1000, 2000, Density.create(440)).apply { isDefaultState = true }),
  )

private val deviceProvider: (Configuration) -> Collection<Device> = {
  listOf(pixel4Device, nexus7Device, nexus10Device, roundWearOsDevice)
}

/** Tests checking [ComposePreviewElement] being applied to a [Configuration]. */
class ComposePreviewElementConfigurationTest {
  @get:Rule val projectRule = ComposeProjectRule(projectRule = AndroidProjectRule.withSdk())
  private val fixture
    get() = projectRule.fixture

  @Test
  fun `set device by id and name successfully`() {
    // Find by id
    assertDeviceMatches(nexus7Device, "id:Nexus 7")
    // Find by name
    assertDeviceMatches(pixel4Device, "name:Pixel 4")
    // Device not found
    assertDeviceMatches(defaultDevice, "id:not found")
    assertDeviceMatches(defaultDevice, "name:not found")
    assertDeviceMatches(defaultDevice, "invalid:pixel_4")
  }

  @Test
  fun `test specified device sizes`() {
    val configManager = ConfigurationManager.getOrCreateInstance(fixture.module)
    configManager.defaultDevice
    val configuration = Configuration.create(configManager, FolderConfiguration.createDefault())

    SingleComposePreviewElementInstance(
        "NoSize",
        PreviewDisplaySettings("Name", "BaseName", "ParameterName", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(),
      )
      .let { previewElement ->
        previewElement.applyConfigurationForTest(
          configuration,
          highestApiTarget = { null },
          devicesProvider = deviceProvider,
          defaultDeviceProvider = { defaultDevice },
        )
        val screenSize = configuration.device!!.getScreenSize(ScreenOrientation.PORTRAIT)!!
        assertEquals(1000, screenSize.width)
        assertEquals(2000, screenSize.height)
      }
    SingleComposePreviewElementInstance(
        "WithSize",
        PreviewDisplaySettings("Name", "BaseName", "ParameterName", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(null, null, 123, 234, null, null, null, null),
      )
      .let { previewElement ->
        previewElement.applyConfigurationForTest(
          configuration,
          highestApiTarget = { null },
          devicesProvider = deviceProvider,
          defaultDeviceProvider = { defaultDevice },
        )
        val screenSize = configuration.device!!.getScreenSize(ScreenOrientation.PORTRAIT)!!
        assertEquals(123, screenSize.width)
        assertEquals(234, screenSize.height)
      }

    SingleComposePreviewElementInstance(
        "WithSizeAndDecorations",
        PreviewDisplaySettings("Name", "BaseName", "ParameterName", null, true, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(null, null, 123, 234, null, null, null, null),
      )
      .let { previewElement ->
        previewElement.applyConfigurationForTest(
          configuration,
          highestApiTarget = { null },
          devicesProvider = deviceProvider,
          defaultDeviceProvider = { defaultDevice },
        )
        val screenSize = configuration.device!!.getScreenSize(ScreenOrientation.PORTRAIT)!!
        assertEquals(1000, screenSize.width)
        assertEquals(2000, screenSize.height)
      }

    SingleComposePreviewElementInstance(
        "WithSizeAndCustomDensity",
        PreviewDisplaySettings("Name", "BaseName", "ParameterName", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(null, null, 123, 234, null, null, null, null),
      )
      .let { previewElement ->
        previewElement.applyConfigurationForTest(
          configuration,
          highestApiTarget = { null },
          devicesProvider = deviceProvider,
          defaultDeviceProvider = { deviceWithCustomDensity },
        )
        val screenSize = configuration.device!!.getScreenSize(ScreenOrientation.PORTRAIT)!!
        assertEquals(338, screenSize.width)
        assertEquals(643, screenSize.height)
      }
  }

  @Test
  fun `setting a device might set the theme as well`() {
    val configManager = ConfigurationManager.getOrCreateInstance(fixture.module)
    val configuration = Configuration.create(configManager, FolderConfiguration.createDefault())

    SingleComposePreviewElementInstance(
        "WearOs",
        PreviewDisplaySettings("Name", "BaseName", "ParameterName", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(null, null, 100, 100, null, null, null, null),
      )
      .let { previewElement ->
        previewElement.applyConfigurationForTest(
          configuration,
          highestApiTarget = { null },
          devicesProvider = deviceProvider,
          defaultDeviceProvider = { roundWearOsDevice },
        )
        assertEquals("@android:style/Theme.DeviceDefault", configuration.theme)
      }

    SingleComposePreviewElementInstance(
        "Pixel",
        PreviewDisplaySettings("Name", "BaseName", "ParameterName", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(null, null, 100, 100, null, null, null, null),
      )
      .let { previewElement ->
        previewElement.applyConfigurationForTest(
          configuration,
          highestApiTarget = { null },
          devicesProvider = deviceProvider,
          defaultDeviceProvider = { pixel4Device },
        )
        assertEquals("@android:style/Theme", configuration.theme)
      }
  }

  @Test
  fun testWallpaperConfiguration() {
    assertWallpaperUpdate(null, null)
    assertWallpaperUpdate(null, -25)
    assertWallpaperUpdate(null, 167)
    assertWallpaperUpdate(null, -1)
    assertWallpaperUpdate(Wallpaper.RED.resourcePath, 0)
    assertWallpaperUpdate(Wallpaper.GREEN.resourcePath, 1)
    assertWallpaperUpdate(Wallpaper.BLUE.resourcePath, 2)
    assertWallpaperUpdate(Wallpaper.YELLOW.resourcePath, 3)
  }

  @Test
  fun testApiLevel() {
    val configManager = ConfigurationManager.getOrCreateInstance(fixture.module)
    Configuration.create(configManager, FolderConfiguration.createDefault()).also {
      val previewConfiguration =
        PreviewConfiguration.cleanAndGet(30, null, null, null, null, null, null, null, null)
      previewConfiguration.applyConfigurationForTest(
        it,
        highestApiTarget = { configManager.highestApiTarget },
        devicesProvider = deviceProvider,
        defaultDeviceProvider = { defaultDevice },
      )
      assertEquals(30, it.fullConfig.versionQualifier?.version)
    }
  }

  @Test
  fun testParentId() {
    val configManager = ConfigurationManager.getOrCreateInstance(fixture.module)
    Configuration.create(configManager, FolderConfiguration.createDefault()).also {
      val previewConfiguration =
        PreviewConfiguration.cleanAndGet(
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          "id:pixel_4",
          null,
        )
      previewConfiguration.applyConfigurationForTest(
        it,
        highestApiTarget = { configManager.highestApiTarget },
        devicesProvider = deviceProvider,
        defaultDeviceProvider = { defaultDevice },
      )
      assertEquals(pixel4Device, it.device)
      assertEquals(pixel4Device.getState("Portrait"), it.deviceState)
      assertTrue(it.isGestureNav)
      assertEquals(
        listOf(
          FrameworkOverlay.NAV_GESTURE,
          FrameworkOverlay.PIXEL_4,
          FrameworkOverlay.CUTOUT_NONE,
        ),
        it.overlays,
      )
    }

    Configuration.create(configManager, FolderConfiguration.createDefault()).also {
      val previewConfiguration =
        PreviewConfiguration.cleanAndGet(
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          "spec:parent=pixel_4,orientation=landscape,navigation=buttons",
          null,
        )
      previewConfiguration.applyConfigurationForTest(
        it,
        highestApiTarget = { configManager.highestApiTarget },
        devicesProvider = deviceProvider,
        defaultDeviceProvider = { defaultDevice },
      )
      assertFalse(it.isGestureNav)
      assertEquals(
        listOf(
          FrameworkOverlay.NAV_3_BUTTONS,
          FrameworkOverlay.PIXEL_4,
          FrameworkOverlay.CUTOUT_NONE,
        ),
        it.overlays,
      )
    }
  }

  @Test
  fun testCutout() {
    val configManager = ConfigurationManager.getOrCreateInstance(fixture.module)
    Configuration.create(configManager, FolderConfiguration.createDefault()).also {
      val previewConfiguration =
        PreviewConfiguration.cleanAndGet(
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          "spec:width=100dp,height=200dp,dpi=310",
          null,
        )
      previewConfiguration.applyConfigurationForTest(
        it,
        highestApiTarget = { configManager.highestApiTarget },
        devicesProvider = deviceProvider,
        defaultDeviceProvider = { defaultDevice },
      )
      assertEquals(FrameworkOverlay.CUTOUT_NONE, it.cutoutOverlay)
    }

    Configuration.create(configManager, FolderConfiguration.createDefault()).also {
      val previewConfiguration =
        PreviewConfiguration.cleanAndGet(
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          "spec:width=100dp,height=200dp,dpi=310,cutout=corner",
          null,
        )
      previewConfiguration.applyConfigurationForTest(
        it,
        highestApiTarget = { configManager.highestApiTarget },
        devicesProvider = deviceProvider,
        defaultDeviceProvider = { defaultDevice },
      )
      assertEquals(FrameworkOverlay.CUTOUT_CORNER, it.cutoutOverlay)
    }
  }

  private fun assertWallpaperUpdate(expectedWallpaperPath: String?, wallpaperParameterValue: Int?) {
    val configManager = ConfigurationManager.getOrCreateInstance(fixture.module)
    Configuration.create(configManager, FolderConfiguration.createDefault()).also {
      val previewConfiguration =
        PreviewConfiguration.cleanAndGet(
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          wallpaperParameterValue,
        )
      previewConfiguration.applyConfigurationForTest(
        it,
        highestApiTarget = { null },
        devicesProvider = deviceProvider,
        defaultDeviceProvider = { defaultDevice },
      )
      assertEquals(expectedWallpaperPath, it.wallpaperPath)
    }
  }

  private fun assertDeviceMatches(expectedDevice: Device?, deviceSpec: String) {
    val configManager = ConfigurationManager.getOrCreateInstance(fixture.module)
    Configuration.create(configManager, FolderConfiguration.createDefault()).also {
      val previewConfiguration =
        PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null, deviceSpec)
      previewConfiguration.applyConfigurationForTest(
        it,
        highestApiTarget = { null },
        devicesProvider = deviceProvider,
        defaultDeviceProvider = { defaultDevice },
      )
      assertEquals(expectedDevice, it.device)
    }
  }
}
