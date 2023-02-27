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

import com.android.adblib.AdbLogger
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.tools.idea.adb.AdbAdapter
import com.android.tools.idea.adb.processnamemonitor.ClientMonitorListener.ClientEvent.ClientChanged
import com.android.tools.idea.adb.processnamemonitor.ClientMonitorListener.ClientEvent.ClientListChanged
import com.android.tools.idea.adb.processnamemonitor.DeviceMonitorEvent.Online
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Implementation of ProcessNameMonitorFlows
 */
@Suppress("EXPERIMENTAL_API_USAGE") // Not experimental in main
internal class ProcessNameMonitorFlowsImpl(
  private val adbAdapter: AdbAdapter,
  private val logger: AdbLogger,
  private val context: CoroutineContext = EmptyCoroutineContext,
) : ProcessNameMonitorFlows {
  override fun trackDevices(): Flow<DeviceMonitorEvent> = callbackFlow {
    val listener = DevicesMonitorListener(this, logger)
    adbAdapter.addDeviceChangeListener(listener)

    // Adding a listener does not fire events about existing devices, so we have to add them manually.
    adbAdapter.getDevices().filter { it.isOnline }.forEach {
      trySendBlocking(Online(it))
        .onFailure { e -> logger.warn(e, "Failed to send a DeviceMonitorEvent") }
    }

    awaitClose {
      adbAdapter.removeDeviceChangeListener(listener)
    }
  }.flowOn(context)

  override fun trackClients(device: IDevice): Flow<ClientMonitorEvent> = ClientsEventFlow(device, adbAdapter, logger).flow().flowOn(context)
}

/**
 * Creates a [Flow] of [ClientMonitorEvent]
 *
 * The 2 functions, handleClientListChanged and handleClientChanged, cannot be called concurrently because they are only called from inside
 * the same [FlowCollector] in [ProcessNameMonitorFlowsImpl.trackClients]. This means that we do not need to worry about concurrent access
 * to the fields clients and preInitializedClients.
 *
 * Note that this class could be refactored completely into ProcessNameMonitorFlowsImpl.trackClients but extracting here makes it more
 * readable.
 */
private class ClientsEventFlow(private val device: IDevice, private val adbAdapter: AdbAdapter, private val logger: AdbLogger) {
  private var clients = mutableSetOf<Client>()
  private val preInitializedClients = hashSetOf<Client>()

  @Suppress("EXPERIMENTAL_API_USAGE") // callbackFlow is not experimental in main
  fun flow(): Flow<ClientMonitorEvent> {
    return callbackFlow {
      val listener = ClientMonitorListener(device, this, logger)
      adbAdapter.addDeviceChangeListener(listener)
      adbAdapter.addClientChangeListener(listener)

      // Adding a listener does not fire events about existing clients, so we have to add them manually.
      trySendBlocking(ClientListChanged(device.clients))
        .onFailure { logger.warn(it, "Failed to send a ClientEvent") }

      awaitClose {
        adbAdapter.removeDeviceChangeListener(listener)
        adbAdapter.removeClientChangeListener(listener)
      }
    }.transform {
      when (it) {
        is ClientListChanged -> handleClientListChanged(this, it.clients.toHashSet())
        is ClientChanged -> handleClientChanged(this, it.client)
      }
    }
  }

  suspend fun handleClientListChanged(flow: FlowCollector<ClientMonitorEvent>, newClients: MutableSet<Client>) {
    val addedClients = newClients - clients
    val addedProcessNames = addedClients.associateTo(hashMapOf()) { it.pid() to ProcessNames(it.applicationId(), it.processName()) }
    handlePreInitializedClients(addedProcessNames, newClients)
    val removedClients = clients - newClients
    clients = newClients

    if (addedProcessNames.isNotEmpty() || removedClients.isNotEmpty()) {
      flow.emit(ClientMonitorEvent(addedProcessNames, removedClients.map(Client::pid)))
    }
  }


  suspend fun handleClientChanged(flow: FlowCollector<ClientMonitorEvent>, client: Client) {
    if (preInitializedClients.contains(client)) {
      val pid = client.pid()
      val names = ProcessNames(client.applicationId(), client.processName())
      if (names.isInitialized()) {
        logger.debug { ("Process initialized: $pid -> ($names)") }
        preInitializedClients.remove(client)
        clients.add(client)

        flow.emit(ClientMonitorEvent(mapOf(pid to names), emptyList()))
      }
    }
  }

  private fun handlePreInitializedClients(processNames: MutableMap<Int, ProcessNames>, clients: MutableCollection<Client>) {
    val clientsByPid = clients.associateByTo(hashMapOf(), Client::pid)
    with(processNames.entries.iterator()) {
      while (hasNext()) {
        val (pid, names) = next()
        if (names.isNotInitialized()) {
          logger.debug { "Process $pid is not yet initialized. Skipped..." }
          val client = clientsByPid[pid]
          if (client != null) {
            clients.remove(client)
            preInitializedClients.add(client)
          }
          remove()
        }
      }
    }
  }
}

private fun Client.pid(): Int = clientData.pid
private fun Client.processName(): String = clientData.clientDescription ?: ""
private fun Client.applicationId(): String = clientData.packageName ?: ""
