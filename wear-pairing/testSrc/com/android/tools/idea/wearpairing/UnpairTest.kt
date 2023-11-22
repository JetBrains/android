/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.sdklib.ISystemImage
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.ignore.IgnoreWithCondition
import com.android.testutils.ignore.OnWindows
import com.google.common.util.concurrent.Futures
import com.intellij.testFramework.ApplicationRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.nio.file.Paths

private fun IDevice.addExecuteShellCommandReply(requestHandler: (request: String) -> String) {
  whenever(executeShellCommand(Mockito.anyString(), Mockito.any())).thenAnswer { invocation ->
    val request = invocation.arguments[0] as String
    val receiver = invocation.arguments[1] as IShellOutputReceiver
    val reply = requestHandler(request)

    val byteArray = "$reply\n".toByteArray(Charsets.UTF_8)
    receiver.addOutput(byteArray, 0, byteArray.size)
  }
}

private fun PairingDevice.buildIDevice(
  avdInfo: AvdInfo? = null,
  systemProperties: Map<String, String> = emptyMap(),
  shellCommandHandler: (String) -> (String) = { throw IllegalStateException("Unknown ADB request $it") }
): IDevice {
  return Mockito.mock(IDevice::class.java).apply {
    whenever(arePropertiesSet()).thenReturn(true)
    whenever(isOnline).thenReturn(true)
    whenever(isEmulator).thenReturn(true)
    whenever(name).thenReturn(this@buildIDevice.deviceID)
    whenever(serialNumber).thenReturn("serialNumber")
    whenever(state).thenReturn(IDevice.DeviceState.ONLINE)
    whenever(version).thenReturn(AndroidVersion(apiLevel, null))
    whenever(getProperty("dev.bootcomplete")).thenReturn("1")
    if (avdInfo != null) {
      whenever(this.avdData).thenReturn(Futures.immediateFuture(
        AvdData(avdInfo.name,
          // The path is formatted in this way as a regression test for b/275128556
                avdInfo.dataFolderPath.resolve("..").resolve(avdInfo.dataFolderPath))))
    }
    else {
      whenever(this.avdData).thenReturn(Futures.immediateFuture(null))
    }

    systemProperties.forEach { (key, value) ->
      whenever(getSystemProperty(key)).thenReturn(Futures.immediateFuture(value))
    }

    addExecuteShellCommandReply(shellCommandHandler)
  }
}

class UnpairTest {
  @get:Rule
  val applicationRule = ApplicationRule()

  private val phoneDevice = PairingDevice(
    deviceID = "id1", displayName = "My Phone", apiLevel = 30, isWearDevice = false, isEmulator = true, hasPlayStore = true,
    state = ConnectionState.ONLINE
  )
  private val wearPropertiesMap = mapOf(AvdManager.AVD_INI_TAG_ID to "android-wear", AvdManager.AVD_INI_ANDROID_API to "28")
  private val avdWearInfo = AvdInfo("My Wear", Paths.get("ini"), Paths.get("id2"), Mockito.mock(ISystemImage::class.java),
                                    wearPropertiesMap)
  private val wearDevice = PairingDevice(
    deviceID = "id2", displayName = "Round Watch", apiLevel = 30, isEmulator = true, isWearDevice = true, hasPlayStore = true,
    state = ConnectionState.ONLINE
  )

  private fun handlePhoneAdbRequest(request: String): String? =
    when {
      request == "cat /proc/uptime" -> "500"
      request.contains("grep versionCode") -> "versionCode=${PairingFeature.MULTI_WATCH_SINGLE_PHONE_PAIRING.minVersion}"
      request.contains("grep 'cloud network id: '") -> "cloud network id: CloudID"
      request.startsWith("dumpsys activity") -> "Fake dumpsys activity"
      else -> null
    }

