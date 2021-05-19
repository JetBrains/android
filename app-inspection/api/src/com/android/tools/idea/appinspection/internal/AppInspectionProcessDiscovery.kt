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
package com.android.tools.idea.appinspection.internal

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.appinspection.api.process.ProcessListener
import com.android.tools.idea.appinspection.api.process.ProcessNotifier
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.TransportProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.isInspectable
import com.android.tools.idea.transport.manager.TransportStreamChannel
import com.android.tools.idea.transport.manager.TransportStreamEventListener
import com.android.tools.idea.transport.manager.TransportStreamListener
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.profiler.proto.Common
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import java.lang.Long.max
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * A class that manages processes discovered from transport pipeline.
 *
 * Definition: An inspectable process is a process that has certain inspection flags indicated in its manifest. However, currently it is
 * simply a process that is known by transport pipeline and is running on a JVMTI-compatible (O+) device.
 * TODO(b/148540564): tracks the work needed to make process detection feasible.
 *
 * Processes are discovered by listening to the transport pipeline via [TransportEventListener].
 *
 * [addProcessListener] allows the frontend to listen for new inspectable processes. Meant for populating the AppInspection combobox.
 */
@AnyThread
internal class AppInspectionProcessDiscovery(
  private val dispatcher: CoroutineDispatcher,
  private val manager: TransportStreamManager
) : ProcessNotifier {

  // Keep track of the last_active_event_timestamp per stream. This ensures that in the event the stream is disconnected and connected
  // again, AppInspection will be able to filter out and ignore the events that happened in the past.
  private val streamLastActiveTime = ConcurrentHashMap<Long, Long>()

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
  override fun removeProcessListener(listener: ProcessListener): Unit = synchronized(processData) {
    processData.processListeners.remove(listener)
  }

  /**
   * Register listeners to receive stream and process events from transport pipeline.
   */
  private fun registerListenersForDiscovery() {
    // Create listener for STREAM connected
    manager.addStreamListener(object : TransportStreamListener {
      override fun onStreamConnected(streamChannel: TransportStreamChannel) {
        streamIdMap[streamChannel.stream.streamId] = streamChannel
        val streamLastEventTimestamp = streamLastActiveTime[streamChannel.stream.streamId]?.let { { it + 1 } } ?: { Long.MIN_VALUE }
        streamChannel.registerStreamEventListener(
          TransportStreamEventListener(
            eventKind = Common.Event.Kind.PROCESS,
            executor = dispatcher.asExecutor(),
            startTime = streamLastEventTimestamp,
            filter = { it.process.hasProcessStarted() }
          ) {
            val process = it.process.processStarted.process
            addProcess(streamChannel, process)
            setStreamLastActiveTime(streamChannel.stream.streamId, it.timestamp)
          }
        )
        streamChannel.registerStreamEventListener(
          TransportStreamEventListener(
            eventKind = Common.Event.Kind.PROCESS,
            executor = dispatcher.asExecutor(),
            startTime = streamLastEventTimestamp,
            filter = { !it.process.hasProcessStarted() }
          ) {
            removeProcess(streamChannel.stream.streamId, it.groupId.toInt())
            setStreamLastActiveTime(streamChannel.stream.streamId, it.timestamp)
          }
        )
      }

      override fun onStreamDisconnected(streamChannel: TransportStreamChannel) {
        streamIdMap.remove(streamChannel.stream.streamId)
      }
    }, dispatcher.asExecutor())
  }

  private fun setStreamLastActiveTime(streamId: Long, timestamp: Long) {
    streamLastActiveTime[streamId] = max(timestamp, streamLastActiveTime[streamId] ?: Long.MIN_VALUE)
  }

  /**
   * Adds a process to internal cache. This is called when transport pipeline is aware of a new process.
   */
  private fun addProcess(streamChannel: TransportStreamChannel, process: Common.Process) {
    synchronized(processData) {
      processData.processesMap.computeIfAbsent(
        StreamProcessIdPair(
          streamChannel.stream.streamId, process.pid)) {
        val descriptor = TransportProcessDescriptor(streamChannel.stream, process)
        if (descriptor.isInspectable()) {
          processData.processListeners.forEach { (listener, executor) -> executor.execute { listener.onProcessConnected(descriptor) } }
        }
        descriptor
      }
    }
  }

  /**
   * Removes a process from internal cache. This function is called when a process goes offline.
   */
  private fun removeProcess(streamId: Long, processId: Int) {
    synchronized(processData) {
      processData.processesMap.remove(
        StreamProcessIdPair(streamId, processId))?.let { descriptor ->
        processData.processListeners.forEach { (listener, executor) -> executor.execute { listener.onProcessDisconnected(descriptor) } }
      }
    }
  }

  /**
   * Gets the [TransportStreamChannel] for the given [streamId]. Returns null if stream does not exist (ex: may have recently closed).
   */
  internal fun getStreamChannel(streamId: Long) = streamIdMap[streamId]
}