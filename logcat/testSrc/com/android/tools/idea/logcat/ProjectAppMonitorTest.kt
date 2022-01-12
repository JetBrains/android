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
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.runInEdtAndWait
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`

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

  private val device = mock<IDevice>().apply { setClients() }
  private val logcatPresenter = FakeLogcatPresenter()

  @After
  fun tearDown() {
    Disposer.dispose(logcatPresenter)
  }

  @Test
  fun deviceChanged_addClients() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, device, "client1", "client2")

    device.setClients(client1, client2, client3)
    projectAppMonitor.deviceChanged(device, CHANGE_CLIENT_LIST)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(listOf(client1.startedMessage(), client2.startedMessage()))
    }
  }

  @Test
  fun deviceChanged_addMoreClients() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, device, "client1", "client2", "client3")
    device.setClients(client1)
    projectAppMonitor.deviceChanged(device, CHANGE_CLIENT_LIST)

    device.setClients(client1, client2, client3)
    projectAppMonitor.deviceChanged(device, CHANGE_CLIENT_LIST)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(
        listOf(client1.startedMessage()),
        listOf(client2.startedMessage(), client3.startedMessage()),
      )
    }
  }

  @Test
  fun deviceChanged_removeClients() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, device, "client1", "client2", "client3")
    device.setClients(client1, client2, client3)
    projectAppMonitor.deviceChanged(device, CHANGE_CLIENT_LIST)

    device.setClients(client3)
    projectAppMonitor.deviceChanged(device, CHANGE_CLIENT_LIST)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(
        listOf(client1.startedMessage(), client2.startedMessage(), client3.startedMessage()),
        listOf(client1.endedMessage(), client2.endedMessage()),
      )
    }
  }

  @Test
  fun deviceChanged_addAndRemoveClients() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, device, "client1", "client2", "client3")
    device.setClients(client1, client2)
    projectAppMonitor.deviceChanged(device, CHANGE_CLIENT_LIST)

    device.setClients(client2, client3)
    projectAppMonitor.deviceChanged(device, CHANGE_CLIENT_LIST)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(
        listOf(client1.startedMessage(), client2.startedMessage()),
        listOf(client3.startedMessage(), client1.endedMessage()),
      )
    }
  }

  @Test
  fun deviceChanged_noChanges() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, device, "client1", "client2")
    device.setClients(client1, client2)
    projectAppMonitor.deviceChanged(device, CHANGE_CLIENT_LIST)

    device.setClients(client2, client1)
    projectAppMonitor.deviceChanged(device, CHANGE_CLIENT_LIST)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(
        listOf(client1.startedMessage(), client2.startedMessage()),
      )
    }
  }

  @Test
  fun deviceChanged_wrongMask() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, device, "client1", "client2")

    device.setClients(client1, client2)
    projectAppMonitor.deviceChanged(device, CHANGE_STATE)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).isEmpty()
    }
  }

  @Test
  fun deviceChanged_wrongDevice() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, device, "client1", "client2")
    val otherDevice = mock<IDevice>()

    otherDevice.setClients(client1, client2)
    projectAppMonitor.deviceChanged(device, CHANGE_STATE)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).isEmpty()
    }
  }

  @Test
  fun clientChanged() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, device, "client1", "client2")
    device.setClients(client1)

    projectAppMonitor.clientChanged(client1, CHANGE_NAME)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(listOf(client1.startedMessage()))
    }
  }

  @Test
  fun clientChanged_wrongMask() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, device, "client1", "client2")
    device.setClients(client1)

    projectAppMonitor.clientChanged(client1, CHANGE_DEBUGGER_STATUS)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).isEmpty()
    }
  }

  @Test
  fun clientChanged_wrongDevice() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, device, "client1", "client2")
    val otherDevice = mock<IDevice>()
    otherDevice.setClients(client1)

    projectAppMonitor.clientChanged(client1, CHANGE_NAME)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).isEmpty()
    }
  }

  @Test
  fun clientChanged_wrongPackage() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, device, "client2")
    device.setClients(client1)

    projectAppMonitor.clientChanged(client1, CHANGE_NAME)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).isEmpty()
    }
  }

  @Test
  fun clientChanged_alreadyAdded() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, device, "client1")
    device.setClients(client1)
    projectAppMonitor.deviceChanged(device, CHANGE_CLIENT_LIST)

    projectAppMonitor.clientChanged(client1, CHANGE_NAME)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(listOf(client1.startedMessage()))
    }
  }

  @Test
  fun clientChanged_thenRemoved() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, device, "client1")

    device.setClients(client1)
    projectAppMonitor.clientChanged(client1, CHANGE_NAME)
    device.setClients()
    projectAppMonitor.deviceChanged(device, CHANGE_CLIENT_LIST)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(
        listOf(client1.startedMessage()),
        listOf(client1.endedMessage()),
      )
    }
  }

  @Test
  fun clientAddedWithoutPackageNameAndThenUpdated() {
    val projectAppMonitor = projectAppMonitor(logcatPresenter, device, "client", "client2")
    val client = client(1, "")
    device.setClients(client, client2)
    projectAppMonitor.deviceChanged(device, CHANGE_CLIENT_LIST)

    `when`(client.clientData.packageName).thenReturn("client")
    projectAppMonitor.clientChanged(client, CHANGE_NAME)

    runInEdtAndWait {
      assertThat(logcatPresenter.messageBatches).containsExactly(
        listOf(client2.startedMessage()),
        listOf(client.startedMessage()),
      )
    }
  }
}

private fun IDevice.setClients(vararg clients: Client) {
  `when`(this.clients).thenReturn(clients)
  clients.forEach {
    `when`(it.device).thenReturn(this)
  }
}

private fun client(pid: Int, packageName: String): Client {
  val clientData = mock<ClientData>()
  `when`(clientData.pid).thenReturn(pid)
  `when`(clientData.packageName).thenReturn(packageName)

  val client = mock<Client>()
  `when`(client.clientData).thenReturn(clientData)
  `when`(client.toString()).thenReturn("pid=$pid packageName=$packageName")

  return client
}

private fun projectAppMonitor(logcatPresenter: LogcatPresenter, device: IDevice, vararg packageNames: String) =
  ProjectAppMonitor(logcatPresenter, FakePackageNamesProvider(*packageNames), device)