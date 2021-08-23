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

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test


class WearPairingSettingsTest {
  private val phoneDevice = PairingDevice(
    deviceID = "id1", displayName = "My Phone", apiLevel = 30, isWearDevice = false, isEmulator = true, hasPlayStore = true,
    state = ConnectionState.ONLINE, isPaired = false
  )
  private val wearDevice = PairingDevice(
    deviceID = "id2", displayName = "Round Watch", apiLevel = 30, isEmulator = true, isWearDevice = true, hasPlayStore = true,
    state = ConnectionState.ONLINE, isPaired = false
  )

  @Before
  fun setUp() {
    WearPairingManager.loadSettings(emptyList(), emptyList()) // Clean up any pairing data leftovers
  }

  @Test
  fun loadSettingsShouldSetPairedDevices() {
    assert(WearPairingManager.getPairedDevices(phoneDevice.deviceID).first == null)

    val pairedDevices = listOf(phoneDevice.toPairingDeviceState(), wearDevice.toPairingDeviceState())
    val pairedConnectionState = PairingConnectionsState().apply {
      phoneId = phoneDevice.deviceID
      wearDeviceIds.add(wearDevice.deviceID)
    }

    WearPairingManager.loadSettings(pairedDevices, listOf(pairedConnectionState))

    WearPairingManager.getPairedDevices(phoneDevice.deviceID).apply {
      assertThat(first).isNotNull()
      assertThat(second).isNotNull()
      assertThat(first!!.deviceID).isEqualTo(phoneDevice.deviceID)
      assertThat(second!!.deviceID).isEqualTo(wearDevice.deviceID)
      assertThat(first!!.state).isEqualTo(ConnectionState.DISCONNECTED)
      assertThat(second!!.state).isEqualTo(ConnectionState.DISCONNECTED)
    }
  }
}