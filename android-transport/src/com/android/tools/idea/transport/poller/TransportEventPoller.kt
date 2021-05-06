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
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Encapsulates most of the polling functionality that Transport Pipeline subscribers would need to implement
 * to listen for updates and Events coming in from the pipeline
 */
class TransportEventPoller(
  private val transportClient: TransportServiceGrpc.TransportServiceBlockingStub,
  private val sortOrder: Comparator<Common.Event> = Comparator.comparing(Common.Event::getTimestamp)
) {
  private val writeLock = Object()
  private val eventListeners: MutableList<TransportEventListener> = CopyOnWriteArrayList() // Used to preserve insertion order
  private val listenersToLastTimestamp = ConcurrentHashMap<TransportEventListener, Long>()

  /**
   * Adds a listener to the list to poll for and be notified of changes. Listeners are polled in insertion order.
   */
  fun registerListener(listener: TransportEventListener) {
    synchronized(writeLock) {
      eventListeners.add(listener)
      listenersToLastTimestamp[listener] = Long.MIN_VALUE
    }
  }

  /**
   * Removes a listener from the list, or do nothing if it is not in the list
   */
  fun unregisterListener(listener: TransportEventListener) {
    synchronized(writeLock) {
      eventListeners.remove(listener)
      listenersToLastTimestamp.remove(listener)
    }
  }

  fun poll() {
    // Copy the list so we can remove listeners within the loop in-place.
    val listeners = mutableListOf<TransportEventListener>().apply { addAll(eventListeners) }
    // Poll for each listener
    for (eventListener in listeners) {
      // Use start/end time if available
      val startTimestamp = listenersToLastTimestamp[eventListener] ?: eventListener.startTime?.invoke() ?: Long.MIN_VALUE
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
        val filtered = response.groupsList
          .flatMap { group -> group.eventsList }
          .sortedWith(sortOrder)
          .filter { event -> event.timestamp >= startTimestamp && eventListener.filter(event) }
        filtered.forEach { event ->
          eventListener.executor.execute {
            if(eventListener.callback(event)) {
              // Previous code collected the flag and unregistered once in the main thread,
              // but there was a concurrency bug if the main thread finishes before the listeners.
              // We unregister from here instead. Unregistering the same listener multiple times is harmless.
              unregisterListener(eventListener)
            }
          }
        }
        val maxTimeEvent = filtered.maxBy { it.timestamp }
        // Update last timestamp per listener
        synchronized(writeLock) {
          // Make sure the listener is still registered before adding a new timestamp
          if (maxTimeEvent != null && listenersToLastTimestamp.containsKey(eventListener)) {
            listenersToLastTimestamp[eventListener] = max(startTimestamp, maxTimeEvent.timestamp + 1)
          }
        }
      }
    }
  }

  companion object {
    private val myExecutorService: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    private val myScheduledFutures = mutableMapOf<TransportEventPoller, ScheduledFuture<*>>()

    @JvmOverloads
    @JvmStatic
    fun createPoller(transportClient: TransportServiceGrpc.TransportServiceBlockingStub,
                     pollPeriodNs: Long,
                     sortOrder: java.util.Comparator<Common.Event> = Comparator.comparing(Common.Event::getTimestamp),
                     executorServiceForTest: ScheduledExecutorService? = null
    ): TransportEventPoller {
      val poller = TransportEventPoller(transportClient, sortOrder)
      val scheduledFuture = (executorServiceForTest ?: myExecutorService).scheduleWithFixedDelay(
        {
          try {
            poller.poll()
          }
          catch (t: Throwable) {
            Logger.getInstance(TransportEventPoller::class.java).warn(t.toString())
          }
        },
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
