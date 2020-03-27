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
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.appinspection.internal.AppInspectionTransport
import com.android.tools.idea.appinspection.internal.attachAppInspectionTarget
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.manager.TransportStreamChannel
import com.android.tools.idea.transport.manager.TransportStreamEventListener
import com.android.tools.idea.transport.manager.TransportStreamListener
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.profiler.proto.Common
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import javax.annotation.concurrent.ThreadSafe

typealias TargetListener = (AppInspectionTarget) -> Unit
typealias JarCopierCreator = (Common.Device) -> AppInspectionJarCopier?

/**
 * A class that hosts an [AppInspectionDiscovery] and manages processes discovered from transport pipeline and other sources.
 *
 * Definition: An inspectable process is a process that has certain inspection flags indicated in its manifest. However, currently it is
 * simply a process that is known by transport pipeline and was launched by studio (signalled via AppInspectionLaunchTaskContributor).
 * TODO(b/148540564): tracks the work needed to make process detection feasible.
 *
 * Processes are discovered by listening to the transport pipeline via [TransportEventListener]. However, because the transport pipeline
 * does not distinguish between inspectable and non-inspectable processes, we use the process information passed by
 * AppInspectionLaunchTaskContributor to identify which process was launched and assume it is inspectable. In other words, this service
 * considers a process inspectable when it is known by both the transport pipeline and the launch task contributor.
 *
 * [addProcessListener] allows the frontend to listen for new inspectable processes. Meant for populating the combobox.
 *
 * [attachToProcess] is used by the frontend to establish an [AppInspectionTarget] when it needs to (ex: when user selects process).
 */
// TODO(b/143628758): This Discovery mechanism must be called only behind the flag SQLITE_APP_INSPECTOR_ENABLED
@ThreadSafe
class AppInspectionDiscoveryHost(
  private val executor: ExecutorService,
  client: TransportClient,
  private val manager: TransportStreamManager,
  private val createJarCopier: JarCopierCreator
) {
  /**
   * Defines a listener that is fired when a new inspectable process is available or an existing one is disconnected.
   */
  interface ProcessListener {
    /**
     * Called when a new process on device is available.
     */
    fun onProcessConnected(descriptor: ProcessDescriptor)

    /**
     * Called when an existing process is disconnected.
     */
    fun onProcessDisconnected(descriptor: ProcessDescriptor)
  }

  val discovery = AppInspectionDiscovery(executor, client)

  private val streamIdMap = ConcurrentHashMap<Long, TransportStreamChannel>()

  private data class StreamProcessIdPair(val streamId: Long, val pid: Int)

  private class ProcessData {
    @GuardedBy("processData")
    val processesMap = mutableMapOf<StreamProcessIdPair, ProcessDescriptor>()

    @GuardedBy("processData")
    val processListeners = mutableMapOf<ProcessListener, Executor>()
  }

  private val processData: ProcessData = ProcessData()

  init {
    registerListenersForDiscovery()
  }

  /**
   * Adds an [ProcessListener] to discovery service. Listener will receive future connections when they come online.
   *
   * This has the side effect of notifying users of all existing live targets the discovery service is aware of.
   */
  fun addProcessListener(
    executor: Executor,
    listener: ProcessListener
  ) {
    synchronized(processData) {
      if (processData.processListeners.putIfAbsent(listener, executor) == null) {
        processData.processesMap.values.forEach { executor.execute { listener.onProcessConnected(it) } }
      }
    }
  }

  /**
   * Attaches to a process on device and creates an [AppInspectionTarget] which will be passed to clients via [TargetListener].
   *
   * This is meant to be called by the frontend when it needs to obtain an [AppInspectionTarget]. For example, when user selects a process
   * from a dropdown of all inspectable processes.
   */
  fun attachToProcess(processDescriptor: ProcessDescriptor): ListenableFuture<AppInspectionTarget> {
    streamIdMap[processDescriptor.stream.streamId]?.let { streamChannel ->
      createJarCopier(processDescriptor.stream.device)?.let { jarCopier ->
        return discovery.attachToProcess(processDescriptor, jarCopier, streamChannel)
      }
    }
    return Futures.immediateFailedFuture(
      RuntimeException("Cannot attach to process because the device does not exist. Process: $processDescriptor"))
  }

  /**
   * Register listeners to receive stream and process events from transport pipeline.
   */
  private fun registerListenersForDiscovery() {
    // Create listener for STREAM connected
    manager.addStreamListener(object : TransportStreamListener {
      override fun onStreamConnected(streamChannel: TransportStreamChannel) {
        streamIdMap[streamChannel.stream.streamId] = streamChannel
        streamChannel.registerStreamEventListener(
          TransportStreamEventListener(
            eventKind = Common.Event.Kind.PROCESS,
            executor = executor,
            filter = { it.process.hasProcessStarted() }
          ) {
            val process = it.process.processStarted.process
            addProcess(streamChannel, process)
          }
        )
        streamChannel.registerStreamEventListener(
          TransportStreamEventListener(
            eventKind = Common.Event.Kind.PROCESS,
            executor = executor,
            filter = { !it.process.hasProcessStarted() }
          ) {
            removeProcess(streamChannel.stream.streamId, it.groupId.toInt())
          }
        )
      }

      override fun onStreamDisconnected(streamChannel: TransportStreamChannel) {
        streamIdMap.remove(streamChannel.stream.streamId)
      }
    }, executor)
  }

  /**
   * Adds a process to internal cache. This is called when transport pipeline is aware of a new process.
   */
  private fun addProcess(streamChannel: TransportStreamChannel, process: Common.Process) {
    synchronized(processData) {
      processData.processesMap.computeIfAbsent(StreamProcessIdPair(streamChannel.stream.streamId, process.pid)) {
        val descriptor = ProcessDescriptor(streamChannel.stream, process)
        if (descriptor.isInspectable()) {
          processData.processListeners.forEach { (listener, executor) -> executor.execute { listener.onProcessConnected(descriptor) } }
        }
        descriptor
      }
    }
  }

  /**
   * Return true if the process it represents is inspectable.
   *
   * Currently, a process is deemed inspectable if the device it's running on is O+ and if it's debuggable. The latter condition is
   * guaranteed to be true because transport pipeline only provides debuggable processes, so there is no need to check.
   */
  private fun ProcessDescriptor.isInspectable(): Boolean {
    // TODO(b/152216552): Support O+ (API 26+) devices. Due to device-side issues, we only support API 29+ devices for now.
    return stream.device.apiLevel >= AndroidVersion.VersionCodes.Q
  }

  /**
   * Removes a process from internal cache. This function is called when a process goes offline.
   */
  private fun removeProcess(streamId: Long, processId: Int) {
    synchronized(processData) {
      processData.processesMap.remove(StreamProcessIdPair(streamId, processId))?.let {
        processData.processListeners.forEach { (listener, executor) -> executor.execute { listener.onProcessDisconnected(it) } }
      }
    }
  }
}

/**
 * A class which keeps track of [AppInspectionTarget] and triggers callbacks on [TargetListener] when new targets are available.
 *
 * This class exposes an [addTargetListener] method that allows clients to add their own listeners to be notified of when new targets become
 * available. The method [attachToProcess] is exposed internally to [AppInspectionDiscoveryHost] and allows it to obtain an
 * [AppInspectionTarget].
 */
// TODO(b/143628758): This Discovery mechanism must be called only behind the flag SQLITE_APP_INSPECTOR_ENABLED
@ThreadSafe
class AppInspectionDiscovery internal constructor(
  private val executor: ExecutorService,
  private val transportClient: TransportClient
) {
  private val lock = Any()

  @GuardedBy("lock")
  private val targetListeners = mutableMapOf<TargetListener, Executor>()

  @GuardedBy("lock")
  private val targets = ConcurrentHashMap<ProcessDescriptor, ListenableFuture<AppInspectionTarget>>()

  /**
   * Adds a [TargetListener]. Target listeners will receive a callback when a new target is available.
   *
   * This has the side effect of notifying users of all existing live targets the discovery service is aware of.
   *
   * This method is synchronized to make sure the listener receives correct callback for existing targets.
   */
  fun addTargetListener(executor: Executor, listener: TargetListener) {
    synchronized(lock) {
      if (targetListeners.putIfAbsent(listener, executor) == null) {
        targets.values.forEach { targetFuture -> targetFuture.transform(executor) { target -> listener(target) } }
      }
    }
  }

  /**
   * Attempts to connect to a process on device specified by [processDescriptor]. Returns a future of [AppInspectionTarget] which can be
   * used to launch inspector connections.
   *
   * Synchronization used here to make sure no new listener is added during the life of this function call, and this gets called atomically.
   */
  internal fun attachToProcess(
    processDescriptor: ProcessDescriptor,
    jarCopier: AppInspectionJarCopier,
    streamChannel: TransportStreamChannel
  ): ListenableFuture<AppInspectionTarget> =
    synchronized(lock) {
      targets.computeIfAbsent(processDescriptor) {
        val transport =
          AppInspectionTransport(transportClient, processDescriptor.stream, processDescriptor.process, executor, streamChannel)
        attachAppInspectionTarget(transport, jarCopier).transform { target ->
          target.addTargetTerminatedListener(executor) { targets.remove(it) }
          targetListeners.forEach { (listener, executor) -> executor.execute { listener(target) } }
          target
        }
      }
    }
}