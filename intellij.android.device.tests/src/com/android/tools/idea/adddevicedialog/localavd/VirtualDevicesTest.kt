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
package com.android.tools.idea.adddevicedialog.localavd

import com.android.repository.api.RepoPackage
import com.android.resources.ScreenOrientation
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.targets.SystemImageManager
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argumentCaptor
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.NoErrorsOrWarningsLogger
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.verify

class VirtualDevicesTest {
  @Test
  fun addAutomotiveDevice() {
    val deviceManager =
      DeviceManager.createInstance(mock<AndroidSdkHandler>(), NoErrorsOrWarningsLogger())
    val avdManagerConnection = mock<AvdManagerConnection>()
    val allDevices = deviceManager.getDevices(DeviceManager.ALL_DEVICES).toList()
    val autoDevice = allDevices.first { it.id == "automotive_1080p_landscape" }

    whenever(avdManagerConnection.avdExists(any())).thenReturn(false)

    VirtualDevices(allDevices, avdManagerConnection, mockSystemImageManager())
      .add(autoDevice.toVirtualDevice(), mockSystemImage())

    val hardwarePropertiesCaptor = argumentCaptor<Map<String, String>>()
    verify(avdManagerConnection)
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

    assertThat(hardwarePropertiesCaptor.value).containsKey(AvdManager.AVD_INI_CLUSTER_WIDTH)
  }

  private companion object {
    private fun mockSystemImageManager(): SystemImageManager {
      val repoPackage = mock<RepoPackage>()
      whenever(repoPackage.path).thenReturn("system-images;android-33;android-automotive;x86_64")

      val sdklibImage = mock<com.android.sdklib.repository.targets.SystemImage>()
      whenever(sdklibImage.`package`).thenReturn(repoPackage)

      val manager = mock<SystemImageManager>()
      whenever(manager.images).thenReturn(listOf(sdklibImage))

      return manager
    }

    private fun mockSystemImage(): SystemImage {
      val image = mock<SystemImage>()
      whenever(image.path).thenReturn("system-images;android-33;android-automotive;x86_64")

      return image
    }
  }
}
