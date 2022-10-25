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

import com.android.tools.idea.appinspection.inspector.api.AppInspectionAppProguardedException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionArtifactNotFoundException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionCannotFindAdbDeviceException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionLibraryMissingException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionProcessNoLongerExistsException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionServiceException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionVersionIncompatibleException
import com.android.tools.idea.appinspection.inspector.api.AppInspectionVersionMissingException
import com.android.tools.idea.appinspection.inspector.api.launch.LibraryCompatbilityInfo
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbUtils
import com.android.tools.idea.layoutinspector.pipeline.adb.executeShellCommand
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeLayoutInspectorClient
import com.android.tools.idea.util.ListenerCollection
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo.AttachErrorCode
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly

/**
 * Base class for [InspectorClient] implementations with some boilerplate logic provided.
 */
abstract class AbstractInspectorClient(
  final override val clientType: ClientType,
  val project: Project,
  final override val process: ProcessDescriptor,
  final override val isInstantlyAutoConnected: Boolean,
  final override val stats: SessionStatistics,
  parentDisposable: Disposable
) : InspectorClient {
  init {
    Disposer.register(parentDisposable, this)
  }

  final override var state: InspectorClient.State = InspectorClient.State.INITIALIZED
    private set(value) {
      if (field != value) {
        field = value
        fireState(value)
      }
    }

  private val stateCallbacks = ListenerCollection.createWithDirectExecutor<(InspectorClient.State) -> Unit>()
  private val errorCallbacks = ListenerCollection.createWithDirectExecutor<(String) -> Unit>()
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

  /**
   * Fire relevant callbacks registered with [registerTreeEventCallback], if present.
   */
  protected fun fireTreeEvent(event: Any) {
    treeEventCallbacks.forEach { callback -> callback(event) }
  }

  final override fun connect(project: Project) {
    launchMonitor.start(this)
    assert(state == InspectorClient.State.INITIALIZED)
    state = InspectorClient.State.CONNECTING

    // Test that we can actually contact the device via ADB, and fail fast if we can't.
    val adb = AdbUtils.getAdbFuture(project).get() ?: return
    if (adb.executeShellCommand(process.device, "echo ok") != "ok") {
      state = InspectorClient.State.DISCONNECTED
      return
    }
    launchMonitor.updateProgress(DynamicLayoutInspectorErrorInfo.AttachErrorState.ADB_PING)


    doConnect().addCallback(MoreExecutors.directExecutor(), object : FutureCallback<Nothing> {
      override fun onSuccess(value: Nothing?) {
        state = InspectorClient.State.CONNECTED
      }

      override fun onFailure(t: Throwable) {
        launchMonitor.onFailure(t)
        disconnect()
        Logger.getInstance(AbstractInspectorClient::class.java).warn(
          "Connection failure with " +
          "'use.dev.jar=${StudioFlags.APP_INSPECTION_USE_DEV_JAR.get()}' " +
          "'use.snapshot.jar=${StudioFlags.APP_INSPECTION_USE_SNAPSHOT_JAR.get()}' " +
          "cause:", t)
      }
    })
  }

  override fun updateProgress(state: DynamicLayoutInspectorErrorInfo.AttachErrorState) {
    launchMonitor.updateProgress(state)
  }

  protected abstract fun doConnect(): ListenableFuture<Nothing>

  private val disconnectStateLock = Any()
  final override fun disconnect() {
    synchronized(disconnectStateLock) {
      if (state == InspectorClient.State.DISCONNECTED || state == InspectorClient.State.DISCONNECTING) {
        return
      }
      state = InspectorClient.State.DISCONNECTING
    }
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

class ConnectionFailedException(message: String, val code: AttachErrorCode = AttachErrorCode.UNKNOWN_ERROR_CODE): Exception(message)

val Throwable.errorCode: AttachErrorCode
  get() = when (this) {
    is ConnectionFailedException -> code
    is AppInspectionCannotFindAdbDeviceException -> AttachErrorCode.APP_INSPECTION_CANNOT_FIND_DEVICE
    is AppInspectionProcessNoLongerExistsException -> AttachErrorCode.APP_INSPECTION_PROCESS_NO_LONGER_EXISTS
    is AppInspectionVersionIncompatibleException -> AttachErrorCode.APP_INSPECTION_INCOMPATIBLE_VERSION
    is AppInspectionVersionMissingException -> AttachErrorCode.APP_INSPECTION_VERSION_FILE_NOT_FOUND
    is AppInspectionLibraryMissingException -> AttachErrorCode.APP_INSPECTION_MISSING_LIBRARY
    is AppInspectionAppProguardedException -> AttachErrorCode.APP_INSPECTION_PROGUARDED_APP
    is AppInspectionArtifactNotFoundException -> AttachErrorCode.APP_INSPECTION_ARTIFACT_NOT_FOUND
    is AppInspectionServiceException -> {
      logUnexpectedError(InspectorConnectionError(this))
      AttachErrorCode.UNKNOWN_APP_INSPECTION_ERROR
    }
    else -> {
      logUnexpectedError(InspectorConnectionError(this))
      AttachErrorCode.UNKNOWN_ERROR_CODE
    }
  }

val LibraryCompatbilityInfo.Status?.errorCode
  get() = when (this) {
    LibraryCompatbilityInfo.Status.INCOMPATIBLE -> AttachErrorCode.APP_INSPECTION_INCOMPATIBLE_VERSION
    LibraryCompatbilityInfo.Status.APP_PROGUARDED -> AttachErrorCode.APP_INSPECTION_PROGUARDED_APP
    LibraryCompatbilityInfo.Status.VERSION_MISSING -> AttachErrorCode.APP_INSPECTION_VERSION_FILE_NOT_FOUND
    LibraryCompatbilityInfo.Status.LIBRARY_MISSING -> AttachErrorCode.APP_INSPECTION_MISSING_LIBRARY
    else -> {
      logUnexpectedError(InspectorConnectionError("Unexpected status $this"))
      AttachErrorCode.UNKNOWN_APP_INSPECTION_ERROR
    }
  }

/**
 * Log this unexpected exception such that it can be found in go/studio-exceptions but do not throw a new exception.
 */
private fun logUnexpectedError(error: InspectorConnectionError) {
  try {
    Logger.getInstance(ComposeLayoutInspectorClient::class.java).error(error)
  }
  catch (_: Throwable) {
  }
}
