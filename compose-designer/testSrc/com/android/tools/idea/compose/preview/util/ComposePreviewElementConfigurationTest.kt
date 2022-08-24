/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util

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
import com.android.tools.idea.avdmanager.AvdScreenData
import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.Wallpaper
import com.android.tools.idea.preview.PreviewDisplaySettings
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun buildState(
  name: String,
  screenWidthPx: Int,
  screenHeightPx: Int,
  density: Density = Density.MEDIUM,
  screenRound: ScreenRound? = null
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
            ratio = AvdScreenData.getScreenRatio(xDimension, yDimension)
            this.screenRound = screenRound ?: ScreenRound.NOTROUND
            chin = 0
          }
      }
  }

private fun buildDevice(
  name: String,
  id: String = name,
  manufacturer: String = "Google",
  software: List<Software> = listOf(Software()),
  states: List<State> = listOf(buildState("default", 1000, 2000).apply { isDefaultState = true })
): Device =
  Device.Builder()
    .apply {
      setId(id)
      setName(name)
      setManufacturer(manufacturer)
      addAllSoftware(software)
      addAllState(states)
    }
    .build()

private val defaultDevice = buildDevice("default", "DEFAULT")
private val pixel4Device = buildDevice("Pixel 4", "pixel_4")
private val nexus7Device = buildDevice("Nexus 7", "Nexus 7")
private val nexus10Device = buildDevice("Nexus 10", "Nexus 10")
private val roundWearOsDevice =
  buildDevice(
    "Wear OS Round Device",
    "wearos_round",
    states =
      listOf(
        buildState("default", 1000, 100, screenRound = ScreenRound.ROUND).apply {
          isDefaultState = true
          hardware = Hardware()
        }
      )
  )

private val deviceProvider: (Configuration) -> Collection<Device> = {
  listOf(pixel4Device, nexus7Device, nexus10Device, roundWearOsDevice)
}

/** Tests checking [ComposePreviewElement] being applied to a [Configuration]. */
class ComposePreviewElementConfigurationTest() {
  @get:Rule
  val projectRule =
    ComposeProjectRule(
      previewAnnotationPackage = "androidx.compose.ui.tooling.preview",
      composableAnnotationPackage = "androidx.compose.runtime"
    )
  private val fixture
    get() = projectRule.fixture

  private fun assertDeviceMatches(expectedDevice: Device?, deviceSpec: String) {
    val configManager = ConfigurationManager.getOrCreateInstance(fixture.module)
    Configuration.create(configManager, null, FolderConfiguration.createDefault()).also {
      val previewConfiguration =
        PreviewConfiguration.cleanAndGet(null, null, null, null, null, null, null, deviceSpec)
      previewConfiguration.applyConfigurationForTest(
        it,
        highestApiTarget = { null },
        devicesProvider = deviceProvider,
        defaultDeviceProvider = { defaultDevice }
      )
      assertEquals(expectedDevice, it.device)
    }
  }

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
    val configuration =
      Configuration.create(configManager, null, FolderConfiguration.createDefault())

    SingleComposePreviewElementInstance(
        "NoSize",
        PreviewDisplaySettings("Name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet()
      )
      .let { previewElement ->
        previewElement.applyConfigurationForTest(
          configuration,
          highestApiTarget = { null },
          devicesProvider = deviceProvider,
          defaultDeviceProvider = { defaultDevice }
        )
        val screenSize = configuration.device!!.getScreenSize(ScreenOrientation.PORTRAIT)!!
        assertEquals(1000, screenSize.width)
        assertEquals(2000, screenSize.height)
      }
    SingleComposePreviewElementInstance(
        "WithSize",
        PreviewDisplaySettings("Name", null, false, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(null, null, 123, 234, null, null, null, null)
      )
      .let { previewElement ->
        previewElement.applyConfigurationForTest(
          configuration,
          highestApiTarget = { null },
          devicesProvider = deviceProvider,
          defaultDeviceProvider = { defaultDevice }
        )
        val screenSize = configuration.device!!.getScreenSize(ScreenOrientation.PORTRAIT)!!
        assertEquals(123, screenSize.width)
        assertEquals(234, screenSize.height)
      }

    SingleComposePreviewElementInstance(
        "WithSizeAndDecorations",
        PreviewDisplaySettings("Name", null, true, false, null),
        null,
        null,
        PreviewConfiguration.cleanAndGet(null, null, 123, 234, null, null, null, null)
      )
      .let { previewElement ->
        previewElement.applyConfigurationForTest(
          configuration,
          highestApiTarget = { null },
          devicesProvider = deviceProvider,
          defaultDeviceProvider = { defaultDevice }
        )
        val screenSize = configuration.device!!.getScreenSize(ScreenOrientation.PORTRAIT)!!
        assertEquals(1000, screenSize.width)
        assertEquals(2000, screenSize.height)
      }
  }

  private fun assertWallpaperUpdate(expectedWallpaperPath: String?, wallpaperParameterValue: Int?) {
    val configManager = ConfigurationManager.getOrCreateInstance(fixture.module)
    Configuration.create(configManager, null, FolderConfiguration.createDefault()).also {
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
          wallpaperParameterValue
        )
      previewConfiguration.applyConfigurationForTest(
        it,
        highestApiTarget = { null },
        devicesProvider = deviceProvider,
        defaultDeviceProvider = { defaultDevice }
      )
      assertEquals(expectedWallpaperPath, it.wallpaperPath)
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
}
