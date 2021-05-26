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
import com.android.tools.idea.concurrency.addCallback
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

/**
 * Base class for [InspectorClient] implementations with some boilerplate logic provided.
 */
abstract class AbstractInspectorClient(final override val process: ProcessDescriptor) : InspectorClient {
  final override var state: InspectorClient.State = InspectorClient.State.INITIALIZED
    private set(value) {
      assert(field != value)
      field = value
      fireState(value)
    }

  private val stateCallbacks = mutableListOf<(InspectorClient.State) -> Unit>()
  private val errorCallbacks = mutableListOf<(String) -> Unit>()
  private val treeEventCallbacks = mutableListOf<(Any) -> Unit>()

  override fun registerStateCallback(callback: (InspectorClient.State) -> Unit) {
    stateCallbacks.add(callback)
  }

  final override fun registerErrorCallback(callback: (String) -> Unit) {
    errorCallbacks.add(callback)
  }

  final override fun registerTreeEventCallback(callback: (Any) -> Unit) {
    treeEventCallbacks.add(callback)
  }

  /**
   * Fire relevant callbacks registered with [registerStateCallback], if present.
   */
  private fun fireState(state: InspectorClient.State) {
    stateCallbacks.forEach { callback -> callback(state) }
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
    state = InspectorClient.State.CONNECTING
    doConnect().addCallback(MoreExecutors.directExecutor(), object : FutureCallback<Nothing> {
      override fun onSuccess(value: Nothing?) {
        state = InspectorClient.State.CONNECTED
      }

      override fun onFailure(t: Throwable) {
        state = InspectorClient.State.DISCONNECTED
      }
    })
  }

  protected abstract fun doConnect(): ListenableFuture<Nothing>

  final override fun disconnect() {
    assert(state == InspectorClient.State.CONNECTED)
    state = InspectorClient.State.DISCONNECTING

    doDisconnect().addListener(
      {
        state = InspectorClient.State.DISCONNECTED
        treeEventCallbacks.clear()
        stateCallbacks.clear()
        errorCallbacks.clear()
      }, MoreExecutors.directExecutor())
  }

  protected abstract fun doDisconnect(): ListenableFuture<Nothing>
}
