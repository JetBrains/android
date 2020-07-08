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
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.guava.await
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Two-way connection for the [AppInspectorClient] which implements [AppInspectorClient.CommandMessenger] and dispatches events for the
 * [AppInspectorClient.RawEventListener].
 */
internal class AppInspectorConnection(
  private val transport: AppInspectionTransport,
  private val inspectorId: String,
  private val connectionStartTimeNs: Long
) : AppInspectorClient.CommandMessenger {
  private val pendingCommands = ConcurrentHashMap<Int, SettableFuture<ByteArray>>()
  private val connectionClosedMessage = "Failed to send a command because the $inspectorId connection is already closed."
  private val disposeCalled = AtomicBoolean(false)
  private var isDisposed = AtomicBoolean(false)
  private val disposeFuture = SettableFuture.create<Unit>()

  private lateinit var rawEventListener: AppInspectorClient.RawEventListener
  private lateinit var serviceEventNotifier: AppInspectorClient.ServiceEventNotifier

  private val inspectorEventListener = transport.createStreamEventListener(
    eventKind = APP_INSPECTION_EVENT,
    filter = { event -> event.hasAppInspectionEvent() && event.appInspectionEvent.inspectorId == inspectorId },
    startTimeNs = { connectionStartTimeNs }
  ) { event ->
    val appInspectionEvent = event.appInspectionEvent
    when {
      appInspectionEvent.hasRawEvent() -> {
        val content = appInspectionEvent.rawEvent.content.toByteArray()
        rawEventListener.onRawEvent(content)
      }
      appInspectionEvent.hasCrashEvent() -> {
        // Remove inspector's listener if it crashes
        serviceEventNotifier.notifyCrash(appInspectionEvent.crashEvent.errorMessage)
        cleanup("Inspector $inspectorId has crashed.")
      }
    }
  }

  private val responsesListener = transport.createStreamEventListener(
    eventKind = APP_INSPECTION_RESPONSE,
    filter = { it.hasAppInspectionResponse() && it.appInspectionResponse.hasRawResponse() },
    startTimeNs = { connectionStartTimeNs }
  ) { event ->
    pendingCommands.remove(event.appInspectionResponse.commandId)?.set(event.appInspectionResponse.rawResponse.content.toByteArray())
  }

  private val processEndListener = transport.createStreamEventListener(
    eventKind = PROCESS,
    startTimeNs = { connectionStartTimeNs },
    isTransient = true
  ) {
    if (it.isEnded) {
      cleanup("Inspector $inspectorId was disposed, because app process terminated.")
    }
    it.isEnded
  }

  /**
   * Sets the active [AppInspectorClient.RawEventListener] and [AppInspectorClient.ServiceEventNotifier] for this connection.
   *
   * This has the side effect of starting all relevant transport listeners, so it should only be called as the last stage of client setup.
   */
  internal fun setEventListeners(clientRawEventListener: AppInspectorClient.RawEventListener,
                                 clientServiceEventNotifier: AppInspectorClient.ServiceEventNotifier) {
    rawEventListener = clientRawEventListener
    serviceEventNotifier = clientServiceEventNotifier
    transport.registerEventListener(inspectorEventListener)
    transport.registerEventListener(responsesListener)
    transport.registerEventListener(processEndListener)
  }

  override suspend fun disposeInspector() {
    if (disposeCalled.compareAndSet(false, true)) {
      val disposeInspectorCommand = DisposeInspectorCommand.newBuilder().build()
      val appInspectionCommand = AppInspectionCommand.newBuilder()
        .setInspectorId(inspectorId)
        .setDisposeInspectorCommand(disposeInspectorCommand)
        .build()
      val commandId = transport.executeCommand(appInspectionCommand)
      val listener = transport.createStreamEventListener(
        eventKind = APP_INSPECTION_RESPONSE,
        filter = { it.hasAppInspectionResponse() && it.appInspectionResponse.commandId == commandId },
        startTimeNs = { connectionStartTimeNs }
      ) {
        cleanup("Inspector $inspectorId was disposed.", it.appInspectionResponse)
        // we manually call unregister, because future can be completed from other places, so we clean up the listeners there
      }
      transport.registerEventListener(listener)
      disposeFuture.addListener(Runnable {
        transport.unregisterEventListener(listener)
      }, MoreExecutors.directExecutor())
    }
    return disposeFuture.await()
  }

  private fun sendCancellationCommand(commandId: Int) {
    val cancellationCommand = AppInspectionCommand.newBuilder()
      .setInspectorId(inspectorId)
      .setCancellationCommand(
        AppInspection.CancellationCommand.newBuilder()
          .setCancelledCommandId(commandId)
          .build()
      )
      .build()
    transport.executeCommand(cancellationCommand)
  }

  override suspend fun sendRawCommand(rawData: ByteArray): ByteArray {
    if (isDisposed.get()) {
      throw AppInspectionConnectionException(connectionClosedMessage)
    }
    val settableFuture = SettableFuture.create<ByteArray>()
    val rawCommand = RawCommand.newBuilder().setContent(ByteString.copyFrom(rawData)).build()
    val appInspectionCommand =
      AppInspectionCommand.newBuilder()
        .setInspectorId(inspectorId)
        .setRawInspectorCommand(rawCommand)
        .build()
    val commandId = transport.executeCommand(appInspectionCommand)
    pendingCommands[commandId] = settableFuture

    // cleanup() might have gotten called from a different thread, so we double-check if connection is disposed by now.
    // if it is disposed, then there is a race between pendingCommands.clear / future completion in cleanup method and
    // "pendingCommands[commandId] =" in this method. To make sure that a future isn't leaked, we remove it ourselves from the map
    // and complete it with an exception.
    // if it isn't disposed, then cleanup didn't happen yet and it will be able properly clear [pendingCommands].
    if (isDisposed.get()) {
      pendingCommands.remove(commandId)
      settableFuture.setException(AppInspectionConnectionException(connectionClosedMessage))
    }

    try {
      return settableFuture.await()
    } catch (e: CancellationException) {
      sendCancellationCommand(commandId)
      throw e
    }
  }

  /**
   * Cleans up inspector connection by unregistering listeners and completing futures.
   * All futures are completed exceptionally with [futureExceptionMessage]. In the case this is
   * called as part of the dispose code path, [disposeFuture] is completed with [disposeResponse].
   */
  private fun cleanup(futureExceptionMessage: String, disposeResponse: AppInspection.AppInspectionResponse? = null) {
    if (isDisposed.compareAndSet(false, true)) {
      transport.unregisterEventListener(inspectorEventListener)
      transport.unregisterEventListener(processEndListener)
      transport.unregisterEventListener(responsesListener)
      pendingCommands.values.forEach { it.setException(AppInspectionConnectionException(futureExceptionMessage)) }
      pendingCommands.clear()
      if (disposeResponse == null) {
        disposeFuture.setException(AppInspectionConnectionException(futureExceptionMessage))
      } else {
        disposeFuture.set(Unit)
      }
      serviceEventNotifier.notifyDispose()
    }
  }
}