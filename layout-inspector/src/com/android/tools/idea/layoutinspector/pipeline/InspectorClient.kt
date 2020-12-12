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

import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.properties.EmptyPropertiesProvider
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorCommand
import com.android.tools.profiler.proto.Common.Event.EventGroupIds
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * Client for communicating with the agent.
 *
 * When created, it is expected that [connect] should be called shortly after, and that the client
 * will be in a valid state until [disconnect] is called.
 */
interface InspectorClient {
  enum class State {
    INITIALIZED,
    CONNECTED,
    DISCONNECTED,
  }

  /**
   * Register a handler that is triggered when this client receives an event associated with the specified [groupId].
   */
  fun register(groupId: EventGroupIds, callback: (Any) -> Unit)

  /**
   * Connect this client to the device.
   *
   * You are only supposed to call this once.
   */
  fun connect()

  /**
   * Disconnect this client.
   *
   * You are only supposed to call this once.
   */
  fun disconnect(): Future<*>

  /**
   * Send a command to the agent.
   */
  fun execute(command: LayoutInspectorCommand)

  /**
   * Refresh the content of the inspector.
   */
  fun refresh()

  /**
   * Log events for Studio stats
   */
  fun logEvent(type: DynamicLayoutInspectorEventType)

  val state: State

  /**
   * The process this client is associated with.
   *
   * If a new process is connected, a new client should be created to handle it.
   */
  val process: ProcessDescriptor

  val treeLoader: TreeLoader

  /**
   * True, if the current connection is currently receiving live updates.
   */
  val isCapturing: Boolean

  /**
   * Return a provider of properties from the current agent.
   */
  val provider: PropertiesProvider

  /**
   * Return true if the current client is actively connected (or about to connect) to the current
   * process.
   */
  val isConnected: Boolean get() = state != State.DISCONNECTED
}

object DisconnectedClient : InspectorClient {
  override fun register(groupId: EventGroupIds, callback: (Any) -> Unit) {}
  override fun connect() {}
  override fun disconnect(): Future<Nothing> = CompletableFuture.completedFuture(null)
  override fun execute(command: LayoutInspectorCommand) {}
  override fun refresh() {}
  override fun logEvent(type: DynamicLayoutInspectorEventType) {}

  override val state = InspectorClient.State.DISCONNECTED
  override val process = object : ProcessDescriptor {
    override val device: DeviceDescriptor = object : DeviceDescriptor {
      override val manufacturer: String = ""
      override val model: String = ""
      override val serial: String = ""
      override val isEmulator: Boolean = false
      override val apiLevel: Int = 0
      override val version: String = ""
      override val codename: String? = null
    }
    override val abiCpuArch: String = ""
    override val name: String = ""
    override val isRunning: Boolean = false
    override val pid: Int = 0
    override val streamId: Long = 0
  }
  override val treeLoader = object : TreeLoader {
    override fun loadComponentTree(data: Any?, resourceLookup: ResourceLookup): Pair<AndroidWindow?, Int>? = null
    override fun getAllWindowIds(data: Any?): List<*> = emptyList<Any>()
  }
  override val isCapturing = false
  override val provider = EmptyPropertiesProvider
}