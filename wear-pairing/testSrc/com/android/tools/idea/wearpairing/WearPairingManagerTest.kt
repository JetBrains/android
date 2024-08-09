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
import com.android.sdklib.internal.avd.ConfigKey
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.waitForCondition
import com.android.tools.idea.wearpairing.WearPairingManager.PairingStatusChangedListener
import com.google.common.util.concurrent.Futures
import com.intellij.testFramework.ApplicationRule
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    mapOf(ConfigKey.TAG_ID to "android-wear", ConfigKey.ANDROID_API to "28")
  private val avdWearInfo =
    AvdInfo(
      Paths.get("ini"),
      Paths.get("id2"),
      Mockito.mock(ISystemImage::class.java),
      wearPropertiesMap,
      null,
    )

  private val pairingManager = WearPairingManager()

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

    pairingManager.setDataProviders({ listOf() }, { listOf(directAccessIDevice, wearIDevice) })
    pairingManager.createPairedDeviceBridge(
      directAccessDevice,
      directAccessIDevice,
      wearDevice,
      wearIDevice,
    )
    assertNotNull(pairingManager.findDevice("firebase-remote-dev1"))
  }

  @Test
  fun pairingIsRemovedWhenNewSessionOfDirectAccessDeviceIsConnected() = runBlocking {
    val expiredDirectAccessDeviceID = "projects/222768521919/deviceSessions/session-294jm1dz2ck5m"
    val newDirectAccessDeviceID = "projects/222768521919/deviceSessions/session-3mafdtdd82zxd"
    val newDirectAccessIDevice =
      directAccessDevice.buildIDevice(
        properties = mapOf(PROP_FIREBASE_TEST_LAB_SESSION to newDirectAccessDeviceID)
      ) { request ->
        handlePhoneAdbRequest(request)
          ?: throw IllegalStateException("Unknown executeShellCommand request $request")
      }

    pairingManager.setDataProviders(
      virtualDevices = { listOf(avdWearInfo) },
      connectedDevices = { listOf(newDirectAccessIDevice) },
    )
    pairingManager.loadSettings(
      pairedDevices =
        listOf(
          PairingDeviceState(
            deviceID = expiredDirectAccessDeviceID,
            displayName = directAccessDevice.displayName,
          ),
          PairingDeviceState(
            deviceID = avdWearInfo.id,
            displayName = avdWearInfo.name,
            isEmulator = true,
          ),
        ),
      pairedDeviceConnections =
        listOf(
          PairingConnectionsState().apply {
            phoneId = expiredDirectAccessDeviceID
            wearDeviceIds.add(avdWearInfo.id)
          }
        ),
    )

    val removedPairingDeferred = CompletableDeferred<WearPairingManager.PhoneWearPair>()
    pairingManager.addDevicePairingStatusChangedListener(
      object : PairingStatusChangedListener {
        override fun pairingStatusChanged(phoneWearPair: WearPairingManager.PhoneWearPair) {}

        override fun pairingDeviceRemoved(phoneWearPair: WearPairingManager.PhoneWearPair) {
          removedPairingDeferred.complete(phoneWearPair)
        }
      }
    )
    pairingManager.setDeviceListListener(WearDevicePairingModel(), WizardActionTest())

    val removedPairing = removedPairingDeferred.await()
    assertEquals(expiredDirectAccessDeviceID, removedPairing.phone.deviceID)
    assertEquals(avdWearInfo.id, removedPairing.wear.deviceID)
    assertTrue(pairingManager.getPairsForDevice(expiredDirectAccessDeviceID).isEmpty())
  }

  @Test
  fun pairingIsUpdatedWhenExistingSessionOfDirectAccessDeviceIsConnected() = runBlocking {
    val directAccessDeviceID = "projects/222768521919/deviceSessions/session-3mafdtdd82zxd"
    val directAccessIDevice =
      directAccessDevice.buildIDevice(
        properties = mapOf(PROP_FIREBASE_TEST_LAB_SESSION to directAccessDeviceID)
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

    pairingManager.setDataProviders(
      virtualDevices = { listOf(avdWearInfo) },
      connectedDevices = { listOf(directAccessIDevice, wearIDevice) },
    )
    pairingManager.loadSettings(
      pairedDevices =
        listOf(
          PairingDeviceState(
            deviceID = directAccessDeviceID,
            displayName = directAccessDevice.displayName,
          ),
          PairingDeviceState(
            deviceID = avdWearInfo.id,
            displayName = avdWearInfo.name,
            isEmulator = true,
          ),
        ),
      pairedDeviceConnections =
        listOf(
          PairingConnectionsState().apply {
            phoneId = directAccessDeviceID
            wearDeviceIds.add(avdWearInfo.id)
          }
        ),
    )

    // The PairingManager calls PairingStatusChangedListener.pairingStatusChanged immediately when
    // adding
    // the listener, so we want to ignore the first status change.
    val initialStatusChange = AtomicBoolean(true)
    val changedPairingDeferred = CompletableDeferred<WearPairingManager.PhoneWearPair>()
    pairingManager.addDevicePairingStatusChangedListener(
      object : PairingStatusChangedListener {
        override fun pairingStatusChanged(phoneWearPair: WearPairingManager.PhoneWearPair) {
          if (!initialStatusChange.getAndSet(false)) {
            changedPairingDeferred.complete(phoneWearPair)
          }
        }

        override fun pairingDeviceRemoved(phoneWearPair: WearPairingManager.PhoneWearPair) {}
      }
    )
    pairingManager.setDeviceListListener(WearDevicePairingModel(), WizardActionTest())

    val changedPairing = changedPairingDeferred.await()
    assertEquals(directAccessDeviceID, changedPairing.phone.deviceID)
    assertEquals(avdWearInfo.id, changedPairing.wear.deviceID)
    assertTrue(
      changedPairing.pairingStatus in
        setOf(WearPairingManager.PairingState.CONNECTED, WearPairingManager.PairingState.CONNECTING)
    )
  }

  @Test
  fun onlyConnectedDirectAccessDevicesShouldBeInPhoneList() = runBlocking {
    val disconnectedDirectAccessDeviceID =
      "projects/222768521919/deviceSessions/session-294jm1dz2ck5m"
    val connectedDirectAccessDeviceID = "projects/222768521919/deviceSessions/session-1dw7qe2spkoq2"
    val connectedAccessIDevice =
      directAccessDevice.buildIDevice(
        properties = mapOf(PROP_FIREBASE_TEST_LAB_SESSION to connectedDirectAccessDeviceID)
      ) { request ->
        handlePhoneAdbRequest(request)
          ?: throw IllegalStateException("Unknown executeShellCommand request $request")
      }

    pairingManager.setDataProviders(
      virtualDevices = { listOf(avdWearInfo) },
      connectedDevices = { listOf(connectedAccessIDevice) },
    )
    pairingManager.loadSettings(
      pairedDevices =
        listOf(
          PairingDeviceState(
            deviceID = disconnectedDirectAccessDeviceID,
            displayName = directAccessDevice.displayName,
          ),
          PairingDeviceState(
            deviceID = avdWearInfo.id,
            displayName = avdWearInfo.name,
            isEmulator = true,
          ),
        ),
      pairedDeviceConnections =
        listOf(
          PairingConnectionsState().apply {
            phoneId = disconnectedDirectAccessDeviceID
            wearDeviceIds.add(avdWearInfo.id)
          }
        ),
    )

    val model = WearDevicePairingModel()
    val phoneListUpdatedDeferred = CompletableDeferred<Unit>()
    model.phoneList.addListener { phoneListUpdatedDeferred.complete(Unit) }
    pairingManager.setDeviceListListener(model, WizardActionTest())

    phoneListUpdatedDeferred.await()
    assertEquals(1, model.phoneList.get().size)
    assertEquals(connectedDirectAccessDeviceID, model.phoneList.get().map { it.deviceID }.first())
  }

  // Regression test for http://b/343394835
  @Test
  fun reconnectionShouldNotOccurOnTheUIThread() {
    val phoneDevice =
      PairingDevice(
        deviceID = "phoneId",
        displayName = "My Phone",
        apiLevel = 34,
        isWearDevice = false,
        isEmulator = true,
        hasPlayStore = true,
        state = ConnectionState.ONLINE,
      )
    val avdPhoneInfo =
      AvdInfo(
        Paths.get("ini"),
        Paths.get(phoneDevice.deviceID),
        Mockito.mock(ISystemImage::class.java),
        mapOf(),
        null,
      )
    val phoneIDevice =
      phoneDevice.buildIDevice(avdInfo = avdPhoneInfo) { request ->
        handlePhoneAdbRequest(request)
          ?: throw IllegalStateException("Unknown executeShellCommand request $request")
      }
    val wearIDevice =
      wearDevice
        .buildIDevice(avdInfo = avdWearInfo) { request ->
          return@buildIDevice handleWearAdbRequest(request)
            ?: throw IllegalStateException("Unknown executeShellCommand request $request")
        }
        .apply {
          whenever(getSystemProperty("ro.oem.companion_package"))
            .thenReturn(Futures.immediateFuture(""))
        }

    val isPairingReconnected = AtomicBoolean(false)
    pairingManager.addDevicePairingStatusChangedListener(
      object : PairingStatusChangedListener {
        override fun pairingStatusChanged(phoneWearPair: WearPairingManager.PhoneWearPair) {
          isPairingReconnected.set(
            phoneWearPair.pairingStatus == WearPairingManager.PairingState.CONNECTED
          )
        }

        override fun pairingDeviceRemoved(phoneWearPair: WearPairingManager.PhoneWearPair) {}
      }
    )

    pairingManager.setDataProviders(
      { listOf(avdPhoneInfo, avdWearInfo) },
      { listOf(phoneIDevice, wearIDevice) },
    )

    pairingManager.loadSettings(
      listOf(phoneDevice.toPairingDeviceState(), wearDevice.toPairingDeviceState()),
      listOf(
        PairingConnectionsState().apply {
          phoneId = phoneDevice.deviceID
          wearDeviceIds.add(wearDevice.deviceID)
        }
      ),
    )

    pairingManager.setDeviceListListener(WearDevicePairingModel(), WizardActionTest())

    waitForCondition(5, TimeUnit.SECONDS) {
      // the pairing will not succeed if the wrong thread is used due to the threading assertions
      isPairingReconnected.get()
    }
  }
}
