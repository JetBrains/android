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
import java.nio.file.Path
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class ConfigureDevicePanelStateTest {
  private val device = TestDevices.pixel9Pro()

  private val skin =
    DefaultSkin(Path.of(System.getProperty("user.home"), "Android", "Sdk", "skins", "pixel_6"))

  private val skins = listOf(NoSkin.INSTANCE, skin, device.skin).toImmutableList()

  @Test
  fun initDeviceSkins() {
    // Arrange
    val state =
      ConfigureDevicePanelState(
        device.copy(skin = NoSkin.INSTANCE, defaultSkin = NoSkin.INSTANCE),
        skins,
        mock(),
      )

    val path = device.skin.path()

    // Act
    state.initDeviceSkins(path)

    // Assert
    assertEquals(device, state.device)
  }

  @Test
  fun setSkinNotInSkins() {
    // Arrange
    val state =
      ConfigureDevicePanelState(
        device.copy(skin = NoSkin.INSTANCE, defaultSkin = NoSkin.INSTANCE),
        skins,
        mock(),
      )

    state.initDeviceSkins(device.skin.path())
    state.device = state.device.copy(skin = skin)

    val image = mock<ISystemImage>()
    whenever(image.hasPlayStore()).thenReturn(true)

    state.setSystemImageSelection(image)

    // Act
    state.setSkin(skin.path)

    // Assert
    assertEquals(device, state.device)
  }

  @Test
  fun setSkin() {
    // Arrange
    val state =
      ConfigureDevicePanelState(
        device.copy(skin = NoSkin.INSTANCE, defaultSkin = NoSkin.INSTANCE),
        skins,
        mock(),
      )

    state.initDeviceSkins(device.skin.path())
    state.device = state.device.copy(skin = skin)
    state.setSystemImageSelection(mock())

    // Act
    state.setSkin(skin.path)

    // Assert
    assertEquals(device.copy(skin = skin), state.device)
  }

  @Test
  fun skinsHasPlayStore() {
    // Arrange
    val image = mock<ISystemImage>()
    whenever(image.hasPlayStore()).thenReturn(true)

    val state =
      ConfigureDevicePanelState(
        device.copy(skin = NoSkin.INSTANCE, defaultSkin = NoSkin.INSTANCE),
        skins,
        image,
      )

    state.initDeviceSkins(device.skin.path())

    // Act
    val skins = state.skins()

    // Assert
    assertEquals(listOf(NoSkin.INSTANCE, device.skin), skins)
  }

  @Test
  fun skins() {
    // Arrange
    val state =
      ConfigureDevicePanelState(
        device.copy(skin = NoSkin.INSTANCE, defaultSkin = NoSkin.INSTANCE),
        skins,
        mock(),
      )

    state.initDeviceSkins(device.skin.path())

    // Act
    val actualSkins = state.skins()

    // Assert
    assertEquals(skins, actualSkins)
  }
}
