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
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdNetworkLatency
import com.android.sdklib.internal.avd.AvdNetworkSpeed
import com.android.sdklib.internal.avd.ConfigKey
import com.android.sdklib.internal.avd.GpuMode
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.targets.SystemImageManager
import com.android.testutils.NoErrorsOrWarningsLogger
import com.android.tools.idea.avd.StorageCapacity.Unit
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class VirtualDevicesTest {
  private val connection = mock<AvdManagerConnection>()

  @Test
  fun addAutomotiveDevice() {
    val deviceManager =
      DeviceManager.createInstance(mock<AndroidSdkHandler>(), NoErrorsOrWarningsLogger())
    val allDevices = deviceManager.getDevices(DeviceManager.ALL_DEVICES).toList()
    val autoDevice = allDevices.first { it.id == "automotive_1080p_landscape" }

    whenever(connection.avdExists(any())).thenReturn(false)

    VirtualDevices(
        connection,
        mockSystemImageManager("system-images;android-33;android-automotive;x86_64"),
      )
      .add(
        autoDevice.toVirtualDeviceProfile(setOf(AndroidVersion(34))).toVirtualDevice(),
        mockSystemImage("system-images;android-33;android-automotive;x86_64"),
      )

    val hardwarePropertiesCaptor = argumentCaptor<Map<String, String>>()
    verify(connection)
      .createOrUpdateAvd(
        /* currentInfo = */ isNull(),
        /* avdName = */ eq("Automotive_1080p_landscape_"),
        /* device = */ eq(autoDevice),
        /* systemImageDescription = */ any(),
        /* orientation = */ eq(ScreenOrientation.PORTRAIT), // TODO: This seems wrong
        /* isCircular = */ eq(false),
        /* sdCard = */ isNull(),
        /* skinFolder = */ any(),
        /* hardwareProperties = */ hardwarePropertiesCaptor.capture(),
        /* userSettings = */ isNull(),
        /* removePrevious = */ eq(true),
      )

    assertThat(hardwarePropertiesCaptor.lastValue).containsKey(ConfigKey.CLUSTER_WIDTH)
  }

  @Test
  fun addGraphicAccelerationEqualsOff() {
    // Arrange
    val devices =
      VirtualDevices(
        connection,
        mockSystemImageManager("system-images;android-31;google_apis;x86_64"),
        getHardwareProperties = { _ -> emptyMap() },
      )

    val device =
      VirtualDevice(
        "Pixel 6",
        mock<Device>(),
        AndroidVersion(31),
        NoSkin.INSTANCE,
        AvdCamera.EMULATED,
        AvdCamera.VIRTUAL_SCENE,
        AvdNetworkSpeed.FULL,
        AvdNetworkLatency.NONE,
        ScreenOrientation.PORTRAIT,
        Boot.QUICK,
        StorageCapacity(2_048, Unit.MB),
        Custom(StorageCapacity(512, Unit.MB)),
        4,
        GpuMode.OFF,
        StorageCapacity(2_048, Unit.MB),
        StorageCapacity(256, Unit.MB),
      )

    val image = mockSystemImage("system-images;android-31;google_apis;x86_64")

    // Act
    devices.add(device, image)

    // Assert
    verify(connection)
      .createOrUpdateAvd(
        anyOrNull(),
        any(),
        any(),
        any(),
        any(),
        any(),
        anyOrNull(),
        any(),
        argThat { properties -> properties["hw.gpu.enabled"] == "no" },
        anyOrNull(),
        any(),
      )
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