  private fun handleWearAdbRequest(request: String): String? =
    when {
      request == "cat /proc/uptime" -> "500"
      request == "am force-stop com.google.android.gms" -> "OK"
      request.contains("grep versionCode") -> "versionCode=${PairingFeature.REVERSE_PORT_FORWARD.minVersion}"
      request == "am broadcast -a com.google.android.gms.INITIALIZE" -> "OK"
      request.startsWith("dumpsys activity") -> "Fake dumpsys activity"
      else -> null
    }

  @IgnoreWithCondition(reason = "b/308744730", condition = OnWindows::class)
  @Test
  fun unpairPixelDevice() = runBlocking {
    var clearedCompanion = false
    val unexpectedAdbRequests = mutableListOf<String>()
    val phoneIDevice = phoneDevice.buildIDevice { request ->
      return@buildIDevice handlePhoneAdbRequest(request) ?: when {
        request.contains("settings get secure") -> PIXEL_COMPANION_APP_ID
        request == "pm clear com.google.android.apps.wear.companion" -> {
          clearedCompanion = true
          "OK"
        }

        else -> {
          unexpectedAdbRequests.add("PHONE $request")
          throw IllegalStateException("Unknown executeShellCommand request $request")
        }
      }
    }

    val wearIDevice = wearDevice.buildIDevice(avdInfo = avdWearInfo,
                                              systemProperties = mapOf("ro.oem.companion_package" to "")) { request ->
      return@buildIDevice handleWearAdbRequest(request) ?: when {
        request.contains("settings get secure") -> PIXEL_COMPANION_APP_ID
        else -> {
          unexpectedAdbRequests.add("WEAR $request")
          throw IllegalStateException("Unknown executeShellCommand request $request")
        }
      }
    }

    WearPairingManager.getInstance().setDataProviders({ listOf(avdWearInfo) }, { listOf(phoneIDevice, wearIDevice) })
    WearPairingManager.getInstance().createPairedDeviceBridge(phoneDevice, phoneIDevice, wearDevice, wearIDevice)
    val phoneWearPair = WearPairingManager.getInstance().getPairsForDevice(wearIDevice.name)
    WearPairingManager.getInstance().removePairedDevices(phoneWearPair.single())
    assertTrue("Companion app must be cleared in Pixel devices", clearedCompanion)
    assertTrue(
      "Unexpected ADB requests\n${unexpectedAdbRequests.joinToString("\n")}",
      unexpectedAdbRequests.isEmpty())
  }

  @Test
  fun unpairNonPixelDevice() = runBlocking {
    val unexpectedAdbRequests = mutableListOf<String>()
    val phoneIDevice = phoneDevice.buildIDevice { request ->
      return@buildIDevice handlePhoneAdbRequest(request) ?: run {
        unexpectedAdbRequests.add("PHONE $request")
        throw IllegalStateException("Unknown executeShellCommand request $request")
      }
    }

    val wearIDevice = wearDevice.buildIDevice(avdInfo = avdWearInfo,
                                              systemProperties = mapOf("ro.oem.companion_package" to "")) { request ->
      return@buildIDevice handleWearAdbRequest(request) ?: when {
        request.contains("settings get secure") -> ""
        else -> {
          unexpectedAdbRequests.add("WEAR $request")
          throw IllegalStateException("Unknown executeShellCommand request $request")
        }
      }
    }

    WearPairingManager.getInstance().setDataProviders({ listOf(avdWearInfo) }, { listOf(phoneIDevice, wearIDevice) })
    WearPairingManager.getInstance().createPairedDeviceBridge(phoneDevice, phoneIDevice, wearDevice, wearIDevice)
    val phoneWearPair = WearPairingManager.getInstance().getPairsForDevice(wearIDevice.name)
    WearPairingManager.getInstance().removePairedDevices(phoneWearPair.single())
    assertTrue(
      "Unexpected ADB requests\n${unexpectedAdbRequests.joinToString("\n")}",
      unexpectedAdbRequests.isEmpty())
  }
}