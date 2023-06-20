/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.transport.manager

import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport.EventGroup
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest
import com.android.tools.profiler.proto.TransportServiceGrpc

/**
 * Contains common queries used by both profilers and App Inspection to query
 * for streams and processes.
 *
 * Only applicable to unified pipeline.
 */
class StreamQueryUtils {
  companion object {
    /**
     * Queries for all devices, including those that are dead.
     *
     * Returns a list of [Common.Stream] objects of type [Common.Stream.Type.DEVICE].
     */
    @JvmStatic
    fun queryForDevices(client: TransportServiceGrpc.TransportServiceBlockingStub): List<Common.Stream> {
      // Get all streams of all types.
      val request = GetEventGroupsRequest.newBuilder()
        .setStreamId(-1) // DataStoreService.DATASTORE_RESERVED_STREAM_ID
        .setKind(Common.Event.Kind.STREAM)
        .build()
      val response = client.getEventGroups(request)
      return response.groupsList.mapNotNull { group ->
        val isStreamDead = group.getEvents(group.eventsCount - 1).isEnded
        val connectedEvent = group.eventsList.lastOrNull { e -> e.hasStream() && e.stream.hasStreamConnected() }
                             ?: // Ignore stream event groups that do not have the connected event.
                             return@mapNotNull null
        val stream = connectedEvent.stream.streamConnected.stream
        // We only want streams of type device to get process information.
        if (stream.type == Common.Stream.Type.DEVICE) {
          if (isStreamDead) {
            // TODO state changes are represented differently in the unified pipeline (with two separate events)
            // remove this once we move complete away from the legacy pipeline.
            stream.toBuilder().apply {
              device = stream.device.toBuilder().apply {
                state = Common.Device.State.DISCONNECTED
              }.build()
            }.build()
          }
          else {
            stream
          }
        }
        else null
      }
    }

    /**
     * Queries for all the processes belonging to [streamId] the pipeline is aware of.
     *
     * Every process is piped through [filter] to determine if it should be returned.
     */
    @JvmStatic
    fun queryForProcesses(
      client: TransportServiceGrpc.TransportServiceBlockingStub,
      streamId: Long,
      filter: (isAlive: Boolean, lastAliveEvent: Common.Process) -> Boolean
    ): List<Common.Process> {
      val processRequest = GetEventGroupsRequest.newBuilder()
        .setStreamId(streamId)
        .setKind(Common.Event.Kind.PROCESS)
        .build()
      val processResponse = client.getEventGroups(processRequest)
      // A group is a collection of events that happened to a single process.
      return processResponse.groupsList.mapNotNull { groupProcess ->
        val isProcessAlive = !groupProcess.getEvents(groupProcess.eventsCount - 1).isEnded
        // Find the alive event with the highest exposure level for the last alive process.
        // On Q & R, a profileable app's event comes from the daemon, while a debuggable
        // app's event comes from adb track-jdwp through TransportServiceProxy. Every
        // debuggable app is also profileable, so it will be reported twice, and the order
        // of the two events cannot be predicted.
        val highExposureProcess = getHighestExposureEventForLastProcess(groupProcess)?.process?.processStarted?.process
                                  ?: // Ignore process event groups that do not have the started event.
                                  return@mapNotNull null
        if (filter(isProcessAlive, highExposureProcess)) {
          // TODO state changes are represented differently in the unified pipeline (with two separate events)
          // remove this once we move complete away from the legacy pipeline.
          if (isProcessAlive) {
            highExposureProcess
          }
          else {
            highExposureProcess.toBuilder().setState(Common.Process.State.DEAD).build()
          }
        }
        else {
          null
        }
      }
    }

    /**
     * Helper method to return the event of the highest exposure level for the last process in an EventGroup.
     * Note process events are grouped by PIDs, so this method doesn't look beyond the next to last "is-ended" event.
     */
    @JvmStatic
    fun getHighestExposureEventForLastProcess(group: EventGroup): Common.Event? {
      var hasVisitedEndedEvent = false
      var found: Common.Event? = null
      for (i in group.eventsCount - 1 downTo 0) {
        val e = group.getEvents(i)
        if (e.isEnded) {
          if (hasVisitedEndedEvent) {
            break
          }
          hasVisitedEndedEvent = true
        }
        else {
          if (e.hasProcess() && e.process.hasProcessStarted() && e.process.processStarted.hasProcess() &&
              (found == null ||
               e.process.processStarted.process.exposureLevelValue >
               found.process.processStarted.process.exposureLevelValue)) {
            found = e
          }
        }
      }
      return found
    }
  }
}