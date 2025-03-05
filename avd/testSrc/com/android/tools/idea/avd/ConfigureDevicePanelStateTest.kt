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

import com.android.sdklib.ISystemImage
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.google.common.truth.Truth.assertThat
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.math.max
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class ConfigureDevicePanelStateTest {
  private val skin =
    DefaultSkin(Path.of(System.getProperty("user.home"), "Android", "Sdk", "skins", "pixel_6"))

  @Test
  fun initDeviceSkins() {
    // Arrange
    val device = TestDevices.pixel9Pro()
    val pixel9Skin = device.skin
    val skins = listOf(NoSkin.INSTANCE, skin, device.skin).toImmutableList()
    device.setNoSkin()

    val state = configureDevicePanelState(device, skins)

    // Act
    state.initDeviceSkins(pixel9Skin.path())

    // Assert
    assertThat(device.skin).isEqualTo(pixel9Skin)
    assertThat(device.defaultSkin).isEqualTo(pixel9Skin)
  }

  @Test
  fun setSkinNotInSkins() {
    // Arrange
    val device = TestDevices.pixel9Pro()
    val pixel9Skin = device.skin
    val skins = listOf(NoSkin.INSTANCE, skin, device.skin).toImmutableList()

    val state = configureDevicePanelState(device, skins)

    state.initDeviceSkins(pixel9Skin.path())
    state.device.skin = skin

    val image = mock<ISystemImage>()
    whenever(image.hasPlayStore()).thenReturn(true)

    state.setSystemImageSelection(image)

    // Act
    state.setSkin(skin.path)

    // Assert
    assertThat(state.device.skin).isEqualTo(pixel9Skin)
  }

  @Test
  fun setSkin() {
    // Arrange
    val device = TestDevices.pixel9Pro()
    val skins = listOf(NoSkin.INSTANCE, skin, device.skin).toImmutableList()
    device.setNoSkin()

    val state = configureDevicePanelState(device, skins)

    state.initDeviceSkins(device.skin.path())
    state.device.skin = skin
    state.setSystemImageSelection(mock())

    // Act
    state.setSkin(skin.path)

    // Assert
    assertThat(state.device.skin).isEqualTo(skin)
  }

  @Test
  fun skinsHasPlayStore() {
    // Arrange
    val device = TestDevices.pixel9Pro()
    val skins = listOf(NoSkin.INSTANCE, skin, device.skin).toImmutableList()
    device.setNoSkin()

    val image = mock<ISystemImage>()
    whenever(image.hasPlayStore()).thenReturn(true)

    val state = configureDevicePanelState(device, skins, image)

    state.initDeviceSkins(device.skin.path())

    // Act
    val actualSkins = state.skins()

    // Assert
    assertEquals(setOf(NoSkin.INSTANCE, device.skin), actualSkins)
  }

  @Test
  fun skinsDefaultSkinEqualsNoSkin() {
    // Arrange
    val device = TestDevices.mediumPhone()

    val image = mock<ISystemImage>()
    whenever(image.hasPlayStore()).thenReturn(true)

    val state = configureDevicePanelState(device, listOf(NoSkin.INSTANCE).toImmutableList(), image)
    state.initDeviceSkins(device.skin.path())

    // Act
    val actualSkins = state.skins()

    // Assert
    assertEquals(setOf(NoSkin.INSTANCE), actualSkins)
  }

  @Test
  fun skins() {
    // Arrange
    val device = TestDevices.pixel9Pro()
    val skins = listOf(NoSkin.INSTANCE, skin, device.skin).toImmutableList()
    device.setNoSkin()

    val state = configureDevicePanelState(device, skins)

    state.initDeviceSkins(device.skin.path())

    // Act
    val actualSkins = state.skins()

    // Assert
    assertEquals(skins, actualSkins)
  }

  @Test
  fun resetPlayStoreFields() {
    // Arrange
    val device = TestDevices.pixel9Pro()

    val image = mock<ISystemImage>()
    whenever(image.hasPlayStore()).thenReturn(true)

    val state =
      configureDevicePanelState(
        device,
        listOf(NoSkin.INSTANCE, device.defaultSkin).toImmutableList(),
        image,
      )

    state.initDeviceSkins(device.defaultSkin.path())
    state.device.skin = NoSkin.INSTANCE

    // Act
    state.resetPlayStoreFields()

    // Assert
    assertEquals(NoSkin.INSTANCE, state.device.skin)
  }
}

private fun VirtualDevice.setNoSkin() {
  skin = NoSkin.INSTANCE
  defaultSkin = NoSkin.INSTANCE
}

private fun configureDevicePanelState(
  device: VirtualDevice,
  skins: ImmutableCollection<Skin> = emptyList<Skin>().toImmutableList(),
  image: ISystemImage? = mock(),
  deviceNameValidator: DeviceNameValidator = DeviceNameValidator(emptySet()),
  fileSystem: FileSystem = FileSystems.getDefault(),
  maxCpuCoreCount: Int = max(1, Runtime.getRuntime().availableProcessors() / 2),
) =
  ConfigureDevicePanelState(device, skins, image, deviceNameValidator, fileSystem, maxCpuCoreCount)
