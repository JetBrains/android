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
class SystemImageFilterStateTest {
  @Test
  fun systemImageFilterState() {
    // Arrange
    val images = listOf(mockSystemImage(AndroidVersion(34).withExtensionLevel(9)))

    // Act
    val state = SystemImageFilterState(API, null, showUnsupportedSystemImages = true)

    // Assert
    assertEquals(images, state.filter(images))
  }

  @Test
  fun systemImageFilterStateIsBaseExtensionAndExtensionLevelIsNull() {
    // Arrange
    val images =
      listOf(
        mockSystemImage(AndroidVersion(34, null, null, true)),
        mockSystemImage(AndroidVersion(34, null, 7, true)),
      )

    // Act
    val state = SystemImageFilterState(API, null, showUnsupportedSystemImages = true)

    // Assert
    assertEquals(images, state.filter(images))
  }

  @Test
  fun systemImageFilterStatePreview() {
    // Arrange
    val images =
      listOf(
        mockSystemImage(AndroidVersion(35, "Baklava", 15, true)),
        mockSystemImage(AndroidVersion(35, "Baklava", 16, true)),
      )

    // Act
    val state =
      SystemImageFilterState(
        AndroidVersionSelection(AndroidVersion(35, "Baklava")),
        null,
        showUnsupportedSystemImages = true,
      )

    // Assert
    assertEquals(images, state.filter(images))
  }

  @Test
  fun systemImageFilterStateApiLevelsAreEqual() {
    // Arrange
    val image = mockSystemImage(AndroidVersion(34).withBaseExtensionLevel())
    val images = listOf(mockSystemImage(AndroidVersion(34).withExtensionLevel(8)), image)

    // Act
    val state = SystemImageFilterState(API, null, showUnsupportedSystemImages = true)

    // Assert
    assertEquals(listOf(image), state.filter(images))
  }

  @Test
  fun systemImageFilterStateApiLevelsArentEqual() {
    // Arrange
    val image = mockSystemImage(AndroidVersion(34).withExtensionLevel(9))
    val images = listOf(mockSystemImage(AndroidVersion(33).withBaseExtensionLevel()), image)

    // Act
    val state = SystemImageFilterState(API, null, showUnsupportedSystemImages = true)

    // Assert
    assertEquals(listOf(image), state.filter(images))
  }

  private companion object {
    private val API = AndroidVersionSelection(AndroidVersion(34).withBaseExtensionLevel())

    private fun mockSystemImage(version: AndroidVersion): ISystemImage {
      val image = mock<ISystemImage>()
      whenever(image.androidVersion).thenReturn(version)

      return image
    }
  }
}
