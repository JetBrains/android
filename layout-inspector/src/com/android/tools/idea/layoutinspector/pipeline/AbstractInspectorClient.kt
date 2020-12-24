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
import java.util.concurrent.Future

/**
 * Base class for [InspectorClient] implementations with some boilerplate logic provided.
 */
abstract class AbstractInspectorClient(final override val process: ProcessDescriptor): InspectorClient {
  final override var state: InspectorClient.State = InspectorClient.State.INITIALIZED
    private set

  private val errorCallbacks = mutableListOf<(String) -> Unit>()
  private val treeEventCallbacks = mutableListOf<(Any) -> Unit>()

  final override fun registerErrorCallback(callback: (String) -> Unit) {
    errorCallbacks.add(callback)
  }

  final override fun registerTreeEventCallback(callback: (Any) -> Unit) {
    treeEventCallbacks.add(callback)
  }

  /**
   * Fire relevant callbacks registered with [registerErrorCallback], if present
   */
  protected fun fireError(error: String) {
    errorCallbacks.forEach { callback -> callback(error) }
  }

  /**
   * Fire relevant callbacks registered with [registerTreeEventCallback], if present.
   */
  protected fun fireTreeEvent(event: Any) {
    treeEventCallbacks.forEach { callback -> callback(event) }
  }

  final override fun connect() {
    assert(state == InspectorClient.State.INITIALIZED)
    doConnect()
    // Update state afterwards, in case connection throws an exception
    state = InspectorClient.State.CONNECTED
  }

  protected abstract fun doConnect()

  final override fun disconnect(): Future<*> {
    assert(state == InspectorClient.State.CONNECTED)
    state = InspectorClient.State.DISCONNECTED

    return doDisconnect().also {
      errorCallbacks.clear()
      treeEventCallbacks.clear()
    }
  }

  protected abstract fun doDisconnect(): Future<*>
}
