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
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.TransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest
import com.android.tools.profiler.proto.Transport.GetEventGroupsResponse
import com.android.tools.profiler.proto.TransportServiceGrpc
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transform
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicBoolean

const val DELAY_MILLIS = 100L

/** Represents stream connection activity. */
sealed class StreamActivity(val streamChannel: TransportStreamChannel)

/** Represents a stream connect activity. */
class StreamConnected(streamChannel: TransportStreamChannel) : StreamActivity(streamChannel)

/** Represents a stream disconnect activity. */
class StreamDisconnected(streamChannel: TransportStreamChannel) : StreamActivity(streamChannel)

/** This simply wraps [Common.Event] and represents an event from a stream channel. */
class StreamEvent(val event: Common.Event)

private fun TransportServiceGrpc.TransportServiceBlockingStub.eventGroupFlow(
  pollPeriodMs: Long = DELAY_MILLIS,
  requestBuilder: () -> GetEventGroupsRequest
): Flow<GetEventGroupsResponse> = flow {
  while (true) {
    emit(getEventGroups(requestBuilder()))
    delay(pollPeriodMs)
  }
}

@Service(Service.Level.APP)
class TransportStreamManagerService : Disposable {
  private val client: TransportClient
  init {
    // Initialize transport gRpc machinery.
    TransportService.getInstance()
    client = TransportClient(TransportService.channelName)
  }

  val streamManager =
    TransportStreamManager(
      TransportClient(TransportService.channelName).transportStub,
      AndroidCoroutineScope(this, Dispatchers.IO)
    )

  override fun dispose() = Unit
}

/**
 * Listens to and manages STREAM events from the transport pipeline. Users can subscribe to the
 * activity flow to listen in on new streams.
 *
 * Streams are provided in the form of [TransportStreamChannel], which provides users the ability to
 * register their own listeners while automatically associating them with the stream.
 *
 * Please use [TransportStreamManagerService] to obtain instance of [TransportStreamManager] in
 * production code.
 */
class TransportStreamManager
@VisibleForTesting
constructor(
  private val client: TransportServiceGrpc.TransportServiceBlockingStub,
  scope: CoroutineScope
) {

  private val streamState =
    flow {
        while (true) {
          emit(StreamQueryUtils.queryForDevices(client))
          delay(DELAY_MILLIS)
        }
      }
      .shareIn(scope, SharingStarted.Lazily, 1)

  /**
   * Collects the current state of connected devices in the form of a flow of [StreamActivity]
   * events.
   */
  fun streamActivityFlow(): Flow<StreamActivity> = flow {
    val streams = mutableMapOf<Long, TransportStreamChannel>()
    streamState.collect { streamState ->
      streamState.forEach { stream ->
        if (stream.device.state == Common.Device.State.ONLINE) {
          if (!streams.containsKey(stream.streamId)) {
            val streamChannel = TransportStreamChannel(stream, client, Dispatchers.IO)
            emit(StreamConnected(streamChannel))
            streams[stream.streamId] = streamChannel
          }
        } else {
          streams.remove(stream.streamId)?.let { channel ->
            channel.cleanUp()
            emit(StreamDisconnected(channel))
          }
        }
      }
    }
  }
}

/**
 * Represents the transport channel of a stream, which is a device or emulator.
 *
 * Users can subscribe to a flow of events in this stream by providing their own [StreamEventQuery].
 */
@AnyThread
class TransportStreamChannel(
  val stream: Common.Stream,
  val client: TransportServiceGrpc.TransportServiceBlockingStub,
  private val dispatcher: CoroutineDispatcher
) {
  private val isClosed = AtomicBoolean(false)

  /** Creates and returns a flow based on the filtering criteria indicated by [query]. */
  fun eventFlow(query: StreamEventQuery): Flow<StreamEvent> {
    var lastTimeStamp = query.startTime?.invoke() ?: Long.MIN_VALUE
    return client
      .eventGroupFlow {
        val builder =
          GetEventGroupsRequest.newBuilder()
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
          val filtered =
            response.groupsList
              .flatMap { group -> group.eventsList }
              .sortedWith(query.sortOrder)
              .filter { event -> event.timestamp >= lastTimeStamp && query.filter(event) }
          filtered.forEach { event -> emit(StreamEvent(event)) }
          val maxTimeEvent = filtered.maxByOrNull { it.timestamp }
          maxTimeEvent?.let { lastTimeStamp = kotlin.math.max(lastTimeStamp, it.timestamp + 1) }
        }
      }
      .flowOn(dispatcher)
  }

  fun processesFlow(
    filter: (isAlive: Boolean, process: Common.Process) -> Boolean,
    doQuery: suspend () -> List<Common.Process> = {
      StreamQueryUtils.queryForProcesses(client, stream.streamId, filter)
    }
  ) =
    flow<Common.Process> {
        val processes = mutableMapOf<Int, Common.Process>()
        while (true) {
          // The query will return a list of all processes the transport pipeline is aware of,
          // whether they
          // are alive or dead. Therefore, in order to check for updates to the processes' states,
          // we need
          // to reconcile it with the cached state from a previous query.
          val queryResult = doQuery()
          val queriedPids = queryResult.map { it.pid }.toSet()
          val deadProcesses = processes.filterNot { it.key in queriedPids }.values

          // Due to the nature of polling, we might miss DEAD process updates because, for example,
          // the process
          // was restarted. We check for this case by looking for known pids that have disappeared
          // from the
          // current query result.
          deadProcesses.forEach { process ->
            emit(process.toBuilder().setState(Common.Process.State.DEAD).build())
          }

          // Emit updates in the order they were received
          queryResult.forEach { process ->
            if (process.state != processes[process.pid]?.state || process.pid !in processes.keys) {
              emit(process)
            }
          }

          processes.clear()
          processes.putAll(queryResult.associateBy { it.pid })
          delay(DELAY_MILLIS)
        }
      }
      .takeWhile { !isClosed.get() }
      .flowOn(dispatcher)

  internal fun cleanUp() {
    isClosed.set(true)
  }
}
