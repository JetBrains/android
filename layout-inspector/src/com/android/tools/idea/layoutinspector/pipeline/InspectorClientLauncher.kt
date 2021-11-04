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

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyClient
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Class responsible for listening to active process connections and launching the correct
 * [InspectorClient] to handle it.
 *
 * @param clientCreators Client factory callbacks that will be triggered in order, and the first
 * callback to return a non-null value will be used.
 *
 * @param executor The executor which will handle connecting / launching the current client. This
 * should not be the UI thread, in order to avoid blocking the UI during this time.
 */
class InspectorClientLauncher(
  private val processes: ProcessesModel,
  private val clientCreators: List<(Params) -> InspectorClient?>,
  private val project: Project,
  private val parentDisposable: Disposable,
  @VisibleForTesting val executor: Executor = AndroidExecutors.getInstance().workerThreadExecutor
) {
  companion object {

    /**
     * Convenience method for creating a launcher with useful client creation rules used in production
     */
    fun createDefaultLauncher(
      processes: ProcessesModel,
      model: InspectorModel,
      stats: SessionStatistics,
      parentDisposable: Disposable
    ): InspectorClientLauncher {
      return InspectorClientLauncher(
        processes,
        listOf(
          { params ->
            if (params.process.device.apiLevel >= AndroidVersion.VersionCodes.Q) {
              AppInspectionInspectorClient(params.process, params.isInstantlyAutoConnected, model, stats, parentDisposable)
            }
            else {
              null
            }
          },
          { params -> LegacyClient(params.process, params.isInstantlyAutoConnected, model, stats, parentDisposable) }
        ),
        model.project,
        parentDisposable)
    }
  }

  interface Params {
    val process: ProcessDescriptor
    val isInstantlyAutoConnected: Boolean
    val disposable: Disposable
  }

  init {
    processes.addSelectedProcessListeners(executor) {
      if (!project.isDisposed) {
        handleProcess(processes.selectedProcess, processes.isAutoConnected)
      }
    }

    Disposer.register(parentDisposable) {
      activeClient = DisconnectedClient
    }
  }

  private fun handleProcess(process: ProcessDescriptor?, isInstantlyAutoConnected: Boolean) {
    var validClientConnected = false
    if (process != null && process.isRunning && enabled) {
      val params = object : Params {
        override val process: ProcessDescriptor = process
        override val isInstantlyAutoConnected: Boolean = isInstantlyAutoConnected
        override val disposable: Disposable = parentDisposable
      }

      for (createClient in clientCreators) {
        val client = createClient(params)
        if (client != null) {
          try {
            val latch = CountDownLatch(1)
            client.registerStateCallback { state ->
              if (state == InspectorClient.State.CONNECTED || state == InspectorClient.State.DISCONNECTED) {
                validClientConnected = (state == InspectorClient.State.CONNECTED)
                latch.countDown()
              }
            }

            activeClient = client // client.connect() call internally might throw
            // InspectorClientLaunchMonitor should kill it before this, but just in case, don't wait forever.
            latch.await(1, TimeUnit.MINUTES)
            if (validClientConnected) {
              break
            }
          }
          catch (ignored: Exception) {
          }
        }
      }
    }

    if (!validClientConnected && !project.isDisposed) {
      val bannerService = InspectorBannerService.getInstance(project)
      // Save the banner so we can put it back after it's cleared by the client change, to show the error that made us disconnect.
      val currentBanner = bannerService.notification
      activeClient = DisconnectedClient
      if (enabled) {
        // If we're enabled, don't show the process as selected anymore. If we're not (the window is minimized), we'll try to reconnect
        // when we're reenabled, so leave the process selected.
        processes.selectedProcess = null
      }
      bannerService.notification = currentBanner
    }
  }

  var activeClient: InspectorClient = DisconnectedClient
    private set(value) {
      if (field != value) {
        if (field.isConnected) {
          field.disconnect()
        }
        Disposer.dispose(field)
        field = value
        clientChangedCallbacks.forEach { callback -> callback(value) }
        value.connect()
      }
    }

  /**
   * Whether or not this launcher will currently respond to new processes or not. With this
   * property, we can stop launching new inspectors when the parent tool window is minimized.
   *
   * If the launcher is enabled while the current client is disconnected, this class will attempt
   * to relaunch the currently selected process, if any. This mimics the user starting an activity
   * if the tool window had been open at the time.
   */
  var enabled = true
    set(value) {
      if (field != value) {
        field = value
        if (!activeClient.isConnected && value) {
          // If here, we may be re-enabling this launcher after previously disabling it (the "isConnected" check above could indicate that
          // the user minimized the inspector and then stopped inspection or the running process afterwards). Now that we're re-enabling,
          // we try to autoconnect but only if we find a valid, running process.
          processes.selectedProcess?.let { process ->
            val runningProcess = process.takeIf { it.isRunning }
                                 ?: processes.processes.firstOrNull { it.pid == process.pid && it.isRunning }

            if (runningProcess != null) {
              processes.selectedProcess = runningProcess // As a side effect, will ensure the pulldown is updated
              executor.execute { handleProcess(processes.selectedProcess, isInstantlyAutoConnected = false) }
            }
          }
        }
      }
    }

  private val clientChangedCallbacks = mutableListOf<(InspectorClient) -> Unit>()

  /**
   * Register a callback that is triggered whenever the active client changes.
   *
   * Such listeners are useful for handling setup that should happen just before client connection
   * happens.
   */
  fun addClientChangedListener(callback: (InspectorClient) -> Unit) {
    clientChangedCallbacks.add(callback)
  }

  @TestOnly
  fun disconnectActiveClient(timeout: Long = Long.MAX_VALUE, unit: TimeUnit = TimeUnit.SECONDS) {
    if (activeClient.isConnected) {
      val latch = CountDownLatch(1)
      activeClient.registerStateCallback { state -> if (state == InspectorClient.State.DISCONNECTED) latch.countDown() }
      activeClient.disconnect()
      latch.await(timeout, unit)
    }
  }
}
