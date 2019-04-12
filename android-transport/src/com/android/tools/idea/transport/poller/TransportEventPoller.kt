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
package com.android.tools.idea.transport.poller

import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Encapsulates most of the polling functionality that Transport Pipeline subscribers would need to implement
 * to listen for updates and Events coming in from the pipeline
 */
class TransportEventPoller(private val transportClient: TransportServiceGrpc.TransportServiceBlockingStub,
                           private val sortOrder: Comparator<Common.Event>) {
  private val eventListeners: MutableList<TransportEventListener> = CopyOnWriteArrayList() // Used to preserve insertion order
  private val listenersToLastTimestamp = ConcurrentHashMap<TransportEventListener, Long>()

  /**
   * Adds a listener to the list to poll for and be notified of changes. Listeners are polled in insertion order.
   */
  fun registerListener(listener: TransportEventListener) {
    eventListeners.add(listener)
  }

  /**
   * Removes a listener from the list
   */
  fun unregisterListener(listener: TransportEventListener) {
    eventListeners.remove(listener)
    listenersToLastTimestamp.remove(listener)
  }

  fun poll() {
    // Poll for each listener
    for (eventListener in eventListeners) {
      // Use start/end time if available
      val startTimestamp = eventListener.startTime?.invoke() ?: listenersToLastTimestamp.getOrDefault(eventListener, Long.MIN_VALUE)
      val endTimestamp = eventListener.endTime()

      val builder = Transport.GetEventGroupsRequest.newBuilder()
        .setKind(eventListener.eventKind)
        .setFromTimestamp(startTimestamp)
        .setToTimestamp(endTimestamp)
      eventListener.streamId?.invoke()?.let { builder.streamId = it }
      eventListener.processId?.invoke()?.let { builder.pid = it }
      eventListener.groupId?.invoke()?.let { builder.groupId = it }

      val request = builder.build()

      // Order by timestamp
      val response = transportClient.getEventGroups(request)
      if (response != Transport.GetEventGroupsResponse.getDefaultInstance()) {
        val maxTimeEvent = response.groupsList
          .flatMap { group -> group.eventsList }
          .sortedWith(sortOrder)
          .filter { event -> event.timestamp >= startTimestamp && eventListener.filter(event) }
          .maxBy { event ->
            // Dispatch events to listeners
            eventListener.executor.execute { eventListener.callback(event) }
            event.timestamp
          }
        // Update last timestamp per listener
        maxTimeEvent?.let { listenersToLastTimestamp[eventListener] = Math.max(startTimestamp, it.timestamp + 1) }
      }
    }
  }

  companion object {
    private val myExecutorService = Executors.newScheduledThreadPool(1)
    private val myScheduledFutures = mutableMapOf<TransportEventPoller, ScheduledFuture<*>>()

    @JvmOverloads
    @JvmStatic
    fun createPoller(transportClient: TransportServiceGrpc.TransportServiceBlockingStub,
                     pollPeriodNs: Long,
                     sortOrder: java.util.Comparator<Common.Event> = java.util.Comparator.comparing(Common.Event::getTimestamp)
    ): TransportEventPoller {
      val poller = TransportEventPoller(transportClient, sortOrder)
      val scheduledFuture = myExecutorService.scheduleAtFixedRate({ poller.poll() },
                                                                  0, pollPeriodNs, TimeUnit.NANOSECONDS)
      myScheduledFutures[poller] = scheduledFuture
      return poller
    }

    @JvmStatic
    fun stopPoller(poller: TransportEventPoller) {
      myScheduledFutures.remove(poller)?.cancel(false)
    }

  }
}
