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
package com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection

import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.toDeviceDescriptor
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.layoutinspector.pipeline.fakeDevice
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeviceModelTest {

  @get:Rule val disposableRule = DisposableRule()

  private lateinit var processModel: ProcessesModel

  private val fakeProcess =
    object : ProcessDescriptor {
      override val device = fakeDevice().toDeviceDescriptor()
      override val abiCpuArch = "fake_arch"
      override val name = "fake_process"
      override val packageName = name
      override val isRunning = true
      override val pid = 1
      override val streamId = 1L
    }

  @Before
  fun setUp() {
    val testProcessDiscovery = TestProcessDiscovery()
    testProcessDiscovery.addDevice(fakeDevice().toDeviceDescriptor())
    processModel = ProcessesModel(testProcessDiscovery)
  }

  @Test
  fun testSettingSelectedDeviceResetsSelectedProcess() {
    val deviceModel = DeviceModel(disposableRule.disposable, processModel)
    processModel.selectedProcess = fakeProcess

    deviceModel.setSelectedDevice(fakeDevice().toDeviceDescriptor())

    assertThat(processModel.selectedProcess).isEqualTo(fakeProcess)

    deviceModel.setSelectedDevice(null)

    assertThat(processModel.selectedProcess).isNull()
  }

  @Test
  fun testListenersAreInvokedWhenSelectedDeviceChanges() {
    val deviceModel = DeviceModel(disposableRule.disposable, processModel)
    var newDevice: DeviceDescriptor? = null

    deviceModel.newSelectedDeviceListeners.add { newDevice = it }

    deviceModel.setSelectedDevice(fakeDevice().toDeviceDescriptor())

    assertThat(newDevice).isEqualTo(fakeDevice().toDeviceDescriptor())
  }

  @Test
  fun testDeviceModelsRemoveThemselvesWhenDisposed() {
    val deviceModel1 = DeviceModel(disposableRule.disposable, processModel)
    assertThat(ForegroundProcessDetectionImpl.deviceModels).containsExactly(deviceModel1)

    val deviceModel2 = DeviceModel(disposableRule.disposable, processModel)
    assertThat(ForegroundProcessDetectionImpl.deviceModels)
      .containsExactly(deviceModel1, deviceModel2)

    Disposer.dispose(deviceModel2)
    assertThat(ForegroundProcessDetectionImpl.deviceModels).containsExactly(deviceModel1)

    Disposer.dispose(deviceModel1)
    assertThat(ForegroundProcessDetectionImpl.deviceModels).isEmpty()
  }

  @Test
  fun testForcedDeviceIsEnforced() {
    val deviceModel = DeviceModel(disposableRule.disposable, processModel)
    val deviceDescriptor = fakeDevice().toDeviceDescriptor()

    deviceModel.forcedDeviceSerialNumber = "wrong serial number"
    deviceModel.setSelectedDevice(deviceDescriptor)
    assertThat(deviceModel.selectedDevice).isNull()

    deviceModel.forcedDeviceSerialNumber = deviceDescriptor.serial
    deviceModel.setSelectedDevice(deviceDescriptor)
    assertThat(deviceModel.selectedDevice).isEqualTo(deviceDescriptor)

    deviceModel.setSelectedDevice(null)
    deviceModel.forcedDeviceSerialNumber = null
    deviceModel.setSelectedDevice(deviceDescriptor)
    assertThat(deviceModel.selectedDevice).isEqualTo(deviceDescriptor)
  }

  @Test
  fun testUnsupportedDeviceIsNotSelected() {
    val deviceModel = DeviceModel(disposableRule.disposable, processModel)
    val deviceDescriptor = fakeDevice(apiLevel = 1).toDeviceDescriptor()

    deviceModel.setSelectedDevice(deviceDescriptor)
    assertThat(deviceModel.selectedDevice).isNull()
  }
}
