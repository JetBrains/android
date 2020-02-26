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
package com.android.tools.idea.layoutinspector.transport

import com.android.tools.idea.layoutinspector.LayoutInspectorPreferredProcess
import com.android.tools.idea.layoutinspector.model.TreeLoader
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.properties.EmptyPropertiesProvider
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Common.Event.EventGroupIds
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import java.util.concurrent.Future

/**
 * Client for communicating with the agent.
 */
interface InspectorClient {
  /**
   * Register a handler for a specific groupId.
   */
  fun register(groupId: EventGroupIds, callback: (Any) -> Unit)

  /**
   * Register a handler for when the current process ends.
   */
  fun registerProcessChanged(callback: () -> Unit)

  /**
   * Find all processes that the inspector can attach to.
   */
  fun loadProcesses(): Map<Common.Stream, List<Common.Process>>

  /**
   * Attach to a preferred process.
   */
  fun attachIfSupported(preferredProcess: LayoutInspectorPreferredProcess): Future<*>?

  /**
   * Attach to a specific process.
   */
  fun attach(stream: Common.Stream, process: Common.Process)

  /**
   * Disconnect from the current process.
   */
  fun disconnect()

  /**
   * Send a command to the agent.
   */
  fun execute(command: LayoutInspectorCommand)

  /**stud
   * Send simple command to agent without arguments.
   */
  fun execute(commandType: LayoutInspectorCommand.Type) {
    execute(LayoutInspectorCommand.newBuilder().setType(commandType).build())
  }

  val treeLoader: TreeLoader
  /**
   * True, if a connection to a device is currently open.
   */
  val isConnected: Boolean

  /**
   * If [isConnected] contains the current selected device stream.
   */
  val selectedStream: Common.Stream

  /**
   * If [isConnected] contains the current selected process.
   */
  val selectedProcess: Common.Process

  /**
   * True, if the current connection is currently receiving live updates.
   */
  val isCapturing: Boolean

  /**
   * Return a provider of properties from the current agent.
   */
  val provider: PropertiesProvider

  companion object {
    /**
     * Prove a way for tests to generate a mock client.
     */
    @VisibleForTesting
    var clientFactory: (model: InspectorModel, parentDisposable: Disposable) -> InspectorClient = { model, parentDisposable ->
      DefaultInspectorClient(model, parentDisposable)
    }

    /**
     * Use this method to create a new client.
     */
    fun createInstance(model: InspectorModel, parentDisposable: Disposable): InspectorClient = clientFactory(model, parentDisposable)
  }
}

object DisconnectedClient : InspectorClient {
  override val treeLoader: TreeLoader = object: TreeLoader {
    override fun loadComponentTree(data: Any?, resourceLookup: ResourceLookup, client: InspectorClient): Pair<ViewNode, Any>? = null
    override fun getAllWindowIds(data: Any?, client: InspectorClient) = listOf<Any>()
  }
  override fun register(groupId: EventGroupIds, callback: (Any) -> Unit) {}
  override fun registerProcessChanged(callback: () -> Unit) {}
  override fun loadProcesses(): Map<Common.Stream, List<Common.Process>> = mapOf()
  override fun attachIfSupported(preferredProcess: LayoutInspectorPreferredProcess): Future<*>? = null
  override fun attach(stream: Common.Stream, process: Common.Process) {}
  override fun disconnect() {}
  override fun execute(command: LayoutInspectorCommand) {}
  override val isConnected = false
  override val selectedStream: Common.Stream = Common.Stream.getDefaultInstance()
  override val selectedProcess: Common.Process = Common.Process.getDefaultInstance()
  override val isCapturing = false
  override val provider = EmptyPropertiesProvider
}