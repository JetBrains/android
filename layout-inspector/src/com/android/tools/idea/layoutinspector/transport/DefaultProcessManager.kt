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

import com.android.tools.idea.transport.manager.TransportStreamChannel
import com.android.tools.idea.transport.manager.TransportStreamEventListener
import com.android.tools.idea.transport.manager.TransportStreamListener
import com.android.tools.idea.transport.manager.TransportStreamManager
import com.android.tools.idea.util.ListenerCollection
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Event.Kind.PROCESS
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
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
  executor: ExecutorService,
  manager: TransportStreamManager,
  parentDisposable: Disposable
) : InspectorProcessManager, Disposable {
  override val processListeners = ListenerCollection.createWithDirectExecutor<() -> Unit>()

  /**
   * Contains the currently available process as: stream -> processId -> process
   */
  private val processes = ConcurrentHashMap<Common.Stream, ConcurrentHashMap<Int, Common.Process>>()

  private val streamListener = object : TransportStreamListener {
    override fun onStreamConnected(streamChannel: TransportStreamChannel) {
      if (streamFilter(streamChannel.stream)) {
        addStream(streamChannel.stream)
        streamChannel.registerStreamEventListener(
          TransportStreamEventListener(eventKind = PROCESS, executor = executor) {
            if (it.process.hasProcessStarted()) {
              addProcess(streamChannel.stream, it.process.processStarted.process)
            }
            else {
              removeProcess(streamChannel.stream, it.groupId.toInt())
            }
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

  override fun getProcesses(stream: Common.Stream): Sequence<Common.Process> {
    val streamProcesses = processes[stream] ?: return emptySequence()
    return streamProcesses.values.asSequence()
  }

  override fun isProcessActive(stream: Common.Stream, process: Common.Process): Boolean =
    processes[stream]?.get(process.pid) == process

  private fun addProcess(stream: Common.Stream, process: Common.Process) {
    addStream(stream)[process.pid] = process
    fireProcessesChanged()
  }

  private fun removeProcess(stream: Common.Stream, processId: Int) {
    processes[stream]?.remove(processId)
    fireProcessesChanged()
  }

  private fun addStream(stream: Common.Stream): MutableMap<Int, Common.Process> {
    return processes.getOrPut(stream, { ConcurrentHashMap() })
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
}
