/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.AndroidDebugBridgeDelegate
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.function.Consumer
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class StudioLegacyAllocationTrackerTest {
  companion object {
    private val clientListeners = CopyOnWriteArrayList<AndroidDebugBridge.IClientChangeListener>()

    @BeforeClass
    @JvmStatic
    fun setUpClass() {
      val mockDelegate = mock(AndroidDebugBridgeDelegate::class.java)
      whenever(mockDelegate.addClientChangeListener(any())).thenAnswer {
        clientListeners.add(it.getArgument(0))
        null
      }
      whenever(mockDelegate.removeClientChangeListener(any())).thenAnswer {
        clientListeners.remove(it.getArgument(0))
        null
      }
      whenever(mockDelegate.clientChanged(any(), anyInt())).thenAnswer {
        val client = it.getArgument<Client>(0)
        val mask = it.getArgument<Int>(1)
        clientListeners.forEach { l -> l.clientChanged(client, mask) }
        null
      }
      AndroidDebugBridge.resetForTests()
      AndroidDebugBridge.preInit(mockDelegate)
    }

    @AfterClass
    @JvmStatic
    fun tearDownClass() {
      clientListeners.clear()
      AndroidDebugBridge.resetForTests()
    }
  }

  @org.junit.Before
  fun setUp() {
    clientListeners.clear()
  }

  @org.junit.After
  fun tearDown() {
    clientListeners.clear()
  }

  private val pid = 1234
  private val appName = "com.example.app"

  @Test
  fun testTrackAllocationsDeviceOffline() {
    val mockDevice = mock(IDevice::class.java)
    whenever(mockDevice.isOnline).thenReturn(false)

    val tracker = StudioLegacyAllocationTracker(mockDevice, pid)
    assertThat(tracker.trackAllocations(true, null, null)).isFalse()
  }

  @Test
  fun testTrackAllocationsClientNotFound() {
    val mockDevice = mock(IDevice::class.java)
    whenever(mockDevice.isOnline).thenReturn(true)
    whenever(mockDevice.getClientName(pid)).thenReturn(appName)
    whenever(mockDevice.getClient(appName)).thenReturn(null)

    val tracker = StudioLegacyAllocationTracker(mockDevice, pid)
    assertThat(tracker.trackAllocations(true, null, null)).isFalse()
  }

  @Test
  fun testTrackAllocationsInitialStopReturnsFalse() {
    val mockDevice = mock(IDevice::class.java)
    val mockClient = mock(Client::class.java)
    whenever(mockDevice.isOnline).thenReturn(true)
    whenever(mockDevice.getClientName(pid)).thenReturn(appName)
    whenever(mockDevice.getClient(appName)).thenReturn(mockClient)

    val tracker = StudioLegacyAllocationTracker(mockDevice, pid)
    val executor = Executor { it.run() }
    val consumer = Consumer<ByteArray?> {}
    assertThat(tracker.trackAllocations(false, executor, consumer)).isFalse()
  }

  @Test
  fun testTrackAllocationsStartAndStopWorkflow() {
    val mockDevice = mock(IDevice::class.java)
    val mockClient = mock(Client::class.java)
    val mockClientData = mock(ClientData::class.java)
    val dummyData = byteArrayOf(10, 20, 30)

    whenever(mockDevice.isOnline).thenReturn(true)
    whenever(mockDevice.getClientName(pid)).thenReturn(appName)
    whenever(mockDevice.getClient(appName)).thenReturn(mockClient)
    whenever(mockClient.clientData).thenReturn(mockClientData)
    whenever(mockClientData.allocationsData).thenReturn(dummyData)

    val tracker = StudioLegacyAllocationTracker(mockDevice, pid)

    // Start tracking
    assertThat(tracker.trackAllocations(true, null, null)).isTrue()
    verify(mockClient).enableAllocationTracker(true)

    // Attempting to start again when already running should return false
    assertThat(tracker.trackAllocations(true, null, null)).isFalse()

    // Stop tracking
    val executor = Executor { it.run() }
    var receivedData: ByteArray? = null
    val consumer = Consumer<ByteArray?> { receivedData = it }

    assertThat(tracker.trackAllocations(false, executor, consumer)).isTrue()
    verify(mockClient).requestAllocationDetails()
    verify(mockClient).enableAllocationTracker(false)

    // Fire clientChanged with unrelated change mask
    AndroidDebugBridge.clientChanged(mockClient, Client.CHANGE_NAME)
    assertThat(receivedData).isNull()

    // Fire clientChanged for a different client
    val otherClient = mock(Client::class.java)
    AndroidDebugBridge.clientChanged(otherClient, Client.CHANGE_HEAP_ALLOCATIONS)
    assertThat(receivedData).isNull()

    // Fire clientChanged for our target client with CHANGE_HEAP_ALLOCATIONS
    AndroidDebugBridge.clientChanged(mockClient, Client.CHANGE_HEAP_ALLOCATIONS)
    assertThat(receivedData).isEqualTo(dummyData)

    // Firing again should not trigger consumer again since listener was removed
    receivedData = null
    AndroidDebugBridge.clientChanged(mockClient, Client.CHANGE_HEAP_ALLOCATIONS)
    assertThat(receivedData).isNull()
    assertThat(clientListeners).isEmpty()
  }
}
