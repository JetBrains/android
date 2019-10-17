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
package com.android.tools.idea.appinspection.transport

import com.android.tools.app.inspection.AppInspection.AppInspectionCommand
import com.android.tools.app.inspection.AppInspection.DisposeInspectorCommand
import com.android.tools.app.inspection.AppInspection.RawCommand
import com.android.tools.app.inspection.AppInspection.ServiceResponse
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.profiler.proto.Common.Event
import com.android.tools.profiler.proto.Common.Event.Kind.PROCESS
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Two-way connection for the [AppInspectorClient] which implements [AppInspectorClient.CommandMessenger] and dispatches events for the
 * [AppInspectorClient.EventListener].
 */
internal class AppInspectorConnection(
  private val transport: AppInspectionTransport,
  private val inspectorId: String
) : AppInspectorClient.CommandMessenger {
  private val pendingCommands = ConcurrentHashMap<Int, SettableFuture<ByteArray>>()
  private val connectionClosedMessage = "Failed to send a command because the $inspectorId connection is already closed."
  private val disposeCalled = AtomicBoolean(false)
  private var isDisposed = AtomicBoolean(false)
  private val disposeFuture = SettableFuture.create<ServiceResponse>()

  var clientEventListener: AppInspectorClient.EventListener? = null
    set(value) {
      field = value
      transport.poller.registerListener(inspectorEventListener)
    }

  private val inspectorEventListener = transport.createEventListener(
    executor = MoreExecutors.directExecutor(),
    filter = { event: Event ->
      (event.appInspectionEvent.commandId == 0
       && event.appInspectionEvent.hasRawEvent()
       && event.appInspectionEvent.rawEvent.inspectorId == inspectorId)
      || (event.appInspectionEvent.hasCrashEvent() && event.appInspectionEvent.crashEvent.inspectorId == inspectorId)
    }
  ) { event ->
    val appInspectionEvent = event.appInspectionEvent
    when {
      appInspectionEvent.hasRawEvent() -> {
        val content = appInspectionEvent.rawEvent.content.toByteArray()
        clientEventListener!!.onRawEvent(content)
      }
      appInspectionEvent.hasCrashEvent() -> {
        // Remove inspector's listener if it crashes
        cleanup("Inspector $inspectorId has crashed.")
        clientEventListener!!.onCrashEvent(appInspectionEvent.crashEvent.errorMessage)
      }
    }
    false
  }

  private val responsesListener = transport.createEventListener(
    executor = MoreExecutors.directExecutor(),
    filter = { it.appInspectionEvent.commandId != 0 && it.appInspectionEvent.hasRawEvent() }
  ) { event ->
    pendingCommands.remove(event.appInspectionEvent.commandId)?.set(event.appInspectionEvent.rawEvent.content.toByteArray())
    false
  }

  private val processEndListener = transport.createEventListener(
    eventKind = PROCESS,
    executor = MoreExecutors.directExecutor()) {
    if (it.isEnded) {
      cleanup("Inspector $inspectorId was disposed, because app process terminated.")
    }
    it.isEnded
  }

  init {
    transport.poller.registerListener(responsesListener)
    transport.poller.registerListener(processEndListener)
  }

  override fun disposeInspector(): ListenableFuture<ServiceResponse> {
    return disposeFuture.also {
      if (disposeCalled.compareAndSet(false, true)) {
        val disposeInspectorCommand = DisposeInspectorCommand.newBuilder().setInspectorId(inspectorId).build()
        val appInspectionCommand = AppInspectionCommand.newBuilder().setDisposeInspectorCommand(disposeInspectorCommand).build()
        val commandId = transport.executeCommand(appInspectionCommand)
        val listener = transport.registerEventListener(MoreExecutors.directExecutor(), { it.appInspectionEvent.commandId == commandId }) {
          disposeFuture.set(it.appInspectionEvent.response)
          cleanup("Inspector $inspectorId was disposed.")
          // we manually call unregister, because future can be completed from other places, so we clean up the listeners there
          false
        }
        disposeFuture.addListener(Runnable {
          transport.poller.unregisterListener(listener)
        }, MoreExecutors.directExecutor())
      }
    }
  }

  override fun sendRawCommand(rawData: ByteArray): ListenableFuture<ByteArray> {
    if (isDisposed.get()) {
      return Futures.immediateFailedFuture(IllegalStateException(connectionClosedMessage))
    }
    val settableFuture = SettableFuture.create<ByteArray>()
    val rawCommand = RawCommand.newBuilder().setInspectorId(inspectorId).setContent(ByteString.copyFrom(rawData)).build()
    val appInspectionCommand = AppInspectionCommand.newBuilder().setRawInspectorCommand(rawCommand).build()
    val commandId = transport.executeCommand(appInspectionCommand)
    pendingCommands[commandId] = settableFuture

    // cleanup() might have gotten called from a different thread, so we double-check if connection is disposed by now.
    // if it is disposed, then there is a race between pendingCommands.clear / future completion in cleanup method and
    // "pendingCommands[commandId] =" in this method. To make sure that a future isn't leaked, we remove it ourselves from the map
    // and complete it with an exception.
    // if it isn't disposed, then cleanup didn't happen yet and it will be able properly clear [pendingCommands].
    if (isDisposed.get()) {
      pendingCommands.remove(commandId)
      settableFuture.setException(IllegalStateException(connectionClosedMessage))
    }

    return settableFuture
  }

  private fun cleanup(message: String) {
    if (isDisposed.compareAndSet(false, true)) {
      transport.poller.unregisterListener(inspectorEventListener)
      transport.poller.unregisterListener(processEndListener)
      transport.poller.unregisterListener(responsesListener)
      pendingCommands.values.forEach { it.setException(RuntimeException(message)) }
      pendingCommands.clear()
      if (!disposeFuture.isDone) {
        disposeFuture.setException(RuntimeException(message))
      }
      clientEventListener!!.onDispose()
    }
  }
}