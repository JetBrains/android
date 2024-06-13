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
package com.android.tools.idea.devicemanagerv2

import com.android.tools.idea.wearpairing.ObservablePairedDevicesList
import com.android.tools.idea.wearpairing.PairingDevice
import com.android.tools.idea.wearpairing.WearPairingManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class PairingStatus(
  val id: String,
  val displayName: String,
  val state: WearPairingManager.PairingState,
)

/**
 * Listens to updates from the [WearPairingManager], producing a Flow describing the pairing status
 * of all devices. Each device's wear pairing ID is mapped to a list of [PairingStatus].
 */
fun ObservablePairedDevicesList.pairedDevicesFlow():
  Flow<ImmutableMap<String, ImmutableList<PairingStatus>>> = callbackFlow {

  // Our state is kept here, updated by the listener, then published to the flow.
  var pairedDevices = persistentHashMapOf<String, PersistentList<PairingStatus>>()

  /**
   * Given a PhoneWearPair that has had a pairing status change, updates the mapping for both the
   * phone and the wear device.
   */
  fun update(
    pair: WearPairingManager.PhoneWearPair,
    updateList: (PersistentList<PairingStatus>, PairingDevice) -> PersistentList<PairingStatus>,
  ) {
    pairedDevices =
      pairedDevices.mutate {
        it[pair.wear.deviceID] =
          updateList(pairedDevices[pair.wear.deviceID] ?: persistentListOf(), pair.phone)
        it[pair.phone.deviceID] =
          updateList(pairedDevices[pair.phone.deviceID] ?: persistentListOf(), pair.wear)
      }
    trySendBlocking(pairedDevices)
  }

  val listener =
    object : WearPairingManager.PairingStatusChangedListener {
      override fun pairingStatusChanged(phoneWearPair: WearPairingManager.PhoneWearPair) {
        update(phoneWearPair) { currentDevices, pairingDevice ->
          val newStatus =
            PairingStatus(
              pairingDevice.deviceID,
              pairingDevice.displayName,
              phoneWearPair.pairingStatus,
            )
          when (val index = currentDevices.indexOfFirst { it.id == pairingDevice.deviceID }) {
            -1 -> currentDevices + newStatus
            else -> currentDevices.set(index, newStatus)
          }
        }
      }

      override fun pairingDeviceRemoved(phoneWearPair: WearPairingManager.PhoneWearPair) {
        update(phoneWearPair) { currentDevices, pairingDevice ->
          currentDevices.removeAll { it.id == pairingDevice.deviceID }
        }
      }
    }

  addDevicePairingStatusChangedListener(listener)
  awaitClose { removeDevicePairingStatusChangedListener(listener) }
}
