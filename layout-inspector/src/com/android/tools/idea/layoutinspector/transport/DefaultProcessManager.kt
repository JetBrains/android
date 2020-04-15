/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.transport

import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.util.ListenerCollection
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A process manager that keeps track of the available processes for the Layout Inspector.
 *
 * This class uses a StreamListener layer to listen for changes in the list of active devices,
 * and their associated processes, reported by the transport layer.
 *
 * [processListeners] gives a notification whenever the data in [processes] is changed
 */
class DefaultProcessManager(
  private val executor: ExecutorService,
  private val client: TransportClient
) : InspectorProcessManager {
  /**
   * Actually not used in AS 4.0.
   */
  override val processListeners = ListenerCollection.createWithDirectExecutor<() -> Unit>()

  /**
   * Contains the currently available process as: stream -> processId -> process
   */
  private val processes = ConcurrentHashMap<Common.Stream, List<Common.Process>>()

  /**
   * Actually not used in AS 4.0.
   */
  override fun isProcessActive(stream: Common.Stream, process: Common.Process): Boolean =
    error("Do not use...")

  /**
   * Get the known active devices with API 29 and above.
   */
  override fun getStreams(): Sequence<Common.Stream> {
    try {
      val future = executor.submit<List<Common.Stream>> { loadDevices() }
      return future.get(400, TimeUnit.MILLISECONDS).asSequence()
    }
    catch (ex: TimeoutException) {
      return processes.keys.asSequence()
    }
    catch (ex: Exception) {
      Logger.getInstance(DefaultProcessManager::class.java).error(ex)
      return processes.keys.asSequence()
    }
  }

  override fun getProcesses(stream: Common.Stream): Sequence<Common.Process> {
    try {
      val future = executor.submit<List<Common.Process>> { loadProcesses(stream) }
      return future.get(400, TimeUnit.MILLISECONDS).asSequence()
    }
    catch (ex: TimeoutException) {
      return processes[stream]?.asSequence().orEmpty()
    }
    catch (ex: Exception) {
      Logger.getInstance(DefaultProcessManager::class.java).error(ex)
      return processes[stream]?.asSequence().orEmpty()
    }
  }

  private fun loadDevices(): List<Common.Stream> {
    // Query for current devices
    val result = mutableListOf<Common.Stream>()
    // Get all streams of all types.
    val request = Transport.GetEventGroupsRequest.newBuilder()
      .setStreamId(-1)  // DataStoreService.DATASTORE_RESERVED_STREAM_ID
      .setKind(Common.Event.Kind.STREAM)
      .build()
    val response = client.transportStub.getEventGroups(request)
    for (group in response.groupsList) {
      val isStreamDead = group.getEvents(group.eventsCount - 1).isEnded
      if (isStreamDead) {
        // Ignore dead streams.
        continue
      }
      val connectedEvent = getLastMatchingEvent(group) { e -> e.hasStream() && e.stream.hasStreamConnected() }
                           ?: // Ignore stream event groups that do not have the connected event.
                           continue
      val stream = connectedEvent.stream.streamConnected.stream
      // We only want streams of type device to get process information.
      if (stream.type == Common.Stream.Type.DEVICE && stream.device.featureLevel >= 29) {
        result.add(stream)
      }
    }
    removeOldStreams(result)
    return result
  }

  private fun removeOldStreams(knownStreams: List<Common.Stream>) {
    processes.keys.retainAll(knownStreams)
    val streams = knownStreams.toMutableSet()
    streams.removeAll(processes.keys)
    streams.forEach { processes.putIfAbsent(it, emptyList()) }
  }

  private fun loadProcesses(stream: Common.Stream): List<Common.Process> {
    val processRequest = Transport.GetEventGroupsRequest.newBuilder()
      .setStreamId(stream.streamId)
      .setKind(Common.Event.Kind.PROCESS)
      .build()
    val processResponse = client.transportStub.getEventGroups(processRequest)
    val processList = ArrayList<Common.Process>()
    // A group is a collection of events that happened to a single process.
    for (groupProcess in processResponse.groupsList) {
      val isProcessDead = groupProcess.getEvents(groupProcess.eventsCount - 1).isEnded
      if (isProcessDead) {
        // Ignore dead processes.
        continue
      }
      val aliveEvent = getLastMatchingEvent(groupProcess) { e -> e.hasProcess() && e.process.hasProcessStarted() }
                       ?: // Ignore process event groups that do not have the started event.
                       continue
      val process = aliveEvent.process.processStarted.process
      processList.add(process)
    }
    processes[stream] = processList
    return processList
  }

  /**
   * Helper method to return the last even in an EventGroup that matches the input condition.
   */
  private fun getLastMatchingEvent(group: Transport.EventGroup, predicate: (Common.Event) -> Boolean): Common.Event? {
    return group.eventsList.lastOrNull { predicate(it) }
  }
}
