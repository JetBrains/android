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

import android.annotation.SuppressLint
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
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION_EVENT
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION_PAYLOAD
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION_RESPONSE
import com.android.tools.profiler.proto.Common.Event.Kind.PROCESS
import com.android.tools.profiler.proto.Transport
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean


/**
 * Represents a request to send [appInspectionCommand] to the inspector on device. [completer] can be optionally null, meaning the caller
 * does not care about the response.
 */
private class InspectorCommand(val appInspectionCommand: AppInspectionCommand,
                               val completer: CompletableDeferred<AppInspection.AppInspectionResponse>?)

/**
 * An actor that listens to [InspectorCommand] and sends service/raw commands to the inspector on device, and sets the responses in
 * deferred object provided in the command.
 *
 * When the channel is closed, it will set all pending commands exceptionally.
 */
private fun CoroutineScope.commandSender(commands: ReceiveChannel<InspectorCommand>,
                                         transport: AppInspectionTransport,
                                         connectionStartTimeNs: Long,
                                         inspectorId: String) = launch {
  val pendingCommands = ConcurrentHashMap<Int, CompletableDeferred<AppInspection.AppInspectionResponse>>()
  launch {
    transport.eventFlow(
      eventKind = APP_INSPECTION_RESPONSE,
      filter = { it.hasAppInspectionResponse() },
      startTimeNs = { connectionStartTimeNs }
    ).collect {
      pendingCommands.remove(it.event.appInspectionResponse.commandId)?.complete(it.event.appInspectionResponse)
    }
  }

  try {
    for (command in commands) {
      if (command.completer != null) {
        pendingCommands[command.appInspectionCommand.commandId] = command.completer
      }
      transport.executeCommand(command.appInspectionCommand)
    }
  }
  catch (e: AppInspectionConnectionException) {
    // We receive this exception when the channel is closed.
    pendingCommands.values.forEach {
      it.completeExceptionally(e)
    }
  }
  catch (e: CancellationException) {
    // There exists a window of time between when this coroutine's scope is cancelled and when
    // the channel is closed. Callers can be suspended indefinitely if they try to send a command
    // in that window. That's why we call cancel here to cancel any remaining items and close
    // the channel.
    commands.cancel()
    // We receive this exception when the scope in which this actor is launched is cancelled.
    pendingCommands.values.forEach {
      it.completeExceptionally(AppInspectionConnectionException(inspectorDisposedMessage(inspectorId)))
    }
  }
}

private fun inspectorDisposedMessage(inspectorId: String) = "Inspector $inspectorId was disposed."

/**
 * A pass-thru operator that doesn't do anything. However, it will terminate when the provided [job] is completed.
 */
private fun <T> Flow<T>.scopeCollection(job: Job): Flow<T> = callbackFlow {
  job.invokeOnCompletion { cause ->
    when (cause) {
      is CancellationException -> this.cancel(cause)
      null -> close()
      else -> cancel(cause.message!!, cause)
    }
  }
  collect {
    send(it)
  }
  awaitClose()
}

/**
 * Two-way connection for the [AppInspectorMessenger] which implements [AppInspectorMessenger] and dispatches events for it.
 */
