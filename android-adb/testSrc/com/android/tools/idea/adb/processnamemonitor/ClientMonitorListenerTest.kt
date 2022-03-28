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

import com.android.ddmlib.Client
import com.android.ddmlib.Client.CHANGE_NAME
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.CHANGE_CLIENT_LIST
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.adb.processnamemonitor.ClientMonitorListener.ClientEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito.`when`

/**
 * Tests for [ClientMonitorListener]
 */
@Suppress("EXPERIMENTAL_API_USAGE")
class ClientMonitorListenerTest {
  private val device1 = mockDevice("device1")
  private val device2 = mockDevice("device2")
  private val client1 = mockClient(1, "package1", "process1")
  private val client2 = mockClient(2, "package2", "process2")

  @Test
  fun clientListChanged() {
    runBlocking {
      val flow = callbackFlow {
        val monitor = ClientMonitorListener(device1, this)

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

        val monitor = ClientMonitorListener(device1, this)
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
        val monitor = ClientMonitorListener(device1.withClients(client1), this)
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

private fun mockDevice(serialNumber: String): IDevice {
  return mock<IDevice>().also {
    `when`(it.serialNumber).thenReturn(serialNumber)
    `when`(it.clients).thenReturn(emptyArray())
  }
}

private fun IDevice.withClients(vararg clients: Client): IDevice {
  clients.forEach {
    `when`(it.device).thenReturn(this)
  }
  `when`(this.clients).thenReturn(clients)
  return this
}

private fun mockClient(pid: Int, packageName: String?, processName: String?): Client {
  val clientData = mockClientData(pid, packageName, processName)
  return mock<Client>().also {
    `when`(it.clientData).thenReturn(clientData)
  }
}

private fun mockClientData(pid: Int, packageName: String?, processName: String?): ClientData {
  return mock<ClientData>().also {
    `when`(it.pid).thenReturn(pid)
    `when`(it.packageName).thenReturn(packageName)
    `when`(it.clientDescription).thenReturn(processName)
  }
}
