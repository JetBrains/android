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
package com.android.tools.idea.run

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FakeAndroidDeviceTest {
  @Test
  fun `test emulator with no avd name`() {
    val emulatorWithNoAvdName = createMockRunningEmulator(name = null)
    assertThat(FakeAndroidDevice(emulatorWithNoAvdName).name).isEqualTo("Google Pixel [local:5554]")
  }

  @Test
  fun `test emulator with avd name`() {
    val emulatorWithNoAvdName = createMockRunningEmulator(name = "My Pixel")
    assertThat(FakeAndroidDevice(emulatorWithNoAvdName).name).isEqualTo("Google Pixel [My Pixel]")
  }

  @Test
  fun `test resizable running emulator`() {
    val emulatorWithNoAvdName = createMockRunningEmulator(name = "resizable", version = AndroidVersion(34, null))
    assertThat(
      FakeAndroidDevice(emulatorWithNoAvdName).supportsMultipleScreenFormats()).isEqualTo(true)
  }

  companion object {
    private fun createMockRunningEmulator(
      name: String?,
      version: AndroidVersion = AndroidVersion(28, null)
    ): IDevice {
      val device = mock<IDevice>()
      whenever(device.isEmulator).thenReturn(true)
      whenever(device.avdName).thenReturn(name)
      whenever(device.serialNumber).thenReturn("local:5554")
      whenever(device.getProperty(IDevice.PROP_DEVICE_MANUFACTURER)).thenReturn("Google")
      whenever(device.getProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn("Pixel")
      whenever(device.getProperty(IDevice.PROP_DEVICE_BOOT_QEMU_DISPLAY_NAME)).thenReturn(name)
      whenever(device.version).thenReturn(version)
      return device
    }
  }
}