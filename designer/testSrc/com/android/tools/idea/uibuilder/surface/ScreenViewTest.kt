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

import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.rendering.RenderLogger
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.ScreenView.DEVICE_CONTENT_SIZE_POLICY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.awt.Dimension

class ScreenViewTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

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
    val renderLogger = RenderLogger("", projectRule.module, null)
    // Fully simulate an error to make the result invalid
    renderLogger.addBrokenClass("Broken", Throwable())
    val file = projectRule.fixture.addFileToProject("src/EmptyFile.kt", "")
    val result = RenderResult.createRenderTaskErrorResult(file, renderLogger)

    `when`(sceneManager.renderResult).thenReturn(result)
    `when`(screenView.sceneManager).thenReturn(sceneManager)

    assertFalse(DEVICE_CONTENT_SIZE_POLICY.hasContentSize(screenView))
  }

  @Test
  fun `device content size policy with no device returns no size`() {
    val screenView = mock(ScreenView::class.java)
    val configuration = mock(Configuration::class.java)
    `when`(configuration.cachedDevice).thenReturn(null)
    `when`(configuration.deviceState).thenReturn(null)

    `when`(screenView.configuration).thenReturn(configuration)

    val outDimension = Dimension(123, 123)

    DEVICE_CONTENT_SIZE_POLICY.measure(screenView, outDimension)

    // Not modified
    assertEquals("measure should not modify the dimensions where there is no device available", 123, outDimension.width)
    assertEquals("measure should not modify the dimensions where there is no device available", 123, outDimension.height)

    `when`(configuration.cachedDevice).thenReturn(buildDevice("Pixel5"))
    DEVICE_CONTENT_SIZE_POLICY.measure(screenView, outDimension)
    // Not modified
    assertEquals("measure should not modify the dimensions where there is no state available", 123, outDimension.width)
    assertEquals("measure should not modify the dimensions where there is no state available", 123, outDimension.height)
  }

  @Test
  fun `device content size policy with device and state`() {
    val screenView = mock(ScreenView::class.java)
    val configuration = mock(Configuration::class.java)
    val device = buildDevice("Pixel5")
    `when`(configuration.cachedDevice).thenReturn(device)
    `when`(configuration.deviceState).thenReturn(device.defaultState)

    `when`(screenView.configuration).thenReturn(configuration)

    val outDimension = Dimension(123, 123)

    DEVICE_CONTENT_SIZE_POLICY.measure(screenView, outDimension)

    // Not modified
    assertEquals(300, outDimension.width)
    assertEquals(500, outDimension.height)
  }
}