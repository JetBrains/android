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
package com.android.tools.idea.appinspection.internal

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.app.inspection.AppInspection
import com.android.tools.app.inspection.AppInspection.AppInspectionCommand
import com.android.tools.app.inspection.AppInspection.DisposeInspectorCommand
import com.android.tools.app.inspection.AppInspection.RawCommand
import com.android.tools.idea.appinspection.inspector.api.AppInspectionConnectionException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionCrashException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorForcefullyDisposedException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION_EVENT
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION_PAYLOAD
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION_RESPONSE
import com.android.tools.profiler.proto.Common.Event.Kind.PROCESS
import com.android.tools.profiler.proto.Transport
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Represents a request to send [appInspectionCommand] to the inspector on device. [completer] can
 * be optionally null, meaning the caller does not care about the response.
 */
private class InspectorCommand(
  val appInspectionCommand: AppInspectionCommand,
  val completer: CompletableDeferred<AppInspection.AppInspectionResponse>?,
)

/**
 * An actor that listens to [InspectorCommand] and sends service/raw commands to the inspector on
 * device, and sets the responses in deferred object provided in the command.
 *
 * When the channel is closed, it will set all pending commands exceptionally.
 */
private fun CoroutineScope.commandSender(
  commands: ReceiveChannel<InspectorCommand>,
  transport: AppInspectionTransport,
  connectionStartTimeNs: Long,
  inspectorId: String,
) = launch {
  val pendingCommands =
    ConcurrentHashMap<Int, CompletableDeferred<AppInspection.AppInspectionResponse>>()
  launch {
    transport
      .eventFlow(
        eventKind = APP_INSPECTION_RESPONSE,
        filter = { it.hasAppInspectionResponse() },
        startTimeNs = { connectionStartTimeNs },
      )
      .collect {
        pendingCommands
          .remove(it.event.appInspectionResponse.commandId)
          ?.complete(it.event.appInspectionResponse)
      }
  }

  try {
    for (command in commands) {
      if (command.completer != null) {
        pendingCommands[command.appInspectionCommand.commandId] = command.completer
      }
      transport.executeCommand(command.appInspectionCommand)
    }
  } catch (e: AppInspectionConnectionException) {
    // We receive this exception when the channel is closed.
    pendingCommands.values.forEach { it.completeExceptionally(e) }
  } catch (e: CancellationException) {
    // We receive this exception when the scope in which this actor is launched is cancelled.
    pendingCommands.values.forEach {
      it.completeExceptionally(CancellationException(inspectorDisposedMessage(inspectorId), e))
    }
    throw e
  }
}

private fun inspectorDisposedMessage(inspectorId: String) = "Inspector $inspectorId was disposed."

/**
 * A pass-thru operator that doesn't do anything. However, it will terminate when the provided [job]
 * is completed.
 */
private fun <T> Flow<T>.scopeCollection(job: Job): Flow<T> = callbackFlow {
  job.invokeOnCompletion { cause ->
    when (cause) {
      is CancellationException -> this.cancel(cause)
      null -> close()
      else -> cancel(cause.message!!, cause)
    }
  }
  collect { send(it) }
  awaitClose()
}

/**
 * Two-way connection for the [AppInspectorMessenger] which implements [AppInspectorMessenger] and
 * dispatches events for it.
 */
