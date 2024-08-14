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

import com.android.repository.api.RepoPackage
import com.android.resources.ScreenOrientation
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.internal.avd.AvdBuilder
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.internal.avd.ConfigKey
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.targets.SystemImageManager
import com.android.testutils.NoErrorsOrWarningsLogger
import com.google.common.truth.Truth.assertThat
import java.nio.file.Paths
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class VirtualDevicesTest {
  private val avdManager = mock<AvdManager>()

  @Test
  fun add() {
    val deviceManager =
      DeviceManager.createInstance(mock<AndroidSdkHandler>(), NoErrorsOrWarningsLogger())
    val allDevices = deviceManager.getDevices(DeviceManager.ALL_DEVICES).toList()
    val autoDevice = allDevices.first { it.id == "automotive_1080p_landscape" }
    val systemImageManager =
      mockSystemImageManager("system-images;android-33;android-automotive;x86_64")
    val systemImage = mockSystemImage("system-images;android-33;android-automotive;x86_64")

    whenever(avdManager.createAvdBuilder(any()))
      .thenReturn(
        AvdBuilder(
          Paths.get("/tmp/avd/automotive.ini"),
          Paths.get("/tmp/avd/automotive.avd"),
          autoDevice,
        )
      )

    val virtualDevice =
      autoDevice.toVirtualDeviceProfile(setOf(AndroidVersion(33))).toVirtualDevice()

    VirtualDevices(avdManager, systemImageManager).add(virtualDevice, systemImage)

    val avdBuilderCaptor = argumentCaptor<AvdBuilder>()
    verify(avdManager).createAvd(avdBuilderCaptor.capture())

    with(avdBuilderCaptor.lastValue) {
      assertThat(avdName).isEqualTo("Automotive_1080p_landscape_")
      assertThat(device).isEqualTo(autoDevice)
      assertThat(displayName).isEqualTo(autoDevice.displayName)
      assertThat(skin).isNull()
      assertThat(screenOrientation).isEqualTo(ScreenOrientation.LANDSCAPE)
      assertThat(configProperties()).containsKey(ConfigKey.CLUSTER_WIDTH)
    }
  }

  private companion object {
    private fun mockSystemImageManager(path: String): SystemImageManager {
      val repoPackage = mock<RepoPackage>()
      whenever(repoPackage.path).thenReturn(path)

      val sdklibImage = mock<com.android.sdklib.repository.targets.SystemImage>()
      whenever(sdklibImage.`package`).thenReturn(repoPackage)

      val manager = mock<SystemImageManager>()
      whenever(manager.images).thenReturn(listOf(sdklibImage))

      return manager
    }

    private fun mockSystemImage(path: String): SystemImage {
      val image = mock<SystemImage>()
      whenever(image.path).thenReturn(path)

      return image
    }
  }
}
