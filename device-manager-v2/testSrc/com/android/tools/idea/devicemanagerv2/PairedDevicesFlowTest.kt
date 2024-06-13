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

import com.android.tools.idea.wearpairing.ConnectionState
import com.android.tools.idea.wearpairing.ObservablePairedDevicesList
import com.android.tools.idea.wearpairing.PairingDevice
import com.android.tools.idea.wearpairing.WearPairingManager
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PairedDevicesFlowTest {
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun updateConnectionStatus() = runTest {
    val input = Channel<WearPairingManager.PairingStatusChangedListener.() -> Unit>()
    val output = Channel<ImmutableMap<String, ImmutableList<PairingStatus>>>()
    val list =
      object : ObservablePairedDevicesList {
        override fun addDevicePairingStatusChangedListener(
          listener: WearPairingManager.PairingStatusChangedListener
        ) {
          backgroundScope.launch {
            while (isActive) {
              val command = input.receive()
              listener.apply { command() }
            }
          }
        }

        override fun removeDevicePairingStatusChangedListener(
          listener: WearPairingManager.PairingStatusChangedListener
        ) {}
      }

    backgroundScope.launch { list.pairedDevicesFlow().collect { output.send(it) } }

    val phone =
      PairingDevice(
        deviceID = "id1",
        displayName = "Pixel 6",
        apiLevel = 33,
        isEmulator = true,
        isWearDevice = false,
        hasPlayStore = true,
        state = ConnectionState.ONLINE,
      )
    val wear =
      PairingDevice(
        deviceID = "id2",
        displayName = "Pixel Watch",
        apiLevel = 33,
        isEmulator = true,
        isWearDevice = true,
        hasPlayStore = true,
        state = ConnectionState.ONLINE,
      )

    input.send {
      pairingStatusChanged(
        WearPairingManager.PhoneWearPair(phone, wear).also {
          it.pairingStatus = WearPairingManager.PairingState.CONNECTING
        }
      )
    }

    output.receive().let {
      it["id1"].let {
        assertThat(checkNotNull(it)).hasSize(1)
        it[0].let {
          assertThat(it.id).isEqualTo("id2")
          assertThat(it.displayName).isEqualTo("Pixel Watch")
          assertThat(it.state).isEqualTo(WearPairingManager.PairingState.CONNECTING)
        }
      }
      it["id2"].let {
        assertThat(checkNotNull(it)).hasSize(1)
        it[0].let {
          assertThat(it.id).isEqualTo("id1")
          assertThat(it.displayName).isEqualTo("Pixel 6")
          assertThat(it.state).isEqualTo(WearPairingManager.PairingState.CONNECTING)
        }
      }
    }

    input.send {
      pairingStatusChanged(
        WearPairingManager.PhoneWearPair(phone, wear).also {
          it.pairingStatus = WearPairingManager.PairingState.CONNECTED
        }
      )
    }

    output.receive().let {
      it["id1"].let {
        assertThat(checkNotNull(it)).hasSize(1)
        it[0].let { assertThat(it.state).isEqualTo(WearPairingManager.PairingState.CONNECTED) }
      }
      it["id2"].let {
        assertThat(checkNotNull(it)).hasSize(1)
        it[0].let { assertThat(it.state).isEqualTo(WearPairingManager.PairingState.CONNECTED) }
      }
    }

    input.send { pairingDeviceRemoved(WearPairingManager.PhoneWearPair(phone, wear)) }

    output.receive().let {
      it["id1"].let { assertThat(it).hasSize(0) }
      it["id2"].let { assertThat(it).hasSize(0) }
    }
  }
}
