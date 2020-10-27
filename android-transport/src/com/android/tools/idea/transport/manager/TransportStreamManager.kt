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
package com.android.tools.idea.transport.manager

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.google.common.annotations.VisibleForTesting
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transform
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

const val DELAY_MILLIS = 100L

/**
 * Represents stream connection activity.
 */
sealed class StreamActivity(val streamChannel: TransportStreamChannel)

/**
 * Represents a stream connect activity.
 */
class StreamConnected(streamChannel: TransportStreamChannel) : StreamActivity(streamChannel)

/**
 * Represents a stream disconnect activity.
 */
class StreamDisconnected(streamChannel: TransportStreamChannel) : StreamActivity(streamChannel)

/**
 * This simply wraps [Common.Event] and represents an event from a stream channel.
 */
class StreamEvent(val event: Common.Event)

/**
 * Listener interface for stream events.
 */
interface TransportStreamListener {
  /**
   * Called when a new stream is connected. The stream could have disconnected at some point in the past, but is connected again.
   */
  fun onStreamConnected(streamChannel: TransportStreamChannel)

  /**
   * Called when a stream is disconnected.
   */
  fun onStreamDisconnected(streamChannel: TransportStreamChannel)
}

private fun TransportServiceGrpc.TransportServiceBlockingStub.eventGroupFlow(
  pollPeriodMs: Long = DELAY_MILLIS,
  requestBuilder: () -> GetEventGroupsRequest
): Flow<GetEventGroupsResponse> = flow {
  while (true) {
    emit(getEventGroups(requestBuilder()))
    delay(pollPeriodMs)
  }
}

/**
 * Manages STREAM events from the transport pipeline. Users can add themselves as a [TransportStreamListener] to be notified of new streams or when
 * an existing stream is disconnected.
 *
 * Streams are provided in the form of [TransportStreamChannel], which provides users the ability to register their own listeners while
 * automatically associating them with the stream.
 */
@AnyThread
class TransportStreamManager private constructor(
  private val client: TransportServiceGrpc.TransportServiceBlockingStub,
  @VisibleForTesting val poller: TransportPoller,
  private val dispatcher: CoroutineDispatcher
) {
  private val streamLock = Any()

  @GuardedBy("streamLock")
  private val streamListeners = mutableMapOf<TransportStreamListener, Executor>()

  @GuardedBy("streamLock")
  private val streams = mutableMapOf<Long, TransportStreamChannel>()

  private val streamsPollingTask = object : PollingTask {
    override fun poll(client: TransportServiceGrpc.TransportServiceBlockingStub): Boolean {
      // Get all streams of all types.
      val request = GetEventGroupsRequest.newBuilder()
        .setStreamId(-1) // DataStoreService.DATASTORE_RESERVED_STREAM_ID
        .setKind(Common.Event.Kind.STREAM)
        .build()
      val response: GetEventGroupsResponse = client.getEventGroups(request)
      for (group in response.groupsList) {
        if (group.eventsCount <= 0) continue
        val streamId = group.groupId
        // sort list by timestamp in descending order
        // the latest event may signal the stream is alive or dead:
        // 1) alive - if stream is new, add stream and notify listeners. otherwise do nothing
        // 2) dead - if stream is dead and manager knows it from before, then remove it and notify listeners. Otherwise do nothing.
        val latestEvent = group.eventsList.maxBy { it.timestamp } ?: continue
        val isConnected = latestEvent.stream.hasStreamConnected()
        synchronized(streamLock) {
          if (isConnected) {
            streams.computeIfAbsent(streamId) {
              val streamChannel = TransportStreamChannel(latestEvent.stream.streamConnected.stream, poller, client, dispatcher)
              streamListeners.forEach { it.value.execute { it.key.onStreamConnected(streamChannel) } }
              streamChannel
            }
          }
          else {
            streams.remove(streamId)?.let { channel ->
              channel.cleanUp()
              streamListeners.forEach { it.value.execute { it.key.onStreamDisconnected(channel) } }
            }
          }
        }
      }
      return false
    }
  }

  fun streamActivityFlow(): Flow<StreamActivity> {
    val streams = mutableMapOf<Long, TransportStreamChannel>()
    // Get all streams of all types.
    val request = GetEventGroupsRequest.newBuilder()
      .setStreamId(-1) // DataStoreService.DATASTORE_RESERVED_STREAM_ID
      .setKind(Common.Event.Kind.STREAM)
      .build()
    return client
      .eventGroupFlow { request }
      .transform { response ->
        for (group in response.groupsList) {
          if (group.eventsCount <= 0) continue
          val streamId = group.groupId
          // sort list by timestamp in descending order
          // the latest event may signal the stream is alive or dead:
          // 1) alive - if stream is new, add stream and notify listeners. otherwise do nothing
          // 2) dead - if stream is dead and manager knows it from before, then remove it and notify listeners. Otherwise do nothing.
          val latestEvent = group.eventsList.maxBy { it.timestamp } ?: continue
          val isConnected = latestEvent.stream.hasStreamConnected()
          if (isConnected) {
            if (!streams.containsKey(streamId)) {
              val streamChannel = TransportStreamChannel(latestEvent.stream.streamConnected.stream, poller, client, dispatcher)
              emit(StreamConnected(streamChannel))
              streams[streamId] = streamChannel
            }
          }
          else {
            streams.remove(streamId)?.let { channel ->
              channel.cleanUp()
              emit(StreamDisconnected(channel))
            }
          }
        }
      }
      .flowOn(dispatcher)
  }

  /**
   * Adds a [TransportStreamListener]. Listener will immediately receive callbacks for currently active streams.
   */
  fun addStreamListener(listener: TransportStreamListener, executor: Executor) {
    synchronized(streamLock) {
      streamListeners[listener] = executor
      streams.values.forEach { executor.execute { listener.onStreamConnected(it) } }
    }
  }

  companion object {
    private val managers = mutableListOf<TransportStreamManager>()

    fun createManager(
      client: TransportServiceGrpc.TransportServiceBlockingStub,
      pollPeriodNs: Long,
      dispatcher: CoroutineDispatcher
    ): TransportStreamManager {
      val poller = TransportPoller.createPoller(client, pollPeriodNs)
      val manager = TransportStreamManager(client, poller, dispatcher)
      poller.registerPollingTask(manager.streamsPollingTask)
      managers.add(manager)
      return manager
    }

    fun unregisterManager(manager: TransportStreamManager) {
      TransportPoller.removePoller(manager.poller)
      managers.remove(manager)
    }
  }
}

