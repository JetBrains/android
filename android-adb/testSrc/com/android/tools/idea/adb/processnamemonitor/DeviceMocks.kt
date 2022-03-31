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
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.DeviceState
import com.android.ddmlib.IDevice.DeviceState.OFFLINE
import com.android.testutils.MockitoKt
import org.mockito.Mockito.`when`

/**
 * Mocks for [IDevice] and [Client]
 */
internal fun mockDevice(serialNumber: String, state: DeviceState = OFFLINE): IDevice {
  return MockitoKt.mock<IDevice>().also {
    `when`(it.serialNumber).thenReturn(serialNumber)
    `when`(it.toString()).thenReturn(serialNumber)
    `when`(it.state).thenReturn(state)
    `when`(it.isOnline).thenReturn(state == DeviceState.ONLINE)
    `when`(it.isOffline).thenReturn(state != DeviceState.ONLINE)
    `when`(it.clients).thenReturn(emptyArray())
  }
}

internal fun IDevice.setState(state: DeviceState): IDevice {
  `when`(this.state).thenReturn(state)
  return this
}

internal fun IDevice.withClients(vararg clients: Client): IDevice {
  `when`(this.clients).thenReturn(clients)
  clients.forEach { `when`(it.device).thenReturn(this) }
  return this
}

internal fun mockClient(pid: Int, packageName: String?, processName: String?): Client {
  val clientData = mockClientData(pid, packageName, processName)
  val client = MockitoKt.mock<Client>()
  `when`(client.clientData).thenReturn(clientData)
  `when`(client.toString()).thenReturn("pid=$pid packageName=$packageName")
  return client
}


private fun mockClientData(pid: Int, packageName: String?, processName: String?): ClientData {
  return MockitoKt.mock<ClientData>().also {
    `when`(it.pid).thenReturn(pid)
    `when`(it.packageName).thenReturn(packageName)
    `when`(it.clientDescription).thenReturn(processName)
  }
}

internal fun Client.withNames(packageName: String, processName: String): Client {
  `when`(clientData.packageName).thenReturn(packageName)
  `when`(clientData.clientDescription).thenReturn(processName)
  return this
}
