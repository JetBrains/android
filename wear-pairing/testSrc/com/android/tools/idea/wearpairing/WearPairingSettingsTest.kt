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

import com.android.sdklib.AndroidVersion
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearPairingSettingsTest {
  @get:Rule val applicationRule = ApplicationRule()

  private val phoneDevice =
    PairingDevice(
      deviceID = "id1",
      displayName = "My Phone",
      androidVersion = AndroidVersion(36, 1),
      isWearDevice = false,
      isEmulator = true,
      hasPlayStore = true,
      state = ConnectionState.ONLINE,
    )
  private val wearDevice =
    PairingDevice(
      deviceID = "id2",
      displayName = "Round Watch",
      androidVersion = AndroidVersion(30),
      isEmulator = true,
      isWearDevice = true,
      hasPlayStore = true,
      state = ConnectionState.ONLINE,
    )

  @Before
  fun setUp() {
    WearPairingManager.getInstance()
      .loadSettings(emptyList(), emptyList()) // Clean up any pairing data leftovers
  }

  @Test
  fun roundTrip() {
    assertThat(phoneDevice.toPairingDeviceState().toPairingDevice(ConnectionState.ONLINE))
      .isEqualTo(phoneDevice)
    assertThat(wearDevice.toPairingDeviceState().toPairingDevice(ConnectionState.ONLINE))
      .isEqualTo(wearDevice)
  }

  @Test
  fun loadSettingsShouldSetPairedDevices() {
    assertThat(WearPairingManager.getInstance().getPairsForDevice(phoneDevice.deviceID)).isEmpty()

    val pairedDevices =
      listOf(phoneDevice.toPairingDeviceState(), wearDevice.toPairingDeviceState())
    val pairedConnectionState =
      PairingConnectionsState().apply {
        phoneId = phoneDevice.deviceID
        wearDeviceIds.add(wearDevice.deviceID)
      }

    WearPairingManager.getInstance().loadSettings(pairedDevices, listOf(pairedConnectionState))

    val phoneWearPairList = WearPairingManager.getInstance().getPairsForDevice(phoneDevice.deviceID)
    assertThat(phoneWearPairList).isNotEmpty()
    val phoneWearPair = phoneWearPairList[0]
    assertThat(phoneWearPair.phone.deviceID).isEqualTo(phoneDevice.deviceID)
    assertThat(phoneWearPair.wear.deviceID).isEqualTo(wearDevice.deviceID)
    assertThat(phoneWearPair.phone.state).isEqualTo(ConnectionState.DISCONNECTED)
    assertThat(phoneWearPair.wear.state).isEqualTo(ConnectionState.DISCONNECTED)
    assertThat(phoneWearPair.getPeerDevice(phoneDevice.deviceID).deviceID)
      .isEqualTo(wearDevice.deviceID)
    assertThat(phoneWearPair.getPeerDevice(wearDevice.deviceID).deviceID)
      .isEqualTo(phoneDevice.deviceID)
  }

  @Test
  fun addListenerShouldReceiveCurrentState() {
    val pairedDevices =
      listOf(phoneDevice.toPairingDeviceState(), wearDevice.toPairingDeviceState())
    val pairedConnectionState =
      PairingConnectionsState().apply {
        phoneId = phoneDevice.deviceID
        wearDeviceIds.add(wearDevice.deviceID)
      }

    WearPairingManager.getInstance().loadSettings(pairedDevices, listOf(pairedConnectionState))

    var receivedPairingStatus: WearPairingManager.PhoneWearPair? = null
    WearPairingManager.getInstance()
      .addDevicePairingStatusChangedListener(
        object : WearPairingManager.PairingStatusChangedListener {
          override fun pairingStatusChanged(phoneWearPair: WearPairingManager.PhoneWearPair) {
            receivedPairingStatus = phoneWearPair
          }

          override fun pairingDeviceRemoved(phoneWearPair: WearPairingManager.PhoneWearPair) {}
        }
      )

    assertThat(receivedPairingStatus?.pairingStatus)
      .isEqualTo(WearPairingManager.PairingState.OFFLINE)
  }
}
