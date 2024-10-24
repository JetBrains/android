/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.avd

import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class DevicePanelStateTest {
  @Test
  fun devicePanelState() {
    // Arrange
    val images = listOf(mockSystemImage(AndroidVersion(34, null, 9, false)))

    // Act
    val state = DevicePanelState(API, null, showUnsupportedSystemImages = true)

    // Assert
    assertEquals(images, state.filter(images))
  }

  @Test
  fun devicePanelStateIsBaseExtensionAndExtensionLevelIsNull() {
    // Arrange
    val images =
      listOf(
        mockSystemImage(AndroidVersion(34, null, null, true)),
        mockSystemImage(AndroidVersion(34, null, 7, true)),
      )

    // Act
    val state = DevicePanelState(API, null, showUnsupportedSystemImages = true)

    // Assert
    assertEquals(images, state.filter(images))
  }

  @Test
  fun devicePanelStateApiLevelsAreEqual() {
    // Arrange
    val image = mockSystemImage(AndroidVersion(34, null, 7, true))
    val images = listOf(mockSystemImage(AndroidVersion(34, null, 8, false)), image)

    // Act
    val state = DevicePanelState(API, null, showUnsupportedSystemImages = true)

    // Assert
    assertEquals(listOf(image), state.filter(images))
  }

  @Test
  fun devicePanelStateApiLevelsArentEqual() {
    // Arrange
    val image = mockSystemImage(AndroidVersion(34, null, 9, false))
    val images = listOf(mockSystemImage(AndroidVersion(33, null, 3, true)), image)

    // Act
    val state = DevicePanelState(API, null, showUnsupportedSystemImages = true)

    // Assert
    assertEquals(listOf(image), state.filter(images))
  }

  private companion object {
    private val API = AndroidVersionSelection(AndroidVersion(34, null, null, true))

    private fun mockSystemImage(version: AndroidVersion): ISystemImage {
      val image = mock<ISystemImage>()
      whenever(image.androidVersion).thenReturn(version)

      return image
    }
  }
}
