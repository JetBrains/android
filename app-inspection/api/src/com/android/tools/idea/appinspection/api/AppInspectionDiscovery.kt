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
import com.android.tools.idea.appinspection.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.api.process.ProcessListener
import com.android.tools.idea.appinspection.api.process.ProcessNotifier
import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.internal.AppInspectionTransport
import com.android.tools.idea.appinspection.internal.attachAppInspectionTarget
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.concurrency.transformAsync
import com.android.tools.idea.transport.TransportClient
import com.android.tools.idea.transport.manager.TransportStreamChannel
import com.android.tools.idea.transport.manager.TransportStreamEventListener
import com.android.tools.idea.transport.manager.TransportStreamListener
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.profiler.proto.Common
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.lang.Long.max
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import javax.annotation.concurrent.ThreadSafe

typealias JarCopierCreator = (Common.Device) -> AppInspectionJarCopier?

/**
 * A class that hosts an [AppInspectionDiscovery] and manages processes discovered from transport pipeline and other sources.
 *
 * Definition: An inspectable process is a process that has certain inspection flags indicated in its manifest. However, currently it is
 * simply a process that is known by transport pipeline and is running on a JVMTI-compatible (O+) device.
 * TODO(b/148540564): tracks the work needed to make process detection feasible.
 *
 * Processes are discovered by listening to the transport pipeline via [TransportEventListener].
 *
 * [addProcessListener] allows the frontend to listen for new inspectable processes. Meant for populating the AppInspection combobox.
 *
 * [launchInspector] is used by the frontend to launch an inspector. It is used when user selects a process in the combobox.
 */
@ThreadSafe
class AppInspectionDiscoveryHost(
  private val executor: ExecutorService,
  client: TransportClient,
  private val manager: TransportStreamManager,
  private val createJarCopier: JarCopierCreator
): ProcessNotifier {
  /**
   * Encapsulates all of the parameters that are required for launching an inspector.
   */
  data class LaunchParameters(
    /**
     * Identifies the target process in which to launch the inspector. It is supplied by [AppInspectionDiscoveryHost].
     */
    val processDescriptor: ProcessDescriptor,
    /**
     * Id of the inspector.
     */
    val inspectorId: String,
    /**
     * The [AppInspectorJar] containing the location of the dex to be installed on device.
     */
    val inspectorJar: AppInspectorJar,
    /**
     * The name of the studio project launching the inspector.
     */
    val projectName: String
  )

  // Keep track of the last_active_event_timestamp per stream. This ensures that in the event the stream is disconnected and connected
  // again, AppInspection will be able to filter out and ignore the events that happened in the past.
  private val streamLastActiveTime = ConcurrentHashMap<Long, Long>()

  private val discovery = AppInspectionDiscovery(executor, client)

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
  override fun addProcessListener(
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
   * Removes a [ProcessListener] and so stops it from hearing future process events.
   */
  override fun removeProcessListener(listener: ProcessListener): Unit = synchronized(processData) { processData.processListeners.remove(listener) }


  /**
   * Launches an inspector based on the information given by [params].
   *
   * [params] contains information such as the inspector's id and dex location, as well as the targeted process's descriptor.
   * [creator] is a callback used to set up a client's [AppInspectorClient.rawEventListener].
   */
  fun <T : AppInspectorClient> launchInspector(params: LaunchParameters,
                                               creator: (AppInspectorClient.CommandMessenger) -> T): ListenableFuture<AppInspectorClient> {
    streamIdMap[params.processDescriptor.stream.streamId]?.let { streamChannel ->
      createJarCopier(params.processDescriptor.stream.device)?.let { jarCopier ->
        return discovery.launchInspector(params, creator, jarCopier, streamChannel)
      }
    }
    return Futures.immediateFailedFuture(
      ProcessNoLongerExistsException("Cannot attach to process because the device does not exist. Process: ${params.processDescriptor}"))
  }

  /**
   * Disposes all of the currently active inspectors associated to project with provided [projectName].
   *
   * This is used by the service to clean up after projects when they are no longer interested in a process, or when they exit.
   */
  fun disposeClients(projectName: String) {
    discovery.disposeClients(projectName)
  }

  /**
   * Register listeners to receive stream and process events from transport pipeline.
   */
  private fun registerListenersForDiscovery() {
    // Create listener for STREAM connected
    manager.addStreamListener(object : TransportStreamListener {
      override fun onStreamConnected(streamChannel: TransportStreamChannel) {
        streamIdMap[streamChannel.stream.streamId] = streamChannel
        val streamLastEventTimestamp = streamLastActiveTime[streamChannel.stream.streamId]?.let { { it + 1} } ?: { Long.MIN_VALUE }
        streamChannel.registerStreamEventListener(
          TransportStreamEventListener(
            eventKind = Common.Event.Kind.PROCESS,
            executor = executor,
            filter = { it.process.hasProcessStarted() },
            startTime = streamLastEventTimestamp
          ) {
            val process = it.process.processStarted.process
            addProcess(streamChannel, process)
            setStreamLastActiveTime(streamChannel.stream.streamId, it.timestamp)
          }
        )
        streamChannel.registerStreamEventListener(
          TransportStreamEventListener(
            eventKind = Common.Event.Kind.PROCESS,
            executor = executor,
            filter = { !it.process.hasProcessStarted() },
            startTime = streamLastEventTimestamp
          ) {
            removeProcess(streamChannel.stream.streamId, it.groupId.toInt())
            setStreamLastActiveTime(streamChannel.stream.streamId, it.timestamp)
          }
        )
      }

      override fun onStreamDisconnected(streamChannel: TransportStreamChannel) {
        streamIdMap.remove(streamChannel.stream.streamId)
      }
    }, executor)
  }

  private fun setStreamLastActiveTime(streamId: Long, timestamp: Long) {
    streamLastActiveTime[streamId] = max(timestamp, streamLastActiveTime[streamId]  ?: Long.MIN_VALUE)
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
    return stream.device.apiLevel >= AndroidVersion.VersionCodes.O
  }

  /**
   * Removes a process from internal cache. This function is called when a process goes offline.
   */
  private fun removeProcess(streamId: Long, processId: Int) {
    synchronized(processData) {
      processData.processesMap.remove(StreamProcessIdPair(streamId, processId))?.let { descriptor ->
        discovery.removeProcess(descriptor)
        processData.processListeners.forEach { (listener, executor) -> executor.execute { listener.onProcessDisconnected(descriptor) } }
      }
    }
  }
}

/**
 * Thrown when trying to launch an inspector on a process that no longer exists.
 *
 * Note: This may not necessarily signal something is broken. We expect this to happen occasionally due to bad timing. For example: user
 * selects a process for inspection on device X right when X is shutting down.
 */
class ProcessNoLongerExistsException(message: String): Exception(message)

/**
 * A class which keeps track of live [AppInspectionTarget] and [AppInspectorClient]. It exposes [launchInspector] that allows for launching
 * of inspection targets and individual inspectors.
 */
@ThreadSafe
class AppInspectionDiscovery internal constructor(
  private val executor: ExecutorService,
  private val transportClient: TransportClient
) {
  private val lock = Any()

  @VisibleForTesting
  @GuardedBy("lock")
  internal val targets = ConcurrentHashMap<ProcessDescriptor, ListenableFuture<AppInspectionTarget>>()

  @GuardedBy("lock")
  @VisibleForTesting
  internal val clients = ConcurrentHashMap<AppInspectionDiscoveryHost.LaunchParameters, ListenableFuture<AppInspectorClient>>()

  /**
   * Attempts to connect to a process on device specified by [processDescriptor]. Returns a future of [AppInspectionTarget] which can be
   * used to launch inspector connections.
   */
  @GuardedBy("lock")
  private fun attachToProcess(
    processDescriptor: ProcessDescriptor,
    jarCopier: AppInspectionJarCopier,
    streamChannel: TransportStreamChannel
  ): ListenableFuture<AppInspectionTarget> {
    return targets.computeIfAbsent(processDescriptor) {
      val transport =
        AppInspectionTransport(transportClient, processDescriptor.stream, processDescriptor.process, executor, streamChannel)
      attachAppInspectionTarget(transport, jarCopier)
    }.also {
      it.addCallback(MoreExecutors.directExecutor(), object : FutureCallback<AppInspectionTarget> {
        override fun onSuccess(result: AppInspectionTarget?) {}
        override fun onFailure(t: Throwable) {
          targets.remove(processDescriptor)
        }
      })
    }
  }

  /**
   * Launches an inspector using the information given by [launchParameters]. Returns a future of the launched [AppInspectorClient].
   *
   * This can return an existing live [AppInspectorClient] if an identical inspector was launched on the same process.
   */
  internal fun launchInspector(
    launchParameters: AppInspectionDiscoveryHost.LaunchParameters,
    creator: (AppInspectorClient.CommandMessenger) -> AppInspectorClient,
    jarCopier: AppInspectionJarCopier,
    streamChannel: TransportStreamChannel
  ): ListenableFuture<AppInspectorClient> = synchronized(lock) {
    clients.computeIfAbsent(launchParameters) {
      attachToProcess(launchParameters.processDescriptor, jarCopier, streamChannel).transformAsync(
        MoreExecutors.directExecutor()) { target ->
        target.launchInspector(launchParameters.inspectorId, launchParameters.inspectorJar, launchParameters.projectName,
                               creator).transform { client ->
          client.addServiceEventListener(object : AppInspectorClient.ServiceEventListener {
            override fun onDispose() {
              clients.remove(launchParameters)
            }
          }, MoreExecutors.directExecutor())
          client
        }
      }.also {
        it.addCallback(MoreExecutors.directExecutor(), object : FutureCallback<AppInspectorClient> {
          override fun onSuccess(result: AppInspectorClient?) {}
          override fun onFailure(t: Throwable) {
            clients.remove(launchParameters)
          }
        })
      }
    }
  }

  /**
   * Called when a process has terminated. This will clean up [AppInspectionTarget] and all clients associated with the process.
   *
   * Note: this does not try to dispose clients because it's too late.
   */
  internal fun removeProcess(process: ProcessDescriptor) {
    synchronized(lock) {
      targets.remove(process)?.let {
        clients.keys.removeAll { launchParam ->
          launchParam.processDescriptor == process
        }
      }
    }
  }

  /**
   * Dispose clients belonging to the project that matches the provided [projectName].
   */
  internal fun disposeClients(projectName: String) {
    synchronized(lock) {
      clients.filterKeys { it.projectName == projectName }
        .values.forEach { if (it.isDone) it.get().messenger.disposeInspector() else it.cancel(false) }
      clients.keys.removeAll { it.projectName == projectName }
    }
  }
}