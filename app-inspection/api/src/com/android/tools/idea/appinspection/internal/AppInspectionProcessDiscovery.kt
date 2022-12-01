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
import com.android.tools.idea.appinspection.api.process.ProcessDiscovery
import com.android.tools.idea.appinspection.api.process.ProcessListener
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.TransportProcessDescriptor
import com.android.tools.idea.appinspection.internal.process.toDeviceDescriptor
import com.android.tools.idea.transport.manager.StreamConnected
import com.android.tools.idea.transport.manager.StreamDisconnected
import com.android.tools.idea.transport.manager.TransportStreamChannel
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.profiler.proto.Common
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/**
 * A class that manages processes discovered from transport pipeline.
 *
 * Definition: An inspectable process is a process that has certain inspection flags indicated in its manifest. However, currently it is
 * simply a process that is known by transport pipeline and is running on a JVMTI-compatible (O+) device.
 * TODO(b/148540564): tracks the work needed to make process detection feasible.
 *
 * [addProcessListener] allows the frontend to listen for new inspectable processes. Meant for populating the AppInspection combobox.
 */
@AnyThread
internal class AppInspectionProcessDiscovery(
  private val manager: TransportStreamManager,
  private val scope: CoroutineScope
) : ProcessDiscovery {

  private val streamIdMap = ConcurrentHashMap<Long, TransportStreamChannel>()

  override val devices
    get() = streamIdMap.values
      .filter { it.stream.hasDevice() && it.stream.device.state == Common.Device.State.ONLINE }
      .map { it.stream.device.toDeviceDescriptor() }

  private data class StreamProcessIdPair(val streamId: Long, val pid: Int)

  private class ProcessData {
    // All known debuggable processes
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
    scope.launch {
      manager.streamActivityFlow()
        .collect { activity ->
          val streamChannel = activity.streamChannel
          if (activity is StreamConnected) {
            streamIdMap[streamChannel.stream.streamId] = streamChannel
            launch {
              streamChannel.processesFlow(filter = { _, process ->
                process.exposureLevel == Common.Process.ExposureLevel.DEBUGGABLE
              }).collect { process ->
                when (process.state) {
                  Common.Process.State.ALIVE -> addProcess(streamChannel, process)
                  Common.Process.State.DEAD -> removeProcess(streamChannel.stream.streamId, process.pid)
                  else -> Unit
                }
              }
            }
          }
          else if (activity is StreamDisconnected) {
            removeProcesses(streamChannel.stream.streamId)
            streamIdMap.remove(streamChannel.stream.streamId)
          }
        }
    }
  }

  /**
   * Adds a process to internal cache. This is called when transport pipeline is aware of a new process.
   */
  private fun addProcess(streamChannel: TransportStreamChannel, process: Common.Process) {
    synchronized(processData) {
      processData.processesMap.computeIfAbsent(StreamProcessIdPair(streamChannel.stream.streamId, process.pid)) {
        val descriptor = TransportProcessDescriptor(streamChannel.stream, process)
        processData.processListeners.forEach { (listener, executor) ->
          if (listener.filter(descriptor)) {
            executor.execute { listener.onProcessConnected(descriptor) }
          }
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
   * Remove all processes from the internal cache associated with the parent stream ID. This function is called when a device goes
   * offline (e.g. emulator closed or USB plug pulled)
   */
  private fun removeProcesses(streamId: Long) {
    synchronized(processData) {
      processData.processesMap.filter { it.key.streamId == streamId }.forEach {
        removeProcess(streamId, it.key.pid)
      }
    }
  }

  /**
   * Gets the [TransportStreamChannel] for the given [streamId]. Returns null if stream does not exist (ex: may have recently closed).
   */
  internal fun getStreamChannel(streamId: Long) = streamIdMap[streamId]
}