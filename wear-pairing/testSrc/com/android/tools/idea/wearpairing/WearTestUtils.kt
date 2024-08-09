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
package com.android.tools.idea.wearpairing

import com.android.ddmlib.AvdData
import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.sdklib.AndroidVersion
import com.android.sdklib.internal.avd.AvdInfo
import com.android.testutils.MockitoKt
import com.google.common.util.concurrent.Futures
import org.mockito.Mockito

private fun IDevice.addExecuteShellCommandReply(requestHandler: (request: String) -> String) {
  MockitoKt.whenever(executeShellCommand(Mockito.anyString(), Mockito.any())).thenAnswer {
    invocation ->
    val request = invocation.arguments[0] as String
    val receiver = invocation.arguments[1] as IShellOutputReceiver
    val reply = requestHandler(request)

    val byteArray = "$reply\n".toByteArray(Charsets.UTF_8)
    receiver.addOutput(byteArray, 0, byteArray.size)
  }
}

internal fun PairingDevice.buildIDevice(
  avdInfo: AvdInfo? = null,
  systemProperties: Map<String, String> = emptyMap(),
  properties: Map<String, String> = emptyMap(),
  shellCommandHandler: (String) -> (String) = {
    throw IllegalStateException("Unknown ADB request $it")
  },
): IDevice {
  return Mockito.mock(IDevice::class.java).apply {
    MockitoKt.whenever(isOnline).thenReturn(true)
    MockitoKt.whenever(isEmulator).thenReturn(this@buildIDevice.isEmulator)
    MockitoKt.whenever(name).thenReturn(displayName)
    MockitoKt.whenever(serialNumber).thenReturn("serialNumber")
    MockitoKt.whenever(state).thenReturn(IDevice.DeviceState.ONLINE)
    MockitoKt.whenever(version).thenReturn(AndroidVersion(apiLevel, null))
    MockitoKt.whenever(getProperty(MockitoKt.any())).thenAnswer {
      properties[it.arguments.single() as String]
    }
    MockitoKt.whenever(this.avdData).thenAnswer {
      if (avdInfo != null) {
        Futures.immediateFuture(
          AvdData(
            avdInfo.name,
            // The path is formatted in this way as a regression test for b/275128556
            avdInfo.dataFolderPath.resolve("..").resolve(avdInfo.dataFolderPath),
          )
        )
      } else {
        Futures.immediateFuture(null)
      }
    }
    MockitoKt.whenever(getSystemProperty(MockitoKt.any())).thenAnswer {
      systemProperties[it.arguments.single()]
    }

    addExecuteShellCommandReply(shellCommandHandler)
  }
}

/** Method that simulates the handling of ADB requests by a phone device. */
internal fun handlePhoneAdbRequest(request: String): String? =
  when {
    request == "cat /proc/uptime" -> "500"
    request.contains("grep versionCode") ->
      "versionCode=${PairingFeature.MULTI_WATCH_SINGLE_PHONE_PAIRING.minVersion}"
    request.contains("grep 'cloud network id: '") -> "cloud network id: CloudID"
    request.startsWith("dumpsys activity") -> "Fake dumpsys activity"
    else -> null
  }

/** Method that simulates the handling of ADB requests by a Wear device. */
internal fun handleWearAdbRequest(request: String): String? =
  when {
    request == "cat /proc/uptime" -> "500"
    request == "am force-stop com.google.android.gms" -> "OK"
    request.contains("grep versionCode") ->
      "versionCode=${PairingFeature.REVERSE_PORT_FORWARD.minVersion}"
    request == "am broadcast -a com.google.android.gms.INITIALIZE" -> "OK"
    request.startsWith("dumpsys activity") -> "Fake dumpsys activity"
    else -> null
  }
