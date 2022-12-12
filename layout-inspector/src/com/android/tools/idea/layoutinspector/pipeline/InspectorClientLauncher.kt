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
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorSessionMetrics
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyClient
import com.android.tools.idea.layoutinspector.tree.TreeSettings
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.rd.util.threadLocalWithInitial
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
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
  private val scope: CoroutineScope,
  private val parentDisposable: Disposable,
  private val metrics: LayoutInspectorSessionMetrics? = null,
  @VisibleForTesting executor: Executor? = null
) {
  companion object {

    /**
     * Convenience method for creating a launcher with useful client creation rules used in production
     */
    fun createDefaultLauncher(
      processes: ProcessesModel,
      model: InspectorModel,
      metrics: LayoutInspectorSessionMetrics,
      treeSettings: TreeSettings,
      inspectorClientSettings: InspectorClientSettings,
      coroutineScope: CoroutineScope,
      parentDisposable: Disposable
    ): InspectorClientLauncher {
      return InspectorClientLauncher(
        processes,
        listOf(
          { params ->
            if (params.process.device.apiLevel >= AndroidVersion.VersionCodes.Q) {
              // Only Q devices or newer support image updates which is used by the app inspection agent
              AppInspectionInspectorClient(
                params.process,
                params.isInstantlyAutoConnected,
                model,
                metrics,
                treeSettings,
                inspectorClientSettings,
                coroutineScope,
                parentDisposable
              )
            }
            else {
              null
            }
          },
          { params -> LegacyClient(params.process, params.isInstantlyAutoConnected, model, metrics, coroutineScope, parentDisposable) }
        ),
        model.project,
        coroutineScope,
        parentDisposable,
        metrics)
    }
  }

  interface Params {
    val process: ProcessDescriptor
    val isInstantlyAutoConnected: Boolean
    val disposable: Disposable
  }

  private val sequenceNumberLock = Any()
  private var sequenceNumber = 0
  private val threadSequenceNumber = threadLocalWithInitial { -1 }
  private val workerExecutor = AndroidExecutors.getInstance().workerThreadExecutor

  init {
    val realExecutor = executor ?: object: Executor {
      private val singleThreadExecutor = Executors.newSingleThreadExecutor()
      override fun execute(command: Runnable) {
        // If we're already in a worker thread as part of a recursive call (e.g. when setting the selected process to
        // null after failing to connect) execute directly in the current thread. Otherwise execute incoming requests sequentially.
        if (threadSequenceNumber.get() > 0) {
          command.run()
        }
        else {
          singleThreadExecutor.execute(command)
        }
      }
    }

    processes.addSelectedProcessListeners(realExecutor) {
      handleProcessInWorkerThread(executor, processes.selectedProcess, processes.isAutoConnected)
    }

    Disposer.register(parentDisposable) {
      threadSequenceNumber.set(++sequenceNumber)
      try {
        activeClient = DisconnectedClient
      }
      finally {
        threadSequenceNumber.set(-1)
      }
    }
  }

  private fun handleProcessInWorkerThread(executor: Executor?, process: ProcessDescriptor?, isAutoConnected: Boolean) {
    if (!project.isDisposed) {
      val processHandler = {
        try {
          handleProcess(process, isAutoConnected)
        }
        catch (ignore: CancellationException) {
        }
      }
      // If we're already executing a recursive call in the most recent request, execute directly in the current thread.
      // If this is a new request, execute in a new worker.
      // If this is an obsolete request, do nothing.
      if (threadSequenceNumber.get() == sequenceNumber) {
        processHandler()
      }
      else if (threadSequenceNumber.get() < 0) {
        synchronized(sequenceNumberLock) {
          val threadStartedLatch = CountDownLatch(1)
          (executor ?: workerExecutor).execute {
            threadSequenceNumber.set(++sequenceNumber)
            try {
              threadStartedLatch.countDown()
              processHandler()
            }
            finally {
              threadSequenceNumber.set(-1)
            }
          }
          threadStartedLatch.await()
        }
      }
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
      metrics?.setProcess(process)
      for (createClient in clientCreators) {
        checkCancelled()
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

            activeClient = client

            // Wait until client is connected or the user stops the connection attempt.
            latch.await()

            // The current selected process changed out from under us, abort the whole thing.
            if (processes.selectedProcess?.isRunning != true || processes.selectedProcess?.pid != process.pid) {
              metrics?.logEvent(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_CANCELLED, client.stats)
              return
            }
            if (validClientConnected) {
              // Successful connected exit creator loop
              break
            }
            // This client didn't work, try the next
            // Disconnect to clean up any partial connection or leftover process
            client.disconnect()
          }
          catch (cancellationException: CancellationException) {
            // Disconnect to clean up any partial connection or leftover process
            client.disconnect()
            metrics?.logEvent(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.ATTACH_CANCELLED, client.stats)
            throw cancellationException
          }
          catch (ignored: Exception) {
            ignored.printStackTrace()
          }
        }
      }
    }

    if (!validClientConnected) {
      val bannerService = InspectorBannerService.getInstance(project) ?: return
      // Save the banner so we can put it back after it's cleared by the client change, to show the error that made us disconnect.
      val notifications = bannerService.notifications
      activeClient = DisconnectedClient
      if (enabled) {
        // If we're enabled, don't show the process as selected anymore. If we're not (the window is minimized), we'll try to reconnect
        // when we're reenabled, so leave the process selected.
        processes.selectedProcess = null
      }
      notifications.forEach { bannerService.addNotification(it.message, it.actions) }
    }
  }

  private fun checkCancelled() {
    if (threadSequenceNumber.get() < sequenceNumber) {
      throw CancellationException("Launch thread preempted")
    }
  }

  var activeClient: InspectorClient = DisconnectedClient
    private set(value) {
      if (field != value) {
        val oldClient = synchronized(sequenceNumberLock) {
          checkCancelled()
          field
        }
        oldClient.disconnect()
        Disposer.dispose(oldClient)
        synchronized(sequenceNumberLock) {
          checkCancelled()
          field = value
        }
        clientChangedCallbacks.forEach { callback -> callback(value) }
        scope.launch { value.connect(project) }
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
              // Reset the process to cause us to connect.
              processes.selectedProcess = null
              processes.selectedProcess = runningProcess
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
