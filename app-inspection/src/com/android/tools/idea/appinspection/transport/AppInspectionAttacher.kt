/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.appinspection.transport

import com.android.ddmlib.IDevice
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportServiceProxy
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.idea.transport.poller.TransportEventPoller
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private const val MAX_RETRY_COUNT = 60

typealias AttachCallback = (Common.Stream, Common.Process) -> Unit

// TODO(b/143628758): This Discovery must be called only behind the flag SQLITE_APP_INSPECTOR_ENABLED
// This  copy pasted from layout inspector (DefaultInspectorClient.kt) with minor changes
internal class AppInspectionAttacher(private val executor: ScheduledExecutorService,
                                     transportChannel: AppInspectionDiscoveryHost.TransportChannel) {
  private var client = TransportClient(transportChannel.channelName)
  private var transportPoller = TransportEventPoller.createPoller(client.transportStub,
                                                                  TimeUnit.MILLISECONDS.toNanos(100),
                                                                  Comparator.comparing(Common.Event::getTimestamp).reversed())

  private var selectedStream: Common.Stream = Common.Stream.getDefaultInstance()
  private var selectedProcess: Common.Process = Common.Process.getDefaultInstance()


  var isConnected = false
    private set

  init {
    registerProcessEnded()
  }

  private fun registerProcessEnded() {
    // TODO: unregister listeners
    transportPoller.registerListener(TransportEventListener(
      eventKind = Common.Event.Kind.PROCESS,
      executor = MoreExecutors.directExecutor(),
      streamId = selectedStream::getStreamId,
      groupId = { selectedProcess.pid.toLong() },
      processId = selectedProcess::getPid) {
      if (selectedStream != Common.Stream.getDefaultInstance() &&
          selectedProcess != Common.Process.getDefaultInstance() && isConnected && it.isEnded) {
        selectedStream = Common.Stream.getDefaultInstance()
        selectedProcess = Common.Process.getDefaultInstance()
        isConnected = false
      }
      false
    })
  }

  private fun loadProcesses(): Map<Common.Stream, List<Common.Process>> {
    // Get all streams of all types.
    val request = Transport.GetEventGroupsRequest.newBuilder()
      .setStreamId(-1)  // DataStoreService.DATASTORE_RESERVED_STREAM_ID
      .setKind(Common.Event.Kind.STREAM)
      .build()
    val response = client.transportStub.getEventGroups(request)
    val streams = response.groupsList.filterNotEnded().mapNotNull { group ->
      group.lastEventOrNull { e -> e.hasStream() && e.stream.hasStreamConnected() }
    }.map { connectedEvent ->
      connectedEvent.stream.streamConnected.stream
    }.filter { stream ->
      stream.type == Common.Stream.Type.DEVICE
    }

    return streams.associateWith { stream ->
      val processRequest = Transport.GetEventGroupsRequest.newBuilder()
        .setStreamId(stream.streamId)
        .setKind(Common.Event.Kind.PROCESS)
        .build()
      val processResponse = client.transportStub.getEventGroups(processRequest)
      val processList = processResponse.groupsList.filterNotEnded().mapNotNull { processGroup ->
        processGroup.lastEventOrNull { e -> e.hasProcess() && e.process.hasProcessStarted() }
      }.map { aliveEvent ->
        aliveEvent.process.processStarted.process
      }
      processList
    }
  }

  /**
   * Attempt to connect to the specified [preferredProcess].
   *
   * The method called will retry itself up to MAX_RETRY_COUNT times.
   */
  fun attach(preferredProcess: AutoPreferredProcess, callback: AttachCallback) {
    executor.execute { attachWithRetry(preferredProcess, callback, 0) }
  }

  private fun attachWithRetry(preferredProcess: AutoPreferredProcess, callback: AttachCallback, timesAttempted: Int) {
    if (selectedStream != Common.Stream.getDefaultInstance() ||
        selectedProcess != Common.Process.getDefaultInstance()) {
      return
    }
    val processesMap = loadProcesses()
    for ((stream, processes) in processesMap) {
      if (preferredProcess.isDeviceMatch(stream.device)) {
        for (process in processes) {
          if (process.name == preferredProcess.packageName) {
            callback(stream, process)
            return
          }
        }
      }
    }
    if (timesAttempted < MAX_RETRY_COUNT) {
      executor.schedule({ attachWithRetry(preferredProcess, callback, timesAttempted + 1) }, 1, TimeUnit.SECONDS)
    }
  }
}

/**
 * Helper method to return the last even in an EventGroup that matches the input condition.
 */
private fun Transport.EventGroup.lastEventOrNull(predicate: (Common.Event) -> Boolean): Common.Event? {
  return eventsList.lastOrNull { predicate(it) }
}

private fun List<Transport.EventGroup>.filterNotEnded(): List<Transport.EventGroup> {
  return filterNot { group -> group.getEvents(group.eventsCount - 1).isEnded }
}

data class AutoPreferredProcess(
  val manufacturer: String,
  val model: String,
  val serialNumber: String,
  val packageName: String?
) {

  constructor(device: IDevice, packageName: String?)
    : this(TransportServiceProxy.getDeviceManufacturer(device),
           TransportServiceProxy.getDeviceModel(device),
           device.serialNumber, packageName)

  /**
   * Returns true if a device from the transport layer matches the device profile stored.
   */
  fun isDeviceMatch(device: Common.Device): Boolean {
    return device.manufacturer == manufacturer &&
           device.model == model &&
           device.serial == serialNumber
  }
}
