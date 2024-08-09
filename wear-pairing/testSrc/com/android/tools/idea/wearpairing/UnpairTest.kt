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

import com.android.sdklib.ISystemImage
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.ConfigKey
import com.android.testutils.ignore.IgnoreTestRule
import com.intellij.testFramework.ApplicationRule
import java.nio.file.Paths
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class UnpairTest {
  @get:Rule val applicationRule = ApplicationRule()

  @get:Rule val ignoreTestsRule = IgnoreTestRule()

  private val phoneDevice =
    PairingDevice(
      deviceID = "id1",
      displayName = "My Phone",
      apiLevel = 30,
      isWearDevice = false,
      isEmulator = true,
      hasPlayStore = true,
      state = ConnectionState.ONLINE,
    )
  private val wearPropertiesMap =
    mapOf(ConfigKey.TAG_ID to "android-wear", ConfigKey.ANDROID_API to "28")
  private val avdWearInfo =
    AvdInfo(
      Paths.get("ini"),
      Paths.get("id2"),
      Mockito.mock(ISystemImage::class.java),
      wearPropertiesMap,
      null,
    )
  private val wearDevice =
    PairingDevice(
      deviceID = "id2",
      displayName = "Round Watch",
      apiLevel = 30,
      isEmulator = true,
      isWearDevice = true,
      hasPlayStore = true,
      state = ConnectionState.ONLINE,
    )

  @Ignore("b/347716312")
  @Test
  fun unpairPixelDevice() = runBlocking {
    var clearedCompanion = false
    val unexpectedAdbRequests = mutableListOf<String>()
    val phoneIDevice =
      phoneDevice.buildIDevice { request ->
        return@buildIDevice handlePhoneAdbRequest(request)
          ?: when {
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

    val wearIDevice =
      wearDevice.buildIDevice(
        avdInfo = avdWearInfo,
        systemProperties = mapOf("ro.oem.companion_package" to ""),
      ) { request ->
        return@buildIDevice handleWearAdbRequest(request)
          ?: when {
            request.contains("settings get secure") -> PIXEL_COMPANION_APP_ID
            else -> {
              unexpectedAdbRequests.add("WEAR $request")
              throw IllegalStateException("Unknown executeShellCommand request $request")
            }
          }
      }

    WearPairingManager.getInstance()
      .setDataProviders({ listOf(avdWearInfo) }, { listOf(phoneIDevice, wearIDevice) })
    WearPairingManager.getInstance()
      .createPairedDeviceBridge(phoneDevice, phoneIDevice, wearDevice, wearIDevice)
    val phoneWearPair = WearPairingManager.getInstance().getPairsForDevice(wearIDevice.name)
    WearPairingManager.getInstance().removePairedDevices(phoneWearPair.single())
    assertTrue("Companion app must be cleared in Pixel devices", clearedCompanion)
    assertTrue(
      "Unexpected ADB requests\n${unexpectedAdbRequests.joinToString("\n")}",
      unexpectedAdbRequests.isEmpty(),
    )
  }

  @Test
  fun unpairNonPixelDevice() = runBlocking {
    val unexpectedAdbRequests = mutableListOf<String>()
    val phoneIDevice =
      phoneDevice.buildIDevice { request ->
        return@buildIDevice handlePhoneAdbRequest(request)
          ?: run {
            unexpectedAdbRequests.add("PHONE $request")
            throw IllegalStateException("Unknown executeShellCommand request $request")
          }
      }

    val wearIDevice =
      wearDevice.buildIDevice(
        avdInfo = avdWearInfo,
        systemProperties = mapOf("ro.oem.companion_package" to ""),
      ) { request ->
        return@buildIDevice handleWearAdbRequest(request)
          ?: when {
            request.contains("settings get secure") -> ""
            else -> {
              unexpectedAdbRequests.add("WEAR $request")
              throw IllegalStateException("Unknown executeShellCommand request $request")
            }
          }
      }

    WearPairingManager.getInstance()
      .setDataProviders({ listOf(avdWearInfo) }, { listOf(phoneIDevice, wearIDevice) })
    WearPairingManager.getInstance()
      .createPairedDeviceBridge(phoneDevice, phoneIDevice, wearDevice, wearIDevice)
    val phoneWearPair = WearPairingManager.getInstance().getPairsForDevice(wearDevice.deviceID)
    WearPairingManager.getInstance().removePairedDevices(phoneWearPair.single())
    assertTrue(
      "Unexpected ADB requests\n${unexpectedAdbRequests.joinToString("\n")}",
      unexpectedAdbRequests.isEmpty(),
    )
  }
}
