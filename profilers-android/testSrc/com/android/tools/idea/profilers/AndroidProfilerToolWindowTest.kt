/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.android.ddmlib.IDevice
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AndroidProfilerToolWindowTest {

  @Test
  fun testDeviceDisplayName() {
    val device =
      mock<IDevice> {
        whenever(it.serialNumber).thenReturn("Serial")
        whenever(it.getProperty(IDevice.PROP_DEVICE_MANUFACTURER)).thenReturn("Manufacturer")
        whenever(it.getProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn("Model")
      }
    assertThat(AndroidProfilerToolWindow.getDeviceDisplayName(device)).isEqualTo("Manufacturer Model")

    val deviceWithEmptyManufacturer =
      mock<IDevice> {
        whenever(it.serialNumber).thenReturn("Serial")
        whenever(it.getProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn("Model")
      }
    assertThat(AndroidProfilerToolWindow.getDeviceDisplayName(deviceWithEmptyManufacturer)).isEqualTo("Model")

    val deviceWithSerialInModel =
      mock<IDevice> {
        whenever(it.serialNumber).thenReturn("Serial")
        whenever(it.getProperty(IDevice.PROP_DEVICE_MANUFACTURER)).thenReturn("Manufacturer")
        whenever(it.getProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn("Model-Serial")
      }
    assertThat(AndroidProfilerToolWindow.getDeviceDisplayName(deviceWithSerialInModel)).isEqualTo("Manufacturer Model")
  }
}
