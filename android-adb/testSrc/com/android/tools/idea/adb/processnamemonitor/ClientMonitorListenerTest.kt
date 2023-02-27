/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.adb.processnamemonitor

import com.android.adblib.testing.FakeAdbLoggerFactory
import com.android.ddmlib.Client.CHANGE_NAME
import com.android.ddmlib.IDevice.CHANGE_CLIENT_LIST
import com.android.tools.idea.adb.processnamemonitor.ClientMonitorListener.ClientEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Tests for [ClientMonitorListener]
 */
@Suppress("EXPERIMENTAL_API_USAGE")
class ClientMonitorListenerTest {
  private val logger = FakeAdbLoggerFactory().logger

  private val device1 = mockDevice("device1")
  private val device2 = mockDevice("device2")
  private val client1 = mockClient(1, "package1", "process1")
  private val client2 = mockClient(2, "package2", "process2")

  @Test
  fun clientListChanged() {
    runBlocking {
      val flow = callbackFlow {
        val monitor = ClientMonitorListener(device1, this, logger)

        monitor.deviceChanged(device1.withClients(client1), CHANGE_CLIENT_LIST)
        monitor.deviceChanged(device1.withClients(client1, client2), CHANGE_CLIENT_LIST)
        monitor.deviceChanged(device1.withClients(client2), CHANGE_CLIENT_LIST)

        close()
        awaitClose { }
      }

      assertThat(flow.toStrings()).containsExactly(
        "ClientListChanged: 1: package1 process1",
        "ClientListChanged: 1: package1 process1, 2: package2 process2",
        "ClientListChanged: 2: package2 process2",
      ).inOrder()
    }
  }

  @Test
  fun clientChanged() {
    runBlocking {
      val flow = callbackFlow {
        val clientNotInitialized = mockClient(1, null, null)
        val clientInitialized = mockClient(1, "package1", "process1")

        val monitor = ClientMonitorListener(device1, this, logger)
        device1.withClients(clientNotInitialized)
        monitor.clientChanged(clientNotInitialized, CHANGE_NAME)
        device1.withClients(clientInitialized)
        monitor.clientChanged(clientInitialized, CHANGE_NAME)

        close()
        awaitClose { }
      }

      assertThat(flow.toStrings()).containsExactly(
        "ClientChanged: 1: null null",
        "ClientChanged: 1: package1 process1",
      ).inOrder()
    }
  }

  @Test
  fun clientChanged_wrongDevice() {
    runBlocking {
      val flow = callbackFlow {
        val monitor = ClientMonitorListener(device1.withClients(client1), this, logger)
        device2.withClients(client2)
        monitor.clientChanged(client2, CHANGE_NAME)

        close()
        awaitClose { }
      }

      assertThat(flow.toStrings()).isEmpty()
    }
  }

}

private suspend fun Flow<ClientEvent>.toStrings(): List<String> {
  return toList().map { it.toString() }
}