/**
 * Represents the transport channel of a stream, which is a device or emulator. It automatically associates user provided
 * [TransportStreamEventListener] with the stream and creates a new [StreamEventPollingTask] and adds it to poller for execution.
 */
@AnyThread
class TransportStreamChannel(
  val stream: Common.Stream,
  val poller: TransportPoller,
  val client: TransportServiceGrpc.TransportServiceBlockingStub,
  private val dispatcher: CoroutineDispatcher
) {

  private val listenersLock = Any()

  @GuardedBy("listenersLock")
  private val listeners = mutableMapOf<TransportStreamEventListener, StreamEventPollingTask>()

  private val isClosed = AtomicBoolean(false)

  /**
   * Once a [TransportStreamEventListener] is added, a [StreamEventPollingTask] will be created based on it and added to the poller for
   * execution. Throws an [IllegalStateException] if channel is closed.
   */
  fun registerStreamEventListener(streamEventListener: TransportStreamEventListener) {
    synchronized(listenersLock) {
      if (isClosed.get()) {
        // It is expected we will hit this case due to timing/race, so we do nothing and quietly return.
        return
      }
      StreamEventPollingTask(stream.streamId, streamEventListener)
        .also {
          poller.registerPollingTask(it)
          listeners[streamEventListener] = it
        }
    }
  }

  fun unregisterStreamEventListener(streamEventListener: TransportStreamEventListener) {
    synchronized(listenersLock) {
      listeners.remove(streamEventListener)?.let { poller.unregisterPollingTask(it) }
    }
  }

  /**
   * Creates and returns a flow based on the filtering criteria indicated by [eventQuery].
   */
  fun eventFlow(query: StreamEventQuery): Flow<StreamEvent> {
    var lastTimeStamp = query.startTime?.invoke() ?: Long.MIN_VALUE
    return client
      .eventGroupFlow {
        val builder = GetEventGroupsRequest.newBuilder()
          .setStreamId(stream.streamId)
          .setKind(query.eventKind)
          .setFromTimestamp(lastTimeStamp)
          .setToTimestamp(query.endTime())
        query.processId?.invoke()?.let { builder.pid = it }
        query.groupId?.invoke()?.let { builder.groupId = it }
        builder.build()
      }
      .takeWhile { !isClosed.get() }
      .transform { response ->
        if (response != GetEventGroupsResponse.getDefaultInstance()) {
          val filtered = response.groupsList
            .flatMap { group -> group.eventsList }
            .sortedWith(query.sortOrder)
            .filter { event -> event.timestamp >= lastTimeStamp && query.filter(event) }
          filtered.forEach { event -> emit(StreamEvent(event)) }
          val maxTimeEvent = filtered.maxBy { it.timestamp }
          maxTimeEvent?.let { lastTimeStamp = kotlin.math.max(lastTimeStamp, it.timestamp + 1) }
        }
      }
      .flowOn(dispatcher)
  }

  internal fun cleanUp() {
    synchronized(listenersLock) {
      if (isClosed.compareAndSet(false, true)) {
        listeners.values.forEach { poller.unregisterPollingTask(it) }
        listeners.clear()
      }
    }
  }
}