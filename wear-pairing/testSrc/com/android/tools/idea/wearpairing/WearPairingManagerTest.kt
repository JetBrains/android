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

import com.android.sdklib.ISystemImage
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.intellij.testFramework.ApplicationRule
import java.nio.file.Paths
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

class WearPairingManagerTest {
  @get:Rule val applicationRule = ApplicationRule()

  private val directAccessDevice =
    PairingDevice(
      deviceID = "localhost:4432",
      displayName = "My Phone",
      apiLevel = 34,
      isWearDevice = false,
      isEmulator = false,
      hasPlayStore = true,
      state = ConnectionState.ONLINE,
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
  private val wearPropertiesMap =
    mapOf(AvdManager.AVD_INI_TAG_ID to "android-wear", AvdManager.AVD_INI_ANDROID_API to "28")
  private val avdWearInfo =
    AvdInfo(
      "My Wear",
      Paths.get("ini"),
      Paths.get("id2"),
      Mockito.mock(ISystemImage::class.java),
      wearPropertiesMap,
      null,
    )

  @Test
  fun directAccessDevicePairingReportsCorrectDeviceIdTest(): Unit = runBlocking {
    val directAccessIDevice =
      directAccessDevice.buildIDevice(
        properties = mapOf(PROP_FIREBASE_TEST_LAB_SESSION to "firebase-remote-dev1")
      ) { request ->
        handlePhoneAdbRequest(request)
          ?: throw IllegalStateException("Unknown executeShellCommand request $request")
      }
    val wearIDevice =
      wearDevice.buildIDevice(
        avdInfo = avdWearInfo,
        systemProperties = mapOf("ro.oem.companion_package" to ""),
      ) { request ->
        return@buildIDevice handleWearAdbRequest(request)
          ?: throw IllegalStateException("Unknown executeShellCommand request $request")
      }

    WearPairingManager.getInstance()
      .setDataProviders({ listOf() }, { listOf(directAccessIDevice, wearIDevice) })
    WearPairingManager.getInstance()
      .createPairedDeviceBridge(directAccessDevice, directAccessIDevice, wearDevice, wearIDevice)
    assertNotNull(WearPairingManager.getInstance().findDevice("firebase-remote-dev1"))
  }
}
