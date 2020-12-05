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

import com.android.ddmlib.AndroidDebugBridge
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyClient
import com.android.tools.idea.layoutinspector.pipeline.transport.TransportInspectorClient
import com.android.tools.idea.layoutinspector.properties.EmptyPropertiesProvider
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand
import com.android.tools.profiler.proto.Common.Event.EventGroupIds
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.openapi.Disposable
import java.util.concurrent.CompletableFuture
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
   * Register a handler for when the current process starts and ends.
   */
  fun registerProcessChanged(callback: (InspectorClient) -> Unit)

  /**
   * Disconnect from the current process.
   */
  fun disconnect(): Future<*>

  /**
   * Send a command to the agent.
   */
  fun execute(command: LayoutInspectorCommand)

  /**
   * Send simple command to agent without arguments.
   */
  fun execute(commandType: LayoutInspectorCommand.Type) {
    execute(LayoutInspectorCommand.newBuilder().setType(commandType).build())
  }

  /**
   * Refresh the content of the inspector.
   */
  fun refresh()

  /**
   * Log events for Studio stats
   */
  fun logEvent(type: DynamicLayoutInspectorEventType)

  val treeLoader: TreeLoader

  /**
   * The process to which this client is currently connected.
   */
  val selectedProcess: ProcessDescriptor?

  /**
   * True, if the current connection is currently receiving live updates.
   */
  val isCapturing: Boolean

  /**
   * Return a provider of properties from the current agent.
   */
  val provider: PropertiesProvider

  /**
   * Return true if the current client is actively connected to the current process.
   */
  val isConnected: Boolean get() = selectedProcess != null

  companion object {
    /**
     * Provide a way for tests to generate a mock client.
     */
    @VisibleForTesting
    var clientFactory: (adb: AndroidDebugBridge, processes: ProcessesModel, model: InspectorModel, parentDisposable: Disposable) -> List<InspectorClient> = { adb, processes, model, parentDisposable ->
      listOf(TransportInspectorClient(adb, processes, model, parentDisposable), LegacyClient(adb, processes, model))
    }

    /**
     * Use this method to create a new client.
     */
    fun createInstances(adb: AndroidDebugBridge,
                        processes: ProcessesModel,
                        model: InspectorModel,
                        parentDisposable: Disposable): List<InspectorClient> = clientFactory(adb, processes, model, parentDisposable)
  }
}

object DisconnectedClient : InspectorClient {
  override fun register(groupId: EventGroupIds, callback: (Any) -> Unit) {}
  override fun registerProcessChanged(callback: (InspectorClient) -> Unit) {}
  override fun disconnect(): Future<Nothing> = CompletableFuture.completedFuture(null)
  override fun execute(command: LayoutInspectorCommand) {}
  override fun refresh() {}
  override fun logEvent(type: DynamicLayoutInspectorEventType) {}

  override val treeLoader = object : TreeLoader {
    override fun loadComponentTree(data: Any?, resourceLookup: ResourceLookup): Pair<AndroidWindow?, Int>? = null
    override fun getAllWindowIds(data: Any?): List<*> = emptyList<Any>()
  }
  override val selectedProcess: ProcessDescriptor? = null
  override val isCapturing = false
  override val provider = EmptyPropertiesProvider
}