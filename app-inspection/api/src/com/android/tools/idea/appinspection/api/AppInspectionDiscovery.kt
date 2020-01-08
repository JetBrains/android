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
package com.android.tools.idea.appinspection.api

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.appinspection.internal.AppInspectionTransport
import com.android.tools.idea.appinspection.internal.attachAppInspectionTarget
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.idea.transport.poller.TransportEventPoller
import com.android.tools.profiler.proto.Common
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import javax.annotation.concurrent.ThreadSafe

/**
 * A class that hosts an [AppInspectionDiscovery] instance and exposes a method to initialize its connection.
 *
 * Ideally, a central host service in the calling code will create and connect this discovery and then share just
 * the discovery with interested clients.
 */
// TODO(b/143628758): This Discovery mechanism must be called only behind the flag SQLITE_APP_INSPECTOR_ENABLED
class AppInspectionDiscoveryHost(
  private val executor: ExecutorService,
  client: TransportClient,
  private val poller: TransportEventPoller = TransportEventPoller.createPoller(client.transportStub, TimeUnit.MILLISECONDS.toNanos(100))
) {
  val discovery = AppInspectionDiscovery(executor, client, poller)

  @VisibleForTesting
  internal val streamIdMap = ConcurrentHashMap<Long, Common.Stream>()

  @VisibleForTesting
  internal val processIdMap = ConcurrentHashMap<Long, ProcessDescriptor>()

  init {
    registerListenersForDiscovery()
  }

  /**
   * Shuts down discovery service.
   */
  fun shutDown() {
    discovery.shutDown()
  }

  private fun registerListenersForDiscovery() {
    // Create listener for STREAM connected
    val streamConnectedListener = TransportEventListener(Common.Event.Kind.STREAM, executor, { it.stream.hasStreamConnected() }) {
      val stream = it.stream.streamConnected.stream
      streamIdMap[stream.streamId] = stream
      false
    }
    poller.registerListener(streamConnectedListener)

    // Create listener for STREAM disconnected
    val streamDisconnectedListener = TransportEventListener(Common.Event.Kind.STREAM, executor, { !it.stream.hasStreamConnected() }) {
      // Group ID is the stream ID
      streamIdMap.remove(it.groupId)
      false
    }
    poller.registerListener(streamDisconnectedListener)

    // Create listener for PROCESS started
    val processStartedListener = TransportEventListener(Common.Event.Kind.PROCESS, executor, { it.process.hasProcessStarted() }) {
      // Group ID here is the process ID
      val process = it.process.processStarted.process
      val stream = streamIdMap[process.deviceId] ?: return@TransportEventListener false
      val processDescriptor = ProcessDescriptor(stream, process)
      processIdMap[it.groupId] = processDescriptor
      discovery.addProcess(processDescriptor)
      false
    }
    poller.registerListener(processStartedListener)

    // Create listener for PROCESS stopped
    val processEndedListener = TransportEventListener(Common.Event.Kind.PROCESS, executor, { !it.process.hasProcessStarted() }) {
      // Group ID here is the process ID
      val descriptor = processIdMap[it.groupId] ?: return@TransportEventListener false
      discovery.removeProcess(descriptor)
      processIdMap.remove(it.groupId)
      false
    }
    poller.registerListener(processEndedListener)
  }
}

/**
 * A class which keeps track of a collection of [ProcessDescriptor], representing all of the active processes.
 *
 * This class exposes an [addProcessListener] method that allows users to add their own listeners to be notified of when new processes come
 * online, or when an existing process disconnects.
 *
 * [attachToProcess] allows users to obtain an [AppInspectionTarget] using a [ProcessDescriptor].
 */
// TODO(b/143628758): This Discovery mechanism must be called only behind the flag SQLITE_APP_INSPECTOR_ENABLED
@ThreadSafe
class AppInspectionDiscovery internal constructor(
  private val executor: ExecutorService,
  private val transportClient: TransportClient,
  private val transportPoller: TransportEventPoller
) {
  private data class AppInspectionTargetWrapper(var targetFuture: ListenableFuture<AppInspectionTarget>? = null)

  // Processes map needs to be synchronized with this map when it is being modified.
  @GuardedBy("this")
  private val listeners = mutableMapOf<AppInspectionProcessListener, Executor>()

  // This needs to be synchronized with listeners map when listeners is being modified.
  private val processes = ConcurrentHashMap<ProcessDescriptor, AppInspectionTargetWrapper>()

  @GuardedBy("this")
  private var isShutDown = false

  @VisibleForTesting
  internal val processesForTesting: List<ProcessDescriptor>
    get() = processes.keys.toList()

  internal fun addProcess(descriptor: ProcessDescriptor) {
    synchronized(this) {
      processes.computeIfAbsent(descriptor) {
        listeners.forEach { (listener: AppInspectionProcessListener, executor: Executor) ->
          executor.execute { listener.onProcessConnect(descriptor) }
        }
        AppInspectionTargetWrapper()
      }
    }
  }

  internal fun removeProcess(descriptor: ProcessDescriptor) {
    synchronized(this) {
      val targetWrapper = processes.remove(descriptor)
      if (targetWrapper != null) {
        targetWrapper.targetFuture?.cancel(false)
        listeners.forEach { (listener: AppInspectionProcessListener, executor: Executor) ->
          executor.execute {
            listener.onProcessDisconnect(descriptor)
          }
        }
      }
    }
  }

  /**
   * Adds an [AppInspectionProcessListener] to discovery service. Listener will receive future connections when they come online.
   *
   * This has the side effect of notifying users of all existing live connections the discovery service is aware of.
   */
  fun addProcessListener(listener: AppInspectionProcessListener, executor: Executor): AppInspectionProcessListener {
    synchronized(this) {
      checkIsShutDown()
      listeners.computeIfAbsent(listener) {
        processes.keys.forEach { executor.execute { listener.onProcessConnect(it) } }
        executor
      }
    }
    return listener
  }

  /**
   * Attempts to connect to a process on device specified by [descriptor]. Returns a future of [AppInspectionTarget] which can be used to
   * launch inspector connections.
   */
  fun attachToProcess(descriptor: ProcessDescriptor, jarCopier: AppInspectionJarCopier): ListenableFuture<AppInspectionTarget> {
    return processes.computeIfPresent(descriptor) { _, wrapper ->
      if (wrapper.targetFuture == null) {
        val transport = AppInspectionTransport(transportClient, descriptor.stream, descriptor.process, executor, transportPoller)
        wrapper.targetFuture = attachAppInspectionTarget(transport, jarCopier)
      }
      wrapper
    }?.targetFuture ?: throw NoSuchElementException("AppInspection discovery is not aware of such process!")
  }

  internal fun shutDown() {
    synchronized(this) {
      if (!isShutDown) {
        isShutDown = true
        listeners.clear()
      }
    }
  }

  @GuardedBy("this")
  private fun checkIsShutDown() {
    if (isShutDown) {
      throw IllegalStateException("AppInspection discovery is shut down!")
    }
  }
}

/**
 * Defines a listener that is fired when a new process is available or an existing one is disconnected.
 */
interface AppInspectionProcessListener {
  /**
   * Called when a new process on device is available.
   */
  fun onProcessConnect(processDescriptor: ProcessDescriptor)

  /**
   * Called when an existing process is disconnected.
   */
  fun onProcessDisconnect(processDescriptor: ProcessDescriptor)
}