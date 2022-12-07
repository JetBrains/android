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
package com.android.tools.idea.run.deployment.liveedit

import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(JUnit4::class)
class DeviceEventWatcherTest {

  @Mock
  private val testDevice: IDevice = MockitoKt.mock()

  @Test
  fun testDeviceDisconnect() {
    val watcher = DeviceEventWatcher()
    var device: IDevice? = null
    var event: DeviceEvent? = null
    watcher.addListener { d, e ->
      device = d
      event = e
    }
    watcher.deviceDisconnected(testDevice)
    assertEquals(testDevice, device)
    assertEquals(DeviceEvent.DEVICE_DISCONNECT, event)
  }

  @Test
  fun testAppConnect() {
    val watcher = DeviceEventWatcher()
    val appId = "app.id"

    var device: IDevice? = null
    var event: DeviceEvent? = null
    watcher.setApplicationId(appId)
    watcher.addListener { d, e ->
      device = d
      event = e
    }

    val notMyClient: Client = MockitoKt.mock()
    val notMyData: ClientData = MockitoKt.mock()
    Mockito.`when`(notMyClient.clientData).thenReturn(notMyData)
    Mockito.`when`(notMyData.packageName).thenReturn("not.mine")

    // Ignore clients for other apps
    watcher.clientChanged(notMyClient, Client.CHANGE_NAME)
    assertNull(device)
    assertNull(event)

    val myClient: Client = MockitoKt.mock()
    val myData: ClientData = MockitoKt.mock()
    Mockito.`when`(myClient.device).thenReturn(testDevice)
    Mockito.`when`(myClient.clientData).thenReturn(myData)
    Mockito.`when`(myData.packageName).thenReturn(appId)

    // Detect changes for the specified app
    watcher.clientChanged(myClient, Client.CHANGE_NAME)
    assertEquals(testDevice, device)
    assertEquals(DeviceEvent.APPLICATION_CONNECT, event)
  }

  @Test
  fun testAppDisconnect() {
    val watcher = DeviceEventWatcher()
    val appId = "app.id"

    var device: IDevice? = null
    var event: DeviceEvent? = null
    watcher.setApplicationId(appId)
    watcher.addListener { d, e ->
      device = d
      event = e
    }

    val myClient: Client = MockitoKt.mock()
    val myData: ClientData = MockitoKt.mock()
    Mockito.`when`(myClient.device).thenReturn(testDevice)
    Mockito.`when`(testDevice.clients).thenReturn(arrayOf(myClient))

    // Ignore client list change events until we have at least one connected client.
    watcher.deviceChanged(testDevice, IDevice.CHANGE_CLIENT_LIST)
    assertNull(device)
    assertNull(event)

    // Detect the connected client
    Mockito.`when`(myClient.clientData).thenReturn(myData)
    Mockito.`when`(myData.packageName).thenReturn(appId)
    watcher.clientChanged(myClient, Client.CHANGE_NAME)

    // Remove the connected client
    Mockito.`when`(testDevice.clients).thenReturn(arrayOf())

    watcher.deviceChanged(testDevice, IDevice.CHANGE_CLIENT_LIST)
    assertEquals(testDevice, device)
    assertEquals(DeviceEvent.APPLICATION_DISCONNECT, event)
  }

  @Test
  fun testDebuggerEvents() {
    val watcher = DeviceEventWatcher()
    val appId = "app.id"

    var device: IDevice? = null
    var event: DeviceEvent? = null
    watcher.setApplicationId(appId)
    watcher.addListener { d, e ->
      device = d
      event = e
    }

    val notMyClient: Client = MockitoKt.mock()
    val notMyData: ClientData = MockitoKt.mock()
    Mockito.`when`(notMyClient.clientData).thenReturn(notMyData)
    Mockito.`when`(notMyClient.isDebuggerAttached).thenReturn(true)
    Mockito.`when`(notMyData.packageName).thenReturn("not.mine")

    // Ignore clients for other apps
    watcher.clientChanged(notMyClient, Client.CHANGE_DEBUGGER_STATUS)
    assertNull(device)
    assertNull(event)

    val myClient: Client = MockitoKt.mock()
    val myData: ClientData = MockitoKt.mock()
    Mockito.`when`(myClient.device).thenReturn(testDevice)
    Mockito.`when`(myClient.clientData).thenReturn(myData)
    Mockito.`when`(myData.packageName).thenReturn(appId)

    // Connect the debugger
    Mockito.`when`(myClient.isDebuggerAttached).thenReturn(true)

    watcher.clientChanged(myClient, Client.CHANGE_DEBUGGER_STATUS)
    assertEquals(testDevice, device)
    assertEquals(DeviceEvent.DEBUGGER_CONNECT, event)

    // Disconnect the debugger
    Mockito.`when`(myClient.isDebuggerAttached).thenReturn(false)

    watcher.clientChanged(myClient, Client.CHANGE_DEBUGGER_STATUS)
    assertEquals(testDevice, device)
    assertEquals(DeviceEvent.DEBUGGER_DISCONNECT, event)
  }
}
