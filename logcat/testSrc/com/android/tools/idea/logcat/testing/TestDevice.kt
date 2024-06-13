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

import com.android.adblib.DeviceState
import com.android.adblib.DeviceState.ONLINE
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.LocalEmulatorProperties
import com.android.sdklib.deviceprovisioner.testing.FakeAdbDeviceProvisionerPlugin
import com.android.sdklib.deviceprovisioner.testing.FakeAdbDeviceProvisionerPlugin.FakeDeviceHandle
import com.android.sdklib.devices.Abi
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.tools.idea.logcat.devices.Device
import icons.StudioIcons
import java.nio.file.Path

internal class TestDevice(
  val serialNumber: String,
  private val state: DeviceState,
  private val release: String,
  private val sdk: Int,
  private val manufacturer: String = "",
  private val model: String = "",
  private val avdName: String = "",
  private val type: DeviceType = DeviceType.HANDHELD,
) {

  val device =
    when {
      serialNumber.isEmulatorSerial() ->
        Device.createEmulator(serialNumber, state == ONLINE, release, sdk, avdName, sdk, type)
      else ->
        Device.createPhysical(
          serialNumber,
          state == ONLINE,
          release,
          sdk,
          manufacturer,
          model,
          sdk,
          type,
        )
    }
  private val deviceProperties =
    when {
      serialNumber.isEmulatorSerial() ->
        LocalEmulatorProperties.build(
          makeAvdInfo(avdName, manufacturer, model, AndroidVersion(sdk))
        ) {
          icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
          deviceType = type
          populateDeviceInfoProto("Test", null, emptyMap(), "connectionId")
        }
      else ->
        DeviceProperties.buildForTest {
          manufacturer = this@TestDevice.manufacturer
          model = this@TestDevice.model
          androidRelease = release
          androidVersion = AndroidVersion(sdk)
          icon = StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
          deviceType = type
        }
    }

  // Return a new TestDevice with a different serial number
  fun withSerialNumber(serialNumber: String): TestDevice =
    TestDevice(serialNumber, state, release, sdk, manufacturer, model, avdName)

  // Return a new TestDevice with a different state
  fun withState(state: DeviceState): TestDevice =
    TestDevice(device.serialNumber, state, release, sdk, manufacturer, model, avdName)

  suspend fun addDevice(plugin: FakeAdbDeviceProvisionerPlugin): FakeDeviceHandle {
    val handle = plugin.newDevice(serialNumber, deviceProperties)
    plugin.addDevice(handle)
    if (state == ONLINE) {
      handle.activationAction.activate()
      yieldUntil { handle.state.connectedDevice != null }
    }
    return handle
  }
}

// Emulator can have a blank serial when it's offline
private fun String.isEmulatorSerial() = startsWith("emulator") || isBlank()

private fun makeAvdInfo(
  avdName: String,
  manufacturer: String,
  model: String,
  androidVersion: AndroidVersion,
): AvdInfo {
  val basePath = Path.of("/tmp/fake_avds/$avdName")
  return AvdInfo(
    avdName,
    basePath.resolve("config.ini"),
    basePath,
    null,
    mapOf(
      AvdManager.AVD_INI_DEVICE_MANUFACTURER to manufacturer,
      AvdManager.AVD_INI_DEVICE_NAME to model,
      AvdManager.AVD_INI_ANDROID_API to androidVersion.apiStringWithoutExtension,
      AvdManager.AVD_INI_ABI_TYPE to Abi.ARM64_V8A.toString(),
      AvdManager.AVD_INI_DISPLAY_NAME to avdName,
    ),
    null,
    AvdInfo.AvdStatus.OK,
  )
}
