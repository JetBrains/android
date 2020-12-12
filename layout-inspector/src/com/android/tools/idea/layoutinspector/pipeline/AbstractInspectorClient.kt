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
package com.android.tools.idea.layoutinspector.pipeline

import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.profiler.proto.Common.Event.EventGroupIds
import java.util.concurrent.Future

/**
 * Base class for [InspectorClient] implementations with some boilerplate logic provided.
 */
abstract class AbstractInspectorClient(final override val process: ProcessDescriptor): InspectorClient {
  final override var state: InspectorClient.State = InspectorClient.State.INITIALIZED
    private set

  private val eventCallbacks = mutableMapOf<EventGroupIds, MutableList<(Any) -> Unit>>()

  final override fun register(groupId: EventGroupIds, callback: (Any) -> Unit) {
    eventCallbacks.getOrPut(groupId) { mutableListOf() }.add(callback)
    if (eventCallbacks.getValue(groupId).size == 1) {
      onRegistered(groupId)
    }
  }

  /**
   * Callback triggered the first time an event handler is registered for [groupId], in case the
   * child client needs to do some additional setup work.
   */
  protected open fun onRegistered(groupId: EventGroupIds) {}

  /**
   * Fire relevant callbacks registered with [register], if present.
   */
  protected fun fireEvent(groupId: EventGroupIds, data: Any) {
    eventCallbacks[groupId]?.forEach { callback -> callback(data) }
  }

  final override fun connect() {
    assert(state == InspectorClient.State.INITIALIZED)
    state = InspectorClient.State.CONNECTED
    doConnect()
  }

  protected abstract fun doConnect()

  final override fun disconnect(): Future<*> {
    assert(state == InspectorClient.State.CONNECTED)
    state = InspectorClient.State.DISCONNECTED

    return doDisconnect().also {
      eventCallbacks.clear()
    }
  }

  protected abstract fun doDisconnect(): Future<*>
}
