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
package com.android.tools.idea.layoutinspector.transport

import com.android.tools.idea.transport.manager.StreamEventQuery
import com.android.tools.idea.transport.manager.TransportStreamChannel
import com.android.tools.idea.transport.manager.TransportStreamEventListener
import com.android.tools.idea.transport.manager.TransportStreamListener
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.idea.util.ListenerCollection
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Event.Kind.PROCESS
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.TransportServiceGrpc.TransportServiceBlockingStub
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * A process manager that keeps track of the available processes for the Layout Inspector.
 *
 * This class uses a StreamListener layer to listen for changes in the list of active devices,
 * and their associated processes, reported by the transport layer.
 *
 * [processListeners] gives a notification whenever the data in [processes] is changed
 */
// TODO(b/150618894): Investigate if this functionality should be shared with the App Inspector.
class DefaultProcessManager(
  private val transportStub: TransportServiceBlockingStub,
  executor: ExecutorService,
  manager: TransportStreamManager,
  parentDisposable: Disposable
) : InspectorProcessManager, Disposable {
  override val processListeners = ListenerCollection.createWithDirectExecutor<() -> Unit>()

  /**
   * Contains the currently available process as: stream -> processId -> process
   */
  private val processes = ConcurrentHashMap<Common.Stream, Map<Int, Common.Process>>()

  private val scheduler: ScheduledExecutorService = JobScheduler.getScheduler()

  /**
   * Is true when a [handleNextStream] is scheduled to run or is running.
   */
  private val validationScheduled = AtomicBoolean(false)

  /**
   * Contains the streams for which a process may have changed state.
   */
  private val invalidations = object : ConcurrentLinkedDeque<Common.Stream>() {
    // Override add to make this Deque function as Set.
    // i.e. multiple invalidation of the same stream should only cause a single update.
    override fun add(element: Common.Stream): Boolean {
      return !contains(element) && super.add(element)
    }
  }

  private val streamListener = object : TransportStreamListener {
    override fun onStreamConnected(streamChannel: TransportStreamChannel) {
      if (streamFilter(streamChannel.stream)) {
        addStream(streamChannel.stream)
        val streamQuery = StreamEventQuery(eventKind = PROCESS)
        streamChannel.registerStreamEventListener(
          TransportStreamEventListener(streamEventQuery = streamQuery, executor = executor) {
            invalidateCache(streamChannel.stream)
          }
        )
      }
    }

    override fun onStreamDisconnected(streamChannel: TransportStreamChannel) {
      if (streamFilter(streamChannel.stream)) {
        removeStream(streamChannel.stream)
      }
    }
  }

  init {
    Disposer.register(parentDisposable, this)
    manager.addStreamListener(streamListener, executor)
  }

  override fun dispose() {
    //  It is not necessary to remove the stream listener since the TransportStreamManager will go away with the DefaultInspectorClient
    //  manager.removeStreamListener(streamListener)
  }

  override fun getStreams(): Sequence<Common.Stream> = processes.keys.asSequence()

  override fun getProcesses(stream: Common.Stream): Sequence<Common.Process> =
    processes[stream]?.values?.asSequence() ?: emptySequence()

  override fun isProcessActive(stream: Common.Stream, process: Common.Process): Boolean =
    processes[stream]?.get(process.pid) == process

  private fun addStream(stream: Common.Stream) {
    processes.getOrPut(stream, { emptyMap() })
    fireProcessesChanged()
  }

  private fun removeStream(stream: Common.Stream) {
    processes.remove(stream)
    fireProcessesChanged()
  }

  private fun fireProcessesChanged() {
    processListeners.forEach(Consumer { it() })
  }

  private fun streamFilter(stream: Common.Stream): Boolean =
    stream.type == Common.Stream.Type.DEVICE && stream.device.featureLevel >= 29

  private fun invalidateCache(stream: Common.Stream) {
    invalidations.add(stream)
    scheduleNext()
  }

  private fun scheduleNext() {
    if (validationScheduled.compareAndSet(false, true)) {
      scheduler.schedule({ handleNextStream() }, 250, TimeUnit.MILLISECONDS)
    }
  }

  private fun handleNextStream() {
    val stream = invalidations.poll()
    try {
      stream?.let { loadProcesses(it) }
    }
    finally {
      validationScheduled.set(false)
      if (invalidations.isNotEmpty()) {
        scheduleNext()
      }
    }
  }

  private fun loadProcesses(stream: Common.Stream) {
    val processRequest = Transport.GetEventGroupsRequest.newBuilder().apply {
      streamId = stream.streamId
      kind = PROCESS
    }.build()
    val processResponse = transportStub.getEventGroups(processRequest)
    val processMap = mutableMapOf<Int, Common.Process>()
    // A group is a collection of events that happened to a single process.
    for (groupProcess in processResponse.groupsList) {
      val isProcessDead = groupProcess.getEvents(groupProcess.eventsCount - 1).isEnded
      if (isProcessDead) {
        // Ignore dead processes.
        continue
      }
      val aliveEvent = groupProcess.eventsList.lastOrNull { e -> e.hasProcess() && e.process.hasProcessStarted() }
                       ?: // Ignore process event groups that do not have the started event.
                       continue
      val process = aliveEvent.process.processStarted.process
      processMap[process.pid] = process
    }
    processes[stream] = processMap
    fireProcessesChanged()
  }
}
