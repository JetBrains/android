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
package com.android.tools.idea.uibuilder.surface

import com.android.resources.Density
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.RenderLogger
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.createRenderTaskErrorResult
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.ScreenView.DEVICE_CONTENT_SIZE_POLICY
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import java.awt.Dimension

class ScreenViewTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @After
  fun tearDown() {
    StudioFlags.NELE_DP_SIZED_PREVIEW.clearOverride()
  }

  private fun buildState(): State {
    val screen = Screen().apply {
      yDimension = 500
      xDimension = 300
    }
    val hardware = Hardware().apply {
      setScreen(screen)
    }
    return State().apply {
      setHardware(hardware)
      isDefaultState = true
    }
  }

  private fun buildDevice(name: String, defaultState: State = buildState()): Device {
    return Device.Builder().apply {
      setName(name)
      setManufacturer(name)
      addSoftware(Software())
      addState(defaultState)
    }.build()
  }

  @Test
  fun `device content size policy with no content returns no content size`() {
    val screenView = mock(ScreenView::class.java)
    val sceneManager = mock(LayoutlibSceneManager::class.java)
    val renderLogger = RenderLogger()
    // Fully simulate an error to make the result invalid
    renderLogger.addBrokenClass("Broken", Throwable())
    val file = projectRule.fixture.addFileToProject("src/EmptyFile.kt", "")
    val result = createRenderTaskErrorResult(file, renderLogger)

    whenever(sceneManager.renderResult).thenReturn(result)
    whenever(screenView.sceneManager).thenReturn(sceneManager)

    assertFalse(DEVICE_CONTENT_SIZE_POLICY.hasContentSize(screenView))
  }

  @Test
  fun `device content size policy with no device returns no size`() {
    val screenView = mock(ScreenView::class.java)
    val configuration = mock(Configuration::class.java)
    whenever(configuration.cachedDevice).thenReturn(null)
    whenever(configuration.deviceState).thenReturn(null)

    whenever(screenView.configuration).thenReturn(configuration)

    val outDimension = Dimension(123, 123)

    DEVICE_CONTENT_SIZE_POLICY.measure(screenView, outDimension)

    // Not modified
    assertEquals("measure should not modify the dimensions where there is no device available", 123, outDimension.width)
    assertEquals("measure should not modify the dimensions where there is no device available", 123, outDimension.height)

    whenever(configuration.cachedDevice).thenReturn(buildDevice("Pixel5"))
    DEVICE_CONTENT_SIZE_POLICY.measure(screenView, outDimension)
    // Not modified
    assertEquals("measure should not modify the dimensions where there is no state available", 123, outDimension.width)
    assertEquals("measure should not modify the dimensions where there is no state available", 123, outDimension.height)
  }

  @Test
  fun `device content size policy with device and state`() {
    val screenView = mock(ScreenView::class.java)
    val configuration = mock(Configuration::class.java)
    val screen = Screen().apply {
      yDimension = 500
      xDimension = 300
      pixelDensity = Density.MEDIUM
    }
    val device = buildDevice("Pixel5", buildState().apply { hardware.screen = screen })
    whenever(configuration.cachedDevice).thenReturn(device)
    whenever(configuration.deviceState).thenReturn(device.defaultState)

    whenever(screenView.configuration).thenReturn(configuration)

    val outDimension = Dimension(123, 123)

    DEVICE_CONTENT_SIZE_POLICY.measure(screenView, outDimension)

    // Not modified
    assertEquals(300, outDimension.width)
    assertEquals(500, outDimension.height)
  }

  @Test
  fun `device content size policy based on dp screen size`() {
    StudioFlags.NELE_DP_SIZED_PREVIEW.override(true)
    val screenView = mock(ScreenView::class.java)
    val configuration = mock(Configuration::class.java)
    val lowDensityScreen = Screen().apply {
      yDimension = 500
      xDimension = 300
      pixelDensity = Density.LOW
    }
    val device = buildDevice("Pixel5", buildState().apply { hardware.screen = lowDensityScreen })
    whenever(configuration.cachedDevice).thenReturn(device)
    whenever(configuration.deviceState).thenReturn(device.defaultState)

    whenever(screenView.configuration).thenReturn(configuration)

    val outDimension = Dimension(123, 123)

    DEVICE_CONTENT_SIZE_POLICY.measure(screenView, outDimension)

    // Not modified
    assertEquals(400, outDimension.width)
    assertEquals(667, outDimension.height)

    val highDensityScreen = Screen().apply {
      yDimension = 500
      xDimension = 300
      pixelDensity = Density.XXHIGH
    }
    device.defaultState.hardware.screen = highDensityScreen

    DEVICE_CONTENT_SIZE_POLICY.measure(screenView, outDimension)

    // Not modified
    assertEquals(100, outDimension.width)
    assertEquals(167, outDimension.height)
  }
}