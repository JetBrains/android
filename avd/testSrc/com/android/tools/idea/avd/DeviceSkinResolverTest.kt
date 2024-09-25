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

import com.android.tools.idea.avdmanager.SkinUtils
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class DeviceSkinResolverTest {
  @Test
  fun resolveDeviceSkinIsAbsolute() {
    // Arrange
    val deviceSkin = SDK.resolve(Path.of("platforms", "android-33", "skins", "HVGA"))
    val imageSkins = emptyList<Path>()

    // Act
    val skin = DeviceSkinResolver.resolve(deviceSkin, imageSkins, null, null)

    // Assert
    assertEquals(deviceSkin, skin)
  }

  @Test
  fun resolveDeviceSkinEqualsEmptyPath() {
    // Arrange
    val deviceSkin = Path.of("")
    val imageSkins = emptyList<Path>()

    // Act
    val skin = DeviceSkinResolver.resolve(deviceSkin, imageSkins, null, null)

    // Assert
    assertEquals(deviceSkin, skin)
  }

  @Test
  fun resolveDeviceSkinEqualsNoSkin() {
    // Arrange
    val deviceSkin = SkinUtils.noSkin()
    val imageSkins = emptyList<Path>()

    // Act
    val skin = DeviceSkinResolver.resolve(deviceSkin, imageSkins, null, null)

    // Assert
    assertEquals(deviceSkin, skin)
  }

  @Test
  fun resolveImageSkinIsntNull() {
    // Arrange
    val imageSkin =
      SDK.resolve(
        Path.of("system-images", "android-33", "android-wear", "x86_64", "skins", "WearSmallRound")
      )

    val imageSkins = listOf(imageSkin)

    // Act
    val skin = DeviceSkinResolver.resolve(WEAR_SMALL_ROUND, imageSkins, null, null)

    // Assert
    assertEquals(imageSkin, skin)
  }

  @Test
  fun resolveSdkIsntNull() {
    // Arrange
    val imageSkins = emptyList<Path>()

    // Act
    val skin = DeviceSkinResolver.resolve(PIXEL_9_PRO, imageSkins, SDK, null)

    // Assert
    assertEquals(SDK.resolve(Path.of("skins", "pixel_9_pro")), skin)
  }

  @Test
  fun resolveDeviceArtResourcesIsntNullAndDeviceSkinFileNameEqualsWearLargeRound() {
    // Arrange
    val deviceSkin = Path.of("WearLargeRound")
    val imageSkins = emptyList<Path>()

    // Act
    val skin = DeviceSkinResolver.resolve(deviceSkin, imageSkins, null, DEVICE_ART_RESOURCES)

    // Assert
    assertEquals(DEVICE_ART_RESOURCES.resolve("wearos_large_round"), skin)
  }

  @Test
  fun resolveDeviceArtResourcesIsntNullAndDeviceSkinFileNameEqualsWearRect() {
    // Arrange
    val deviceSkin = Path.of("WearRect")
    val imageSkins = emptyList<Path>()

    // Act
    val skin = DeviceSkinResolver.resolve(deviceSkin, imageSkins, null, DEVICE_ART_RESOURCES)

    // Assert
    assertEquals(DEVICE_ART_RESOURCES.resolve("wearos_rect"), skin)
  }

  @Test
  fun resolveDeviceArtResourcesIsntNullAndDeviceSkinFileNameEqualsWearSmallRound() {
    // Arrange
    val imageSkins = emptyList<Path>()

    // Act
    val skin = DeviceSkinResolver.resolve(WEAR_SMALL_ROUND, imageSkins, null, DEVICE_ART_RESOURCES)

    // Assert
    assertEquals(DEVICE_ART_RESOURCES.resolve("wearos_small_round"), skin)
  }

  @Test
  fun resolveDeviceArtResourcesIsntNullAndDeviceSkinFileNameEqualsWearSquare() {
    // Arrange
    val deviceSkin = Path.of("WearSquare")
    val imageSkins = emptyList<Path>()

    // Act
    val skin = DeviceSkinResolver.resolve(deviceSkin, imageSkins, null, DEVICE_ART_RESOURCES)

    // Assert
    assertEquals(DEVICE_ART_RESOURCES.resolve("wearos_square"), skin)
  }

  @Test
  fun resolveDeviceArtResourcesIsntNull() {
    // Arrange
    val imageSkins = emptyList<Path>()

    // Act
    val skin = DeviceSkinResolver.resolve(PIXEL_9_PRO, imageSkins, null, DEVICE_ART_RESOURCES)

    // Assert
    assertEquals(DEVICE_ART_RESOURCES.resolve(PIXEL_9_PRO), skin)
  }

  @Test
  fun resolve() {
    // Arrange
    val imageSkins = emptyList<Path>()

    // Act
    val skin = DeviceSkinResolver.resolve(PIXEL_9_PRO, imageSkins, null, null)

    // Assert
    assertEquals(PIXEL_9_PRO, skin)
  }

  private companion object {
    private val SDK: Path = Path.of(System.getProperty("user.home"), "Android", "Sdk")
    private val WEAR_SMALL_ROUND: Path = Path.of("WearSmallRound")
    private val PIXEL_9_PRO: Path = Path.of("pixel_9_pro")

    private val DEVICE_ART_RESOURCES: Path =
      Path.of(
        System.getProperty("user.home"),
        "android-studio-2024.1.1.13-linux",
        "android-studio",
        "plugins",
        "android",
        "resources",
        "device-art-resources",
      )
  }
}
