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
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.RecompositionData
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.EmptyPropertiesProvider
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.ui.HIGHLIGHT_COLOR_RED
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorState
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorSession
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.util.EnumSet
import java.util.concurrent.CompletableFuture

/**
 * Client for communicating with the agent.
 *
 * When created, it is expected that [connect] should be called shortly after, and that the client
 * will be in a valid state until [disconnect] is called.
 */
interface InspectorClient: Disposable {
  enum class State {
    INITIALIZED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED,
  }

  enum class Capability {
    /**
     * Indicates this client supports continuous fetching via [startFetching] and [stopFetching].
     */
    SUPPORTS_CONTINUOUS_MODE,

    /**
     * Indicates that this client is able to separate user defined nodes from system defined nodes.
     */
    SUPPORTS_SYSTEM_NODES,

    /**
     * Indicates that this client is able to send [AndroidWindow.ImageType.SKP] screenshots.
     */
    SUPPORTS_SKP,

    /**
     * Indicates that this client is able to collect semantic information.
     */
    SUPPORTS_SEMANTICS,

    /**
     * Indicates that this client is able to inspect compose parts of the application.
     */
    SUPPORTS_COMPOSE,

    /**
     * Indicates that this client is able to inspect compose recomposition counts of the application.
     */
    SUPPORTS_COMPOSE_RECOMPOSITION_COUNTS,
  }

  /**
   * Register a handler that is triggered whenever this client's [state] has changed.
   */
  fun registerStateCallback(callback: (State) -> Unit)

  /**
   * Register a handler that is triggered when this client encounters an error message
   */
  fun registerErrorCallback(callback: (String) -> Unit)

  /**
   * Register a handler that is triggered when this client receives an event containing layout tree
   * data about this device.
   *
   * See also: [treeLoader], which helps consume this data
   */
  fun registerTreeEventCallback(callback: (Any) -> Unit)

  /**
   * Register a handle that is triggered when this client receives a launch event.
   */
  fun registerConnectionTimeoutCallback(callback: (AttachErrorState) -> Unit)

  /**
   * Connect this client to the device.
   *
   * Use [registerStateCallback] and check for [State.CONNECTED] if you need to know when this has
   * finished. This may also get set to [State.DISCONNECTED] if the client fails to connect.
   *
   * You are only supposed to call this once.
   */
  fun connect(project: Project)

  fun updateProgress(state: AttachErrorState)

  /**
   * Disconnect this client.
   *
   * Use [registerStateCallback] and check for [State.DISCONNECTED] if you need to know when this has
   * finished.
   *
   * You are only supposed to call this once.
   */
  fun disconnect()

  /**
   * Start fetching information continuously off the device.
   *
   * If this is currently happening, then [isCapturing] will be set to true.
   *
   * See also [refresh], which is used for pulling the state of the device one piece at a time
   * instead.
   *
   * Once called, you should call [stopFetching] to cancel.
   *
   * If this client does not have the [Capability.SUPPORTS_CONTINUOUS_MODE] capability, then this
   * method should not be called, and doing so is undefined.
   */
  fun startFetching(): CompletableFuture<Unit>

  /**
   * Stop fetching information off the device.
   *
   * See also: [startFetching]
   *
   * If this client does not have the [Capability.SUPPORTS_CONTINUOUS_MODE] capability, then this
   * method should not be called, and doing so is undefined.
   */
  fun stopFetching(): CompletableFuture<Unit>

  /**
   * Refresh the content of the inspector.
   *
   * This shouldn't be necessary if the client is already continuously fetching off the device, i.e.
   * [isCapturing] is true.
   */
  fun refresh()

  /**
   * Set the requested screenshot type and zoom to be provided by the device.
   */
  fun updateScreenshotType(type: AndroidWindow.ImageType?, scale: Float = 1.0f) {}

  /**
   * Some compose capabilities are discovered after receiving data from the agent.
   */
  fun addDynamicCapabilities(dynamicCapabilities: Set<Capability>) {}

  /**
   * Save a snapshot of the current view, including all data needed to reconstitute it (e.g. properties information) to the given [path].
   */
  fun saveSnapshot(path: Path)

  /**
   * The type of client (app inspection or legacy client)
   */
  val clientType: ClientType

  /**
   * Report this client's capabilities so that external systems can check what functionality is
   * available before interacting with some of this client's methods.
   */
  val capabilities: Set<Capability>
    get() = EnumSet.noneOf(Capability::class.java)

  val state: State

  /**
   * Saved statistics about the current session.
   */
  val stats: SessionStatistics

  /**
   * The process this client is associated with.
   *
   * If a new process is connected, a new client should be created to handle it.
   */
  val process: ProcessDescriptor

  /**
   * Weather the process was auto connected or was manually selected.
   */
  val isInstantlyAutoConnected: Boolean

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
  val isConnected: Boolean get() = (state == State.CONNECTED)

  override fun dispose() {}
}

object DisconnectedClient : InspectorClient {
  override fun connect(project: Project) {}
  override fun updateProgress(state: AttachErrorState) {}

  override fun disconnect() {}

  override fun registerStateCallback(callback: (InspectorClient.State) -> Unit) = Unit
  override fun registerErrorCallback(callback: (String) -> Unit) = Unit
  override fun registerTreeEventCallback(callback: (Any) -> Unit) = Unit
  override fun registerConnectionTimeoutCallback(callback: (AttachErrorState) -> Unit) = Unit

  override fun startFetching(): CompletableFuture<Unit> = CompletableFuture.completedFuture(Unit)
  override fun stopFetching(): CompletableFuture<Unit> = CompletableFuture.completedFuture(Unit)
  override fun refresh() {}
  override fun saveSnapshot(path: Path) {}

  override val clientType: ClientType = ClientType.UNKNOWN_CLIENT_TYPE

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
    override val packageName: String = ""
    override val isRunning: Boolean = false
    override val pid: Int = 0
    override val streamId: Long = 0
  }
  override val stats: SessionStatistics = DisconnectedSessionStatistics
  override val isInstantlyAutoConnected = false
  override val treeLoader = object : TreeLoader {
    override fun loadComponentTree(data: Any?, resourceLookup: ResourceLookup, process: ProcessDescriptor): ComponentTreeData? = null
    override fun getAllWindowIds(data: Any?): List<*> = emptyList<Any>()
  }
  override val isCapturing = false
  override val provider = EmptyPropertiesProvider
}

private object DisconnectedSessionStatistics : SessionStatistics {
  override fun start() {}
  override fun save(data: DynamicLayoutInspectorSession.Builder) {}
  override fun selectionMadeFromImage(view: ViewNode?) {}
  override fun selectionMadeFromComponentTree(view: ViewNode?) {}
  override fun refreshButtonClicked() {}
  override fun gotoSourceFromPropertyValue(view: ViewNode?) {}
  override fun gotoSourceFromTreeActionMenu(event: AnActionEvent) {}
  override fun gotoSourceFromDoubleClick() {}
  override fun updateRecompositionStats(recompositions: RecompositionData, maxHighlight: Float) {}
  override fun resetRecompositionCountsClick() {}
  override fun attachSuccess() {}
  override fun attachError(errorState: AttachErrorState?, errorCode: AttachErrorCode) {}
  override fun composeAttachError(errorCode: AttachErrorCode) {}
  override fun frameReceived() {}

  override var currentModeIsLive: Boolean = false
  override var currentMode3D: Boolean = false
  override var hideSystemNodes: Boolean = true
  override var showRecompositions: Boolean = false
  override var recompositionHighlightColor: Int = HIGHLIGHT_COLOR_RED
}
