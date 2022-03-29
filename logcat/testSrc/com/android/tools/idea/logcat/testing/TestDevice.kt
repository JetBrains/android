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
package com.android.tools.idea.logcat.testing

import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceList
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.testing.FakeAdbDeviceServices
import com.android.adblib.testing.FakeAdbLibSession
import com.android.tools.idea.logcat.devices.Device

private const val PROP_RELEASE = "ro.build.version.release"
private const val PROP_SDK = "ro.build.version.sdk"
private const val PROP_MANUFACTURER = "ro.product.manufacturer"
private const val PROP_MODEL = "ro.product.model"
private const val PROP_AVD_NAME = "ro.kernel.qemu.avd_name"

internal class TestDevice(
  val serialNumber: String,
  state: DeviceState,
  val release: String,
  val sdk: String,
  val manufacturer: String,
  val model: String,
  val avdName: String) {

  var deviceInfo = DeviceInfo(serialNumber, state.state, emptyList())
  val device = when {
    avdName.isEmpty() -> Device.createPhysical(serialNumber, state == DeviceState.ONLINE, release, sdk, manufacturer, model)
    else -> Device.createEmulator(serialNumber, state == DeviceState.ONLINE, release, sdk, avdName)
  }

  private val properties = mapOf(
    PROP_RELEASE to release,
    PROP_SDK to sdk,
    PROP_MANUFACTURER to manufacturer,
    PROP_MODEL to model,
    PROP_AVD_NAME to avdName,
  )

  // Return a new TestDevice with a different serial number
  fun withSerialNumber(serialNumber: String): TestDevice =
    TestDevice(serialNumber, deviceInfo.deviceState, release, sdk, manufacturer, model, avdName)

  // Return a new TestDevice with a different state
  fun withState(state: DeviceState): TestDevice =
    TestDevice(device.serialNumber, state, release, sdk, manufacturer, model, avdName)

  fun getProperty(name: String): String = properties[name] ?: throw IllegalArgumentException("Unknown property: $name")
}

internal fun FakeAdbDeviceServices.setupCommandsForDevice(testDevice: TestDevice) {
  configureProperties(
    testDevice,
    PROP_RELEASE,
    PROP_SDK,
    PROP_MANUFACTURER,
    PROP_MODEL,
  )
  configureProperties(
    testDevice,
    PROP_RELEASE,
    PROP_SDK,
  )
  if (testDevice.device.isEmulator) {
    configureProperties(
      testDevice,
      PROP_RELEASE,
      PROP_SDK,
      PROP_AVD_NAME,
    )
    configureProperties(testDevice, PROP_AVD_NAME)
  }
}

internal fun FakeAdbLibSession.setupInitialDevices(vararg devices: TestDevice) {
  hostServices.devicesList = DeviceList(devices.map { it.deviceInfo }, emptyList())
}

internal fun FakeAdbLibSession.setupTrackingData(vararg devices: List<TestDevice>) {
  hostServices.devicesTrackingData = devices.map { DeviceList(it.map { device -> device.deviceInfo }, emptyList()) }
}

private fun FakeAdbDeviceServices.configureProperties(device: TestDevice, vararg properties: String) {
  configureShellCommand(
    DeviceSelector.fromSerialNumber(device.serialNumber),
    properties.joinToString(" ; ") { "getprop $it" },
    properties.joinToString("\n") { device.getProperty(it) })
}