internal class AppInspectorConnection(
  private val transport: AppInspectionTransport,
  private val inspectorId: String,
  private val connectionStartTimeNs: Long,
  parentScope: CoroutineScope
) : AppInspectorMessenger {
  override val scope = parentScope.createChildScope(false)
  private val connectionClosedMessage = "Failed to send a command because the $inspectorId connection is already closed."
  private val disposeCalled = AtomicBoolean(false)
  private var isDisposed = AtomicBoolean(false)
  private val commandChannel = Channel<InspectorCommand>()

  override val eventFlow = transport.eventFlow(
      eventKind = APP_INSPECTION_EVENT,
      filter = { event ->
        event.hasAppInspectionEvent()
        && event.appInspectionEvent.inspectorId == inspectorId
        && event.appInspectionEvent.hasRawEvent()
      },
      startTimeNs = { connectionStartTimeNs }
    ).map {
      val rawEvent = it.event.appInspectionEvent.rawEvent
      when(rawEvent.dataCase) {
        AppInspection.RawEvent.DataCase.CONTENT -> rawEvent.content.toByteArray()
        AppInspection.RawEvent.DataCase.PAYLOAD_ID -> removePayload(rawEvent.payloadId)
        // This should never happen to users - devs should catch it if we ever add a new case
        else -> throw IllegalStateException("Unhandled event data case: ${rawEvent.dataCase}")
      }
    }.scopeCollection(scope.coroutineContext[Job]!!)

  /**
   * Sets the crash and process-end listeners for this inspector. It also starts the [commandSender] actor that facilitates two-way
   * communication between client and the inspector on device.
   */
  init {
    scope.launch(start = CoroutineStart.ATOMIC) {
      try {
        coroutineScope {
          commandSender(commandChannel, transport, connectionStartTimeNs, inspectorId)
        }
      }
      catch (e: CancellationException) {
        withContext(NonCancellable) {
          doDispose()
        }
      }
    }
    collectDisposedEvent()
    collectProcessTermination()
  }

  private fun collectDisposedEvent() {
    transport.eventFlow(
      eventKind = APP_INSPECTION_EVENT,
      filter = { event -> event.hasAppInspectionEvent() && event.appInspectionEvent.inspectorId == inspectorId },
      startTimeNs = { connectionStartTimeNs }
    ).onEach {
      val appInspectionEvent = it.event.appInspectionEvent
      when {
        appInspectionEvent.hasDisposedEvent() -> {
          if (appInspectionEvent.disposedEvent.errorMessage.isNullOrEmpty()) {
            cleanup("Inspector $inspectorId has been disposed.") { message -> AppInspectorForcefullyDisposedException(message) }
          }
          else {
            cleanup("Inspector $inspectorId has crashed.") { message -> AppInspectionCrashException(message) }
          }
        }
      }
    }.launchIn(scope)
  }

  private fun collectProcessTermination() {
    transport.eventFlow(
      eventKind = PROCESS,
      startTimeNs = { connectionStartTimeNs },
    ).onEach {
      if (it.event.isEnded) {
        cleanup("Inspector $inspectorId was disposed, because app process terminated.")
      }
    }.launchIn(scope)
  }

  private suspend fun doDispose() {
    if (disposeCalled.compareAndSet(false, true)) {
      val disposeInspectorCommand = DisposeInspectorCommand.newBuilder().build()
      val commandId = AppInspectionTransport.generateNextCommandId()
      val appInspectionCommand = AppInspectionCommand.newBuilder()
        .setInspectorId(inspectorId)
        .setDisposeInspectorCommand(disposeInspectorCommand)
        .setCommandId(commandId)
        .build()
      transport.executeCommand(appInspectionCommand)
      cleanup(inspectorDisposedMessage(inspectorId))
    }
  }

  /**
   * Query the payload, removing it from the datastore at the same time (so obsolete, expensively
   * large data doesn't fill up the cache).
   */
  @SuppressLint("CheckResult") // deleteEvents returns an empty message, nothing to check
  private fun removePayload(id: Long): ByteArray {
    val response = transport.client.transportStub.getEventGroups(
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

    val chunks = response
      .groupsList
      // payload ID is globally unique so there should only be one matching group, but we take most recent just in case
      .last()
      .eventsList
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

  private suspend fun cancelCommand(commandId: Int) {
    val cancellationCommand = AppInspectionCommand.newBuilder()
      .setInspectorId(inspectorId)
      .setCancellationCommand(
        AppInspection.CancellationCommand.newBuilder()
          .setCancelledCommandId(commandId)
          .build()
      )
      .build()
    commandChannel.send(InspectorCommand(cancellationCommand, null))
  }

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
    try {
      commandChannel.send(InspectorCommand(appInspectionCommand, response))
    }
    catch (e: CancellationException) {
      throw AppInspectionConnectionException(connectionClosedMessage)
    }
    catch (e: AppInspectionConnectionException) {
      throw AppInspectionConnectionException(connectionClosedMessage)
    }

    try {
      val rawResponse = response.await().rawResponse
      return when(rawResponse.dataCase) {
        AppInspection.RawResponse.DataCase.CONTENT -> rawResponse.content.toByteArray()
        AppInspection.RawResponse.DataCase.PAYLOAD_ID -> removePayload(rawResponse.payloadId)
        // This should never happen to users - devs should catch it if we ever add a new case
        else -> throw IllegalStateException("Unhandled response data case: ${rawResponse.dataCase}")
      }
    }
    catch (e: CancellationException) {
      withContext(NonCancellable) {
        cancelCommand(commandId)
      }
      throw e
    }
  }

  /**
   * Cleans up inspector connection by unregistering listeners and closing the channel to [commandSender] actor.
   * All futures are completed exceptionally with [exceptionMessage].
   */
  private fun cleanup(
    exceptionMessage: String,
    createException: (String) -> AppInspectionConnectionException = { AppInspectionConnectionException(it) }
  ) {
    if (isDisposed.compareAndSet(false, true)) {
      val cause = createException(exceptionMessage)
      commandChannel.close(cause)
      scope.cancel(exceptionMessage, cause)
    }
  }
}