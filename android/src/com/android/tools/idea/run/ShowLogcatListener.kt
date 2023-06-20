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
package com.android.tools.idea.run

import com.android.ddmlib.IDevice
import com.android.tools.idea.concurrency.getDoneOrNull
import com.android.tools.idea.run.ShowLogcatListener.DeviceInfo.EmulatorDeviceInfo
import com.intellij.util.messages.Topic
import org.jetbrains.android.util.AndroidBundle

/**
 * Listener of events requesting that Logcat panels for a specific device be shown.
 */
fun interface ShowLogcatListener {
  fun showLogcat(deviceInfo: DeviceInfo, applicationId: String?)

  fun showLogcat(device: IDevice, applicationId: String?) {
    showLogcat(device.toDeviceInfo(), applicationId)
  }

  sealed class DeviceInfo(val id: String, val serialNumber: String) {
    class PhysicalDeviceInfo(
      serialNumber: String,
      val release: String,
      val sdk: Int,
      val manufacturer: String,
      val model: String,
    ) : DeviceInfo(serialNumber, serialNumber)

    class EmulatorDeviceInfo(
      serialNumber: String,
      val release: String,
      val sdk: Int,
      val avdName: String,
    ) : DeviceInfo(avdName, serialNumber)
  }

  companion object {
    @JvmField
    val TOPIC = Topic("Command to show logcat panel", ShowLogcatListener::class.java)

    private fun IDevice.toDeviceInfo(): DeviceInfo {
      val release = getProperty(IDevice.PROP_BUILD_VERSION) ?: AndroidBundle.message("android.launch.task.show.logcat.unknown.version")
      val sdk = getProperty(IDevice.PROP_BUILD_API_LEVEL)?.toIntOrNull() ?: 0
      return if (serialNumber.startsWith("emulator-")) {
        val avdName = avdData.getDoneOrNull()?.name ?: AndroidBundle.message("android.launch.task.show.logcat.unknown.avd")
        EmulatorDeviceInfo(serialNumber, release, sdk, avdName)
      }
      else {
        val manufacturer =
          getProperty(IDevice.PROP_DEVICE_MANUFACTURER) ?: AndroidBundle.message("android.launch.task.show.logcat.unknown.manufacturer")
        val model = getProperty(IDevice.PROP_DEVICE_MODEL) ?: AndroidBundle.message("android.launch.task.show.logcat.unknown.model")
        DeviceInfo.PhysicalDeviceInfo(serialNumber, release, sdk, manufacturer, model)
      }
    }

    @JvmStatic
    fun getShowLogcatLinkText(device: IDevice): String {
      val serial = device.serialNumber
      val name = if (device.isEmulator) {
        AndroidBundle.message("android.launch.task.show.logcat.emulator", device.avdData.getDoneOrNull()?.name?.replace("_", " ") ?: serial)
      }
      else {
        "${device.getProperty(IDevice.PROP_DEVICE_MANUFACTURER)} ${device.getProperty(IDevice.PROP_DEVICE_MODEL)} ($serial)"
      }
      return AndroidBundle.message("android.launch.task.show.logcat", name)
    }
  }
}