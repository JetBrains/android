/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.toDeviceDescriptor
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceModelTest {

  @Test
  fun testSettingSelectedDeviceResetsSelectedProcess() {
    val testProcessDiscovery = TestProcessDiscovery()
    testProcessDiscovery.addDevice(FakeTransportService.FAKE_DEVICE.toDeviceDescriptor())
    val processModel = ProcessesModel(testProcessDiscovery)
    val deviceModel = DeviceModel(processModel)

    val fakeProcess = object : ProcessDescriptor {
      override val device = FakeTransportService.FAKE_DEVICE.toDeviceDescriptor()
      override val abiCpuArch = "fake_arch"
      override val name = "fake_process"
      override val packageName = name
      override val isRunning = true
      override val pid = 1
      override val streamId = 1L
    }

    processModel.selectedProcess = fakeProcess

    deviceModel.selectedDevice = FakeTransportService.FAKE_DEVICE.toDeviceDescriptor()

    assertThat(processModel.selectedProcess).isNull()
  }
}