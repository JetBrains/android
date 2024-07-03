/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.testutils.MockitoKt.whenever
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.intellij.testFramework.LightPlatform4TestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.invocation.InvocationOnMock

private const val OEM_COMPANION_APP_ID = "com.example"

class PairingFeaturesTest : LightPlatform4TestCase() {
  @Test
  fun onGettingCompanionAppId_settingsIsReadFirst() {
    val device = createDeviceWithShellCommandResult(OEM_COMPANION_APP_ID)

    runBlocking { assertThat(device.getCompanionAppIdForWatch()).isEqualTo(OEM_COMPANION_APP_ID) }
  }

  @Test
  fun onGettingCompanionAppId_nothingIsNotSet_systemPropertyIsRead() {
    val device = createDeviceWithShellCommandResult("null")
    whenever(device.getSystemProperty(anyString()))
      .thenReturn(Futures.immediateFuture(OEM_COMPANION_APP_ID))

    runBlocking { assertThat(device.getCompanionAppIdForWatch()).isEqualTo(OEM_COMPANION_APP_ID) }
  }

  @Test
  fun onGettingCompanionAppId_settingIsNotSet_returnsWear2CompanionAppId() {
    val device = createDeviceWithShellCommandResult("null")
    whenever(device.getSystemProperty(anyString())).thenReturn(Futures.immediateFuture(""))

    runBlocking {
      assertThat(device.getCompanionAppIdForWatch()).isEqualTo(OEM_COMPANION_FALLBACK_APP_ID)
    }
  }
}

@RunWith(Parameterized::class)
class HasPairingFeatureTest(private val pairingFeature: PairingFeature) : LightPlatform4TestCase() {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Array<PairingFeature> {
      return PairingFeature.values()
    }
  }

  @Test
  fun onHasPairingFeature_lowerVersions_fail() {
    val device = createDeviceWithShellCommandResult("    versionCode=1 minSdk=23 targetSdk=30")

    runBlocking {
      assertThat(device.hasPairingFeature(pairingFeature, OEM_COMPANION_FALLBACK_APP_ID)).isFalse()
    }
  }

  @Test
  fun onHasPairingFeature_higherVersions_succeed() {
    val device =
      createDeviceWithShellCommandResult(
        "    versionCode=${pairingFeature.minVersion} minSdk=23 targetSdk=30"
      )

    runBlocking {
      assertThat(device.hasPairingFeature(pairingFeature, OEM_COMPANION_FALLBACK_APP_ID)).isTrue()
    }
  }
}

private fun createDeviceWithShellCommandResult(result: String): IDevice {
  val device = Mockito.mock(IDevice::class.java)
  doAnswer { invocation: InvocationOnMock ->
      val outputReceiver = invocation.getArgument<IShellOutputReceiver>(1)
      val data = result.toByteArray()
      outputReceiver.addOutput(data, 0, data.size)
      null
    }
    .whenever(device)
    .executeShellCommand(anyString(), Mockito.any())
  return device
}
