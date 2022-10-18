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
package com.android.tools.idea.logcat

import com.android.ddmlib.Client
import com.android.ddmlib.Client.CHANGE_DEBUGGER_STATUS
import com.android.ddmlib.Client.CHANGE_NAME
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.CHANGE_CLIENT_LIST
import com.android.ddmlib.IDevice.CHANGE_STATE
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.logcat.devices.Device
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.runInEdtAndWait
import org.junit.After
import org.junit.Rule
import org.junit.Test


private val client1 = client(1, "client1")
private val client2 = client(2, "client2")
private val client3 = client(3, "client3")

/**
 * Tests for [ProjectAppMonitor]
 */
@Suppress("ConvertLambdaToReference")
class ProjectAppMonitorTest {
  @get:Rule
  val rule = ApplicationRule()

  private val device1 = mockDevice("device1")
  private val logcatPresenter = FakeLogcatPresenter()

  @After
  fun tearDown() {
    Disposer.dispose(logcatPresenter)
  }

  @Test
  fun deviceChanged_addClients() {
    logcatPresenter.attachedDevice = createDevice(device1.serialNumber)
    val projectAppMonitor = projectAppMonitor(logcatPresenter, "client1", "client2")

    device1.setClients(client1, client2, client3)
    projectAppMonitor.deviceChanged(device1, CHANGE_CLIENT_LIST)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(listOf(client1.startedMessage(), client2.startedMessage()))
    }
  }

  @Test
  fun deviceChanged_addMoreClients() {
    logcatPresenter.attachedDevice = createDevice(device1.serialNumber)
    val projectAppMonitor = projectAppMonitor(logcatPresenter, "client1", "client2", "client3")
    device1.setClients(client1)
    projectAppMonitor.deviceChanged(device1, CHANGE_CLIENT_LIST)

    device1.setClients(client1, client2, client3)
    projectAppMonitor.deviceChanged(device1, CHANGE_CLIENT_LIST)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(
        listOf(client1.startedMessage()),
        listOf(client2.startedMessage(), client3.startedMessage()),
      )
    }
  }

  @Test
  fun deviceChanged_removeClients() {
    logcatPresenter.attachedDevice = createDevice(device1.serialNumber)
    val projectAppMonitor = projectAppMonitor(logcatPresenter, "client1", "client2", "client3")
    device1.setClients(client1, client2, client3)
    projectAppMonitor.deviceChanged(device1, CHANGE_CLIENT_LIST)

    device1.setClients(client3)
    projectAppMonitor.deviceChanged(device1, CHANGE_CLIENT_LIST)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(
        listOf(client1.startedMessage(), client2.startedMessage(), client3.startedMessage()),
        listOf(client1.endedMessage(), client2.endedMessage()),
      )
    }
  }

  @Test
  fun deviceChanged_addAndRemoveClients() {
    logcatPresenter.attachedDevice = createDevice(device1.serialNumber)
    val projectAppMonitor = projectAppMonitor(logcatPresenter, "client1", "client2", "client3")
    device1.setClients(client1, client2)
    projectAppMonitor.deviceChanged(device1, CHANGE_CLIENT_LIST)

    device1.setClients(client2, client3)
    projectAppMonitor.deviceChanged(device1, CHANGE_CLIENT_LIST)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(
        listOf(client1.startedMessage(), client2.startedMessage()),
        listOf(client3.startedMessage(), client1.endedMessage()),
      )
    }
  }

  @Test
  fun deviceChanged_noChanges() {
    logcatPresenter.attachedDevice = createDevice(device1.serialNumber)
    val projectAppMonitor = projectAppMonitor(logcatPresenter, "client1", "client2")
    device1.setClients(client1, client2)
    projectAppMonitor.deviceChanged(device1, CHANGE_CLIENT_LIST)

    device1.setClients(client2, client1)
    projectAppMonitor.deviceChanged(device1, CHANGE_CLIENT_LIST)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(
        listOf(client1.startedMessage(), client2.startedMessage()),
      )
    }
  }

  @Test
  fun deviceChanged_wrongMask() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, "client1", "client2")

    device1.setClients(client1, client2)
    projectAppMonitor.deviceChanged(device1, CHANGE_STATE)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).isEmpty()
    }
  }

  @Test
  fun deviceChanged_wrongDevice() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, "client1", "client2")
    val otherDevice = mock<IDevice>()

    otherDevice.setClients(client1, client2)
    projectAppMonitor.deviceChanged(device1, CHANGE_STATE)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).isEmpty()
    }
  }

  @Test
  fun clientChanged() {
    logcatPresenter.attachedDevice = createDevice(device1.serialNumber)
    val projectAppMonitor = projectAppMonitor(logcatPresenter, "client1", "client2")
    device1.setClients(client1)

    projectAppMonitor.clientChanged(client1, CHANGE_NAME)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(listOf(client1.startedMessage()))
    }
  }

  @Test
  fun clientChanged_wrongMask() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, "client1", "client2")
    device1.setClients(client1)

    projectAppMonitor.clientChanged(client1, CHANGE_DEBUGGER_STATUS)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).isEmpty()
    }
  }

  @Test
  fun clientChanged_wrongDevice() {
    logcatPresenter.attachedDevice = createDevice("device1")
    val projectAppMonitor = projectAppMonitor(logcatPresenter, "client1", "client2")
    val otherDevice = mockDevice("device2")
    otherDevice.setClients(client1)

    projectAppMonitor.clientChanged(client1, CHANGE_NAME)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).isEmpty()
    }
  }

  @Test
  fun clientChanged_wrongPackage() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, "client2")
    device1.setClients(client1)

    projectAppMonitor.clientChanged(client1, CHANGE_NAME)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).isEmpty()
    }
  }

  @Test
  fun clientChanged_alreadyAdded() {
    logcatPresenter.attachedDevice = createDevice(device1.serialNumber)
    val projectAppMonitor = projectAppMonitor(logcatPresenter, "client1")
    device1.setClients(client1)
    projectAppMonitor.deviceChanged(device1, CHANGE_CLIENT_LIST)

    projectAppMonitor.clientChanged(client1, CHANGE_NAME)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(listOf(client1.startedMessage()))
    }
  }

  @Test
  fun clientChanged_thenRemoved() {
    logcatPresenter.attachedDevice = createDevice(device1.serialNumber)
    val projectAppMonitor = projectAppMonitor(logcatPresenter, "client1")

    device1.setClients(client1)
    projectAppMonitor.clientChanged(client1, CHANGE_NAME)
    device1.setClients()
    projectAppMonitor.deviceChanged(device1, CHANGE_CLIENT_LIST)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(
        listOf(client1.startedMessage()),
        listOf(client1.endedMessage()),
      )
    }
  }

  @Test
  fun clientAddedWithoutPackageNameAndThenUpdated() {
    logcatPresenter.attachedDevice = createDevice(device1.serialNumber)
    val projectAppMonitor = projectAppMonitor(logcatPresenter, "client", "client2")
    val client = client(1, "")
    device1.setClients(client, client2)
    projectAppMonitor.deviceChanged(device1, CHANGE_CLIENT_LIST)

    whenever(client.clientData.packageName).thenReturn("client")
    projectAppMonitor.clientChanged(client, CHANGE_NAME)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(
        listOf(client2.startedMessage()),
        listOf(client.startedMessage()),
      )
    }
  }
}

private fun mockDevice(serialNumber: String) : IDevice {
  val device = mock<IDevice>()
  whenever(device.serialNumber).thenReturn(serialNumber)
  whenever(device.clients).thenReturn(emptyArray())
  return device
}

private fun IDevice.setClients(vararg clients: Client) {
  whenever(this.clients).thenReturn(clients)
  clients.forEach {
    whenever(it.device).thenReturn(this)
  }
}

private fun client(pid: Int, packageName: String): Client {
  val clientData = mock<ClientData>()
  whenever(clientData.pid).thenReturn(pid)
  whenever(clientData.packageName).thenReturn(packageName)

  val client = mock<Client>()
  whenever(client.clientData).thenReturn(clientData)
  whenever(client.toString()).thenReturn("pid=$pid packageName=$packageName")

  return client
}

private fun projectAppMonitor(logcatPresenter: LogcatPresenter, vararg packageNames: String) =
  ProjectAppMonitor(logcatPresenter, FakePackageNamesProvider(*packageNames))

private fun createDevice(serialNumber: String): Device {
  return Device.createPhysical(serialNumber, true, "11", 30, "Google", "Pixel")
}
