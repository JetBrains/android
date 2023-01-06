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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbUtils
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.android.tools.idea.util.ListenerCollection
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

/**
 * Base class for [InspectorClient] implementations with some boilerplate logic provided.
 */
abstract class AbstractInspectorClient(
  final override val clientType: ClientType,
  val project: Project,
  final override val process: ProcessDescriptor,
  final override val isInstantlyAutoConnected: Boolean,
  final override val stats: SessionStatistics,
  private val coroutineScope: CoroutineScope,
  parentDisposable: Disposable
) : InspectorClient {
  init {
    Disposer.register(parentDisposable, this)
  }

  final override var state: InspectorClient.State = InspectorClient.State.INITIALIZED
    @VisibleForTesting
    set(value) {
      if (field != value) {
        field = value
        fireState(value)
      }
    }

  private val stateCallbacks = ListenerCollection.createWithDirectExecutor<(InspectorClient.State) -> Unit>()
  private val errorCallbacks = ListenerCollection.createWithDirectExecutor<(String) -> Unit>()
  private val rootsEventCallbacks = ListenerCollection.createWithDirectExecutor<(List<*>) -> Unit>()
  private val treeEventCallbacks = ListenerCollection.createWithDirectExecutor<(Any) -> Unit>()
  private val attachStateListeners = ListenerCollection.createWithDirectExecutor<(DynamicLayoutInspectorErrorInfo.AttachErrorState) -> Unit>()

  var launchMonitor: InspectorClientLaunchMonitor = InspectorClientLaunchMonitor(project, attachStateListeners)
    @TestOnly set

  override fun dispose() {
    launchMonitor.stop()
  }

  override fun registerStateCallback(callback: (InspectorClient.State) -> Unit) {
    stateCallbacks.add(callback)
  }

  final override fun registerErrorCallback(callback: (String) -> Unit) {
    errorCallbacks.add(callback)
  }

  final override fun registerRootsEventCallback(callback: (List<*>) -> Unit) {
    rootsEventCallbacks.add(callback)
  }

  final override fun registerTreeEventCallback(callback: (Any) -> Unit) {
    treeEventCallbacks.add(callback)
  }

  final override fun registerConnectionTimeoutCallback(callback: (DynamicLayoutInspectorErrorInfo.AttachErrorState) -> Unit) {
    attachStateListeners.add(callback)
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

  protected fun fireRootsEvent(roots: List<*>) {
    rootsEventCallbacks.forEach { callback -> callback(roots) }
  }

  /**
   * Fire relevant callbacks registered with [registerTreeEventCallback], if present.
   */
  protected fun fireTreeEvent(event: Any) {
    treeEventCallbacks.forEach { callback -> callback(event) }
  }

  final override suspend fun connect(project: Project) {
    launchMonitor.start(this)
    assert(state == InspectorClient.State.INITIALIZED)
    state = InspectorClient.State.CONNECTING

    // Test that we can actually contact the device via ADB, and fail fast if we can't.
    val adb = AdbUtils.getAdbFuture(project).await() ?: return
    if (adb.executeShellCommand(process.device, "echo ok") != "ok") {
      state = InspectorClient.State.DISCONNECTED
      return
    }
    launchMonitor.updateProgress(DynamicLayoutInspectorErrorInfo.AttachErrorState.ADB_PING)

    try {
      doConnect()
      state = InspectorClient.State.CONNECTED
    }
    catch (t: Throwable) {
      // TODO(b/254222091) consider moving error handling in exception handler in the coroutine scope
      launchMonitor.onFailure(t)
      disconnect()
      Logger.getInstance(AbstractInspectorClient::class.java).warn(
        "Connection failure with " +
        "'use.dev.jar=${StudioFlags.APP_INSPECTION_USE_DEV_JAR.get()}' " +
        "'use.snapshot.jar=${StudioFlags.APP_INSPECTION_USE_SNAPSHOT_JAR.get()}' " +
        "cause:", t)
    }
  }

  override fun updateProgress(state: DynamicLayoutInspectorErrorInfo.AttachErrorState) {
    launchMonitor.updateProgress(state)
  }

  protected abstract suspend fun doConnect()

  private val disconnectStateLock = Any()
  final override fun disconnect() {
    coroutineScope.launch {
      synchronized(disconnectStateLock) {
        if (state == InspectorClient.State.DISCONNECTED || state == InspectorClient.State.DISCONNECTING) {
          return@launch
        }
        state = InspectorClient.State.DISCONNECTING
      }

      doDisconnect()

      state = InspectorClient.State.DISCONNECTED
      treeEventCallbacks.clear()
      stateCallbacks.clear()
      errorCallbacks.clear()
    }
  }

  protected abstract suspend fun doDisconnect()
}
