/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.connection.assistant.actions

import com.android.ddmlib.AdbDevice
import com.android.ddmlib.IDevice
import com.android.tools.usb.UsbDevice
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceCrossReferenceTest {
  @Test
  fun crossReferenceTest() {
    val usbDevices = listOf(
      UsbDevice("First USB Device", "Teh vendor", "A product", "0xBAADF00D", "123456", "A device"),
      UsbDevice("Another USB Device", "Teh vendor", "A product", null, "123456"),
      UsbDevice("Third USB Device", "Teh vendor", "A product", null, "111111"),
      UsbDevice("Fourth USB Device", "Teh vendor", "A product")
    )

    val adbDevices = listOf(
      AdbDevice(null, null),
      AdbDevice("123456", IDevice.DeviceState.ONLINE),
      AdbDevice("222222", IDevice.DeviceState.UNAUTHORIZED)
    )

    val xrefs = crossReference(usbDevices, emptyList(), adbDevices)
    val summaries = xrefs.map { summarize(it) }
    assertThat(summaries.map { it.label }).containsExactly("", "222222", "123456", "Third USB Device", "Fourth USB Device")
  }
}