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

import com.android.tools.app.inspection.AppInspection
import com.android.tools.app.inspection.AppInspection.AppInspectionCommand
import com.android.tools.app.inspection.AppInspection.DisposeInspectorCommand
import com.android.tools.app.inspection.AppInspection.RawCommand
import com.android.tools.idea.appinspection.inspector.api.AppInspectionConnectionException
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION_EVENT
import com.android.tools.profiler.proto.Common.Event.Kind.APP_INSPECTION_RESPONSE
import com.android.tools.profiler.proto.Common.Event.Kind.PROCESS
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
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
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
  val responsesListener = transport.createStreamEventListener(
    eventKind = APP_INSPECTION_RESPONSE,
    filter = { it.hasAppInspectionResponse() },
    startTimeNs = { connectionStartTimeNs }
  ) { event ->
    pendingCommands.remove(event.appInspectionResponse.commandId)?.complete(event.appInspectionResponse)
  }
  transport.registerEventListener(responsesListener)

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
    // We receive this exception when the scope in which this actor is launched is cancelled.
    pendingCommands.values.forEach {
      it.completeExceptionally(AppInspectionConnectionException(inspectorDisposedMessage(inspectorId)))
    }
  }
  finally {
    transport.unregisterEventListener(responsesListener)
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
 * Two-way connection for the [AppInspectorClient] which implements [AppInspectorClient.CommandMessenger] and dispatches events for the
 * [AppInspectorClient.RawEventListener].
 */
internal class AppInspectorConnection(
  private val transport: AppInspectionTransport,
  private val inspectorId: String,
  private val connectionStartTimeNs: Long,
  private val scope: CoroutineScope
) : AppInspectorClient.CommandMessenger {
  private val connectionClosedMessage = "Failed to send a command because the $inspectorId connection is already closed."
  private val disposeCalled = AtomicBoolean(false)
  private var isDisposed = AtomicBoolean(false)
  private val commandChannel = Channel<InspectorCommand>()

  private lateinit var serviceEventNotifier: AppInspectorClient.ServiceEventNotifier

  private val inspectorEventListener = transport.createStreamEventListener(
    eventKind = APP_INSPECTION_EVENT,
    filter = { event -> event.hasAppInspectionEvent() && event.appInspectionEvent.inspectorId == inspectorId },
    startTimeNs = { connectionStartTimeNs }
  ) { event ->
    val appInspectionEvent = event.appInspectionEvent
    when {
      appInspectionEvent.hasCrashEvent() -> {
        // Remove inspector's listener if it crashes
        serviceEventNotifier.notifyCrash(appInspectionEvent.crashEvent.errorMessage)
        cleanup("Inspector $inspectorId has crashed.")
      }
    }
  }

  override val rawEventFlow = callbackFlow<ByteArray> {
    val listener = transport.createStreamEventListener(
      eventKind = APP_INSPECTION_EVENT,
      filter = { event -> event.hasAppInspectionEvent()
                          && event.appInspectionEvent.inspectorId == inspectorId
                          && event.appInspectionEvent.hasRawEvent() },
      startTimeNs = { connectionStartTimeNs }
    ) { event ->
      val appInspectionEvent = event.appInspectionEvent
      val content = appInspectionEvent.rawEvent.content.toByteArray()
      sendBlocking(content)
    }
    transport.registerEventListener(listener)
    awaitClose { transport.unregisterEventListener(listener) }
  }.scopeCollection(scope.coroutineContext[Job]!!)

  private val processEndListener = transport.createStreamEventListener(
    eventKind = PROCESS,
    startTimeNs = { connectionStartTimeNs },
    isTransient = true
  ) {
    if (it.isEnded) {
      cleanup("Inspector $inspectorId was disposed, because app process terminated.")
    }
  }

  /**
   * Sets the active [AppInspectorClient.RawEventListener] and [AppInspectorClient.ServiceEventNotifier] for this connection.
   *
   * This has the side effect of starting all relevant transport listeners, so it should only be called as the last stage of client setup.
   */
  internal fun setupConnection(clientServiceEventNotifier: AppInspectorClient.ServiceEventNotifier) {
    serviceEventNotifier = clientServiceEventNotifier
    transport.registerEventListener(inspectorEventListener)
    transport.registerEventListener(processEndListener)
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

  override fun disposeInspector() {
    scope.cancel()
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
    catch (e: AppInspectionConnectionException) {
      throw AppInspectionConnectionException(connectionClosedMessage)
    }

    try {
      return response.await().rawResponse.content.toByteArray()
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
   * All futures are completed exceptionally with [futureExceptionMessage].
   */
  private fun cleanup(futureExceptionMessage: String) {
    if (isDisposed.compareAndSet(false, true)) {
      val cause = AppInspectionConnectionException(futureExceptionMessage)
      commandChannel.close(cause)
      transport.unregisterEventListener(inspectorEventListener)
      transport.unregisterEventListener(processEndListener)
      serviceEventNotifier.notifyDispose()
      scope.cancel(futureExceptionMessage)
    }
  }
}