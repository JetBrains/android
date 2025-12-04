/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.devicemanagerv2

import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.devices.Abi
import com.google.common.truth.Truth.assertThat
import icons.StudioIcons
import org.junit.Test
import org.mockito.kotlin.mock

class DeviceTableColumnsTest {

  @Test
  fun formFactor() {
    val phone = sampleRow()
    val headset = sampleRow().copy(type = DeviceType.XR_HEADSET)
    val aiGlasses = sampleRow().copy(type = DeviceType.AI_GLASSES)

    assertThat(DeviceTableColumns.formFactorAttribute.value(phone)).isEqualTo("Phone and Tablet")
    assertThat(DeviceTableColumns.formFactorAttribute.value(headset)).isEqualTo("XR")
    assertThat(DeviceTableColumns.formFactorAttribute.value(aiGlasses)).isEqualTo("XR")
  }

  /** An arbitrary DeviceRowData that can easily be customized with copy(). */
  private fun sampleRow() =
    DeviceRowData(
      template = null,
      handle = mock<DeviceHandle>(),
      name = "Pixel 6",
      type = DeviceType.HANDHELD,
      icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE,
      androidVersion = AndroidVersion(31),
      abi = Abi.ARM64_V8A,
      status = DeviceRowData.Status.ONLINE,
      error = null,
      handleType = DeviceRowData.HandleType.PHYSICAL,
      wearPairingId = "abcd1234",
      pairingStatus = emptyList(),
    )
}