internal class AppInspectorConnection(
  private val transport: AppInspectionTransport,
  private val inspectorId: String,
  private val connectionStartTimeNs: Long,
  parentScope: CoroutineScope,
) : AppInspectorMessenger {
  override val scope = parentScope.createChildScope(false)
  private val disposeCalled = AtomicBoolean(false)
  private var isDisposed = AtomicBoolean(false)
  private val commandChannel = Channel<InspectorCommand>()

  override val eventFlow =
    transport
      .eventFlow(
        eventKind = APP_INSPECTION_EVENT,
        filter = { event ->
          event.hasAppInspectionEvent() &&
            event.appInspectionEvent.inspectorId == inspectorId &&
            event.appInspectionEvent.hasRawEvent()
        },
        startTimeNs = { connectionStartTimeNs },
      )
      .map {
        val rawEvent = it.event.appInspectionEvent.rawEvent
        when (rawEvent.dataCase) {
          AppInspection.RawEvent.DataCase.CONTENT -> rawEvent.content.toByteArray()
          AppInspection.RawEvent.DataCase.PAYLOAD_ID -> removePayload(rawEvent.payloadId)
          // This should never happen to users - devs should catch it if we ever add a new case
          else -> throw IllegalStateException("Unhandled event data case: ${rawEvent.dataCase}")
        }
      }
      .scopeCollection(scope.coroutineContext[Job]!!)

  /**
   * Sets the crash and process-end listeners for this inspector. It also starts the [commandSender]
   * actor that facilitates two-way communication between client and the inspector on device.
   */
  init {
    scope.launch(start = CoroutineStart.ATOMIC) {
      try {
        coroutineScope {
          commandSender(commandChannel, transport, connectionStartTimeNs, inspectorId)
        }
      } catch (e: CancellationException) {
        withContext(NonCancellable) { doDispose() }
        throw e
      }
    }
    collectDisposedEvent()
    collectProcessTermination()
  }

  private fun collectDisposedEvent() {
    transport
      .eventFlow(
        eventKind = APP_INSPECTION_EVENT,
        filter = { event ->
          event.hasAppInspectionEvent() && event.appInspectionEvent.inspectorId == inspectorId
        },
        startTimeNs = { connectionStartTimeNs },
      )
      .onEach {
        val appInspectionEvent = it.event.appInspectionEvent
        when {
          appInspectionEvent.hasDisposedEvent() -> {
            if (appInspectionEvent.disposedEvent.errorMessage.isNullOrEmpty()) {
              cleanup(
                AppInspectorForcefullyDisposedException(inspectorDisposedMessage(inspectorId))
              )
            } else {
              cleanup(AppInspectionCrashException("Inspector $inspectorId has crashed."))
            }
          }
        }
      }
      .launchIn(scope)
  }

  private fun collectProcessTermination() {
    transport
      .eventFlow(eventKind = PROCESS, startTimeNs = { connectionStartTimeNs })
      .onEach {
        if (it.event.isEnded) {
          cleanup(
            AppInspectionConnectionException(
              "Inspector $inspectorId was disposed, because app process terminated."
            )
          )
        }
      }
      .launchIn(scope)
  }

  private fun doDispose() {
    if (disposeCalled.compareAndSet(false, true)) {
      val disposeInspectorCommand = DisposeInspectorCommand.newBuilder().build()
      val commandId = AppInspectionTransport.generateNextCommandId()
      val appInspectionCommand =
        AppInspectionCommand.newBuilder()
          .setInspectorId(inspectorId)
          .setDisposeInspectorCommand(disposeInspectorCommand)
          .setCommandId(commandId)
          .build()
      transport.executeCommand(appInspectionCommand)
      cleanup(AppInspectionConnectionException(inspectorDisposedMessage(inspectorId)))
    }
  }

  /**
   * Query the payload, removing it from the datastore at the same time (so obsolete, expensively
   * large data doesn't fill up the cache).
   */
  @Suppress("CheckResult") // deleteEvents returns an empty message, nothing to check
  private fun removePayload(id: Long): ByteArray {
    val response =
      transport.client.transportStub.getEventGroups(
        Transport.GetEventGroupsRequest.newBuilder()
          .setStreamId(transport.process.streamId)
          .setPid(transport.process.pid)
          .setFromTimestamp(connectionStartTimeNs)
          .setKind(APP_INSPECTION_PAYLOAD)
          .setGroupId(id)
          .build()
      )
    transport.client.transportStub.deleteEvents(
      Transport.DeleteEventsRequest.newBuilder()
        .setStreamId(transport.process.streamId)
        .setPid(transport.process.pid)
        .setKind(APP_INSPECTION_PAYLOAD)
        .setGroupId(id)
        .setFromTimestamp(Long.MIN_VALUE)
        .setToTimestamp(Long.MAX_VALUE)
        .build()
    )

    val chunks =
      response.groupsList
        // payload ID is globally unique so there should only be one matching group, but we take
        // most recent just in case
        .last()
        .eventsList
        .reassemble()
        .map { commonEvent -> commonEvent.appInspectionPayload.chunk.toByteArray() }
        .toList()

    return ByteArray(chunks.sumOf { chunk -> chunk.size }).apply {
      var bufferPos = 0
      chunks.forEach { chunk ->
        chunk.copyInto(this, bufferPos)
        bufferPos += chunk.size
      }
    }
  }

  /**
   * Reassemble the events such that the payload can be reconstructed.
   *
   * Some events may be duplicated by the agent (see b/362688559). Remove duplicates and return a
   * list of events that are forming the original payload. On error return an empty list.
   */
  private fun List<Common.Event>.reassemble(): List<Common.Event> {
    if (isEmpty()) {
      return this
    }
    val chunkCount = first().appInspectionPayload.chunkCount
    if (chunkCount == 0) {
      // This may happen if the device is running with an older version of the app inspection agent:
      return this
    } else {
      // There should be exactly [chunkCount] number of events:
      val wantedEvents =
        mutableListOf<Common.Event>().apply {
          repeat(chunkCount) { add(Common.Event.getDefaultInstance()) }
        }
      // Keep the events by the index they were encoded with.
      // If there are duplicates: keep the latest version.
      forEach {
        val index = it.appInspectionPayload.chunkIndex
        if (index >= chunkCount) {
          // If an index is out of range: abort and return an empty list:
          return emptyList()
        }
        wantedEvents[index] = it
      }
      // If any chunks were missing, return an empty list:
      if (wantedEvents.contains(Common.Event.getDefaultInstance())) {
        return emptyList()
      }
      return wantedEvents
    }
  }

  private suspend fun cancelCommand(commandId: Int) {
    val cancellationCommand =
      AppInspectionCommand.newBuilder()
        .setInspectorId(inspectorId)
        .setCancellationCommand(
          AppInspection.CancellationCommand.newBuilder().setCancelledCommandId(commandId).build()
        )
        .build()
    commandChannel.send(InspectorCommand(cancellationCommand, null))
  }

  @WorkerThread
  override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
    val rawCommand = RawCommand.newBuilder().setContent(ByteString.copyFrom(rawData)).build()
    val commandId = AppInspectionTransport.generateNextCommandId()
    val appInspectionCommand =
      AppInspectionCommand.newBuilder()
        .setInspectorId(inspectorId)
        .setRawInspectorCommand(rawCommand)
        .setCommandId(commandId)
        .build()
    val response = CompletableDeferred<AppInspection.AppInspectionResponse>()
    commandChannel.send(InspectorCommand(appInspectionCommand, response))

    try {
      val rawResponse = response.await().rawResponse
      return when (rawResponse.dataCase) {
        AppInspection.RawResponse.DataCase.CONTENT -> rawResponse.content.toByteArray()
        AppInspection.RawResponse.DataCase.PAYLOAD_ID -> removePayload(rawResponse.payloadId)
        // This should never happen to users - devs should catch it if we ever add a new case
        else -> throw IllegalStateException("Unhandled response data case: ${rawResponse.dataCase}")
      }
    } catch (e: CancellationException) {
      withContext(NonCancellable) {
        try {
          cancelCommand(commandId)
        } catch (_: Exception) {
          // The channel may be closed to sending, so we swallow the exception here
          // in order to throw the original exception below.
        }
      }
      throw e
    }
  }

  /**
   * Cleans up inspector connection by unregistering listeners and closing the channel to
   * [commandSender] actor. All futures are completed exceptionally with [cause.message].
   */
  private fun cleanup(cause: Throwable) {
    if (isDisposed.compareAndSet(false, true)) {
      val exception = CancellationException(cause.message, cause)
      commandChannel.cancel(exception)
      scope.cancel(exception)
    }
  }
}
