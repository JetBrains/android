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

import com.android.annotations.concurrency.AnyThread
import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener
import com.android.ddmlib.Client
import com.android.ddmlib.Client.CHANGE_NAME
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.CHANGE_CLIENT_LIST
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.jetbrains.rd.util.concurrentMapOf
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import javax.annotation.concurrent.GuardedBy

/**
 * Listens to [IDeviceChangeListener.deviceChanged] and [IClientChangeListener.clientChanged] and monitors project related client processes
 * start & end event and sends system log messages to the [LogcatPresenter].
 */
internal class ProjectAppMonitor(
  private val logcatPresenter: LogcatPresenter,
  private val projectPackageNamesProvider: PackageNamesProvider,
  private val device: IDevice,
) : IDeviceChangeListener, IClientChangeListener {
  @GuardedBy("itself")
  private val projectClients = mutableMapOf<Int, Client>()

  init {
    // Initialize the projectClients list by triggering a deviceChanged(CHANGE_CLIENT_LIST) event.
    deviceChanged(device, CHANGE_CLIENT_LIST)
  }

  override fun deviceConnected(device: IDevice) {}
  override fun deviceDisconnected(device: IDevice) {}

  @AnyThread
  override fun deviceChanged(device: IDevice, changeMask: Int) {
    if (device != this.device || changeMask and CHANGE_CLIENT_LIST == 0) {
      return
    }

    val newClients = device.clients.filter { it.isProjectClient() }.associateBy { it.clientData.pid }
    val added: List<Client>
    val removed: List<Client>
    synchronized(projectClients) {
      added = (newClients.keys - projectClients.keys).map { newClients[it]!! }
      removed = (projectClients.keys - newClients.keys).map { projectClients[it]!! }
      projectClients.clear()
      projectClients.putAll(newClients)
    }
    val messages = added.map(Client::startedMessage) + removed.map(Client::endedMessage)
    if (messages.isNotEmpty()) {
      AndroidCoroutineScope(logcatPresenter, AndroidDispatchers.uiThread).launch {
        logcatPresenter.processMessages(messages)
      }
    }
  }

  @AnyThread
  override fun clientChanged(client: Client, changeMask: Int) {
    if (changeMask and CHANGE_NAME == 0 || client.device != this.device || !client.isProjectClient()) {
      return
    }
    val added = synchronized(projectClients) {
      projectClients.put(client.clientData.pid, client) == null
    }
    if (added) {
      AndroidCoroutineScope(logcatPresenter, AndroidDispatchers.uiThread).launch {
        logcatPresenter.processMessages(listOf(client.startedMessage()))
      }
    }
  }

  private fun Client.isProjectClient() = clientData.packageName in projectPackageNamesProvider.getPackageNames()
}

@VisibleForTesting
internal fun Client.startedMessage(): LogCatMessage =
  // clientData.packageName cannot be null because if this method was called, is has already satisfied Client.isProjectClient()
  LogCatMessage(SYSTEM_HEADER, LogcatBundle.message("logcat.process.started", clientData.pid.toString(), clientData.packageName!!))

@VisibleForTesting
internal fun Client.endedMessage(): LogCatMessage =
  // clientData.packageName cannot be null because if this method was called, is has already satisfied Client.isProjectClient()
  LogCatMessage(SYSTEM_HEADER, LogcatBundle.message("logcat.process.ended", clientData.pid.toString(), clientData.packageName!!))
