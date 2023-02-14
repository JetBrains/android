/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.execution.common.processhandler

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.IDevice
import com.android.tools.idea.execution.common.processhandler.SingleDeviceAndroidProcessMonitor.Companion.APP_PROCESS_DISCOVERY_TIMEOUT_MILLIS
import com.android.tools.idea.execution.common.processhandler.SingleDeviceAndroidProcessMonitor.Companion.POLLING_INTERVAL_MILLIS
import com.android.tools.idea.execution.common.processhandler.SingleDeviceAndroidProcessMonitorState.INIT
import com.android.tools.idea.execution.common.processhandler.SingleDeviceAndroidProcessMonitorState.PROCESS_DETACHED
import com.android.tools.idea.execution.common.processhandler.SingleDeviceAndroidProcessMonitorState.PROCESS_FINISHED
import com.android.tools.idea.execution.common.processhandler.SingleDeviceAndroidProcessMonitorState.PROCESS_IS_RUNNING
import com.android.tools.idea.execution.common.processhandler.SingleDeviceAndroidProcessMonitorState.PROCESS_NOT_FOUND
import com.android.tools.idea.execution.common.processhandler.SingleDeviceAndroidProcessMonitorState.WAITING_FOR_PROCESS
import com.android.tools.idea.run.DeploymentApplicationService
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.properties.Delegates


/**
 * Monitors remote android processes with a given [targetApplicationId] running on a given [targetDevice].
 *
 * This monitor runs as a finite-state-machine and when the state changes, it will be notified via [listener].
 * The initial state is [WAITING_FOR_PROCESS] and there are three terminal states, [PROCESS_DETACHED], [PROCESS_FINISHED],
 * and [PROCESS_NOT_FOUND].
 *
 * As soon as a new instance of this class is created, it starts monitoring remote android processes on the device by polling
 * periodically with the interval of [POLLING_INTERVAL_MILLIS].
 *
 * The state moves to [PROCESS_NOT_FOUND] if no target processes are found after [APP_PROCESS_DISCOVERY_TIMEOUT_MILLIS].
 *
 * @param targetApplicationId a target application id to be monitored
 * @param targetDevice a target android device to be monitored
 * @param listener a listener to listen events from this class
 * @param deploymentApplicationService a service to be used to look up running processes on a device
 *   A collected logcat messages are emitted to [textEmitter]. You can set null if you don't need them.
 * @param textEmitter a text emitter to output debug messages to be displayed
 */
class SingleDeviceAndroidProcessMonitor(
  val targetApplicationId: String,
  val targetDevice: IDevice,
  private var listener: SingleDeviceAndroidProcessMonitorStateListener,
  private val deploymentApplicationService: DeploymentApplicationService,
  private val textEmitter: TextEmitter,
  private val finishAndroidProcessCallback: (IDevice) -> Unit,
  private val stateUpdaterExecutor: ScheduledExecutorService = AppExecutorUtil.getAppScheduledExecutorService(),
  listenerExecutor: Executor = AppExecutorUtil.getAppExecutorService()
) : Closeable {

  companion object {
    /**
     * The default polling interval to check running remote android processes in milliseconds.
     * This value is chosen heuristically.
     */
    const val POLLING_INTERVAL_MILLIS: Long = 1000

    /**
     * The default timeout for the target application discovery used in milliseconds.
     * This value (3 minutes) is chosen heuristically. This process monitor is started before APK installation but after the compilation
     * is done. The installation is typically done much faster than 3 minutes but we chose longer timeout to reduce false-positive error.
     */
    const val APP_PROCESS_DISCOVERY_TIMEOUT_MILLIS: Long = 180000
  }

  /**
   * You should acquire synchronization lock to access to this property.
   */
  @delegate:GuardedBy("this")
  private var myState: SingleDeviceAndroidProcessMonitorState by Delegates.observable(INIT) { _, _, newValue ->
    // This callback method can be invoked inside synchronization block. To avoid possible deadlock,
    // invoke the listener from different thread.
    listenerExecutor.execute {
      listener.onStateChanged(this, newValue)
    }
  }

  private val myMonitoringPids = ConcurrentHashMap<Int, Unit>()

  private var myStateUpdaterScheduledFuture: ScheduledFuture<*>? = null

  private var myTimeoutScheduledFuture: ScheduledFuture<*>? = null

  fun start() {
    assert(myState == INIT)
    myState = WAITING_FOR_PROCESS
    myStateUpdaterScheduledFuture = stateUpdaterExecutor.scheduleWithFixedDelay(
      this::updateState, 0, POLLING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
    myTimeoutScheduledFuture = stateUpdaterExecutor.schedule(
      this::timeout, APP_PROCESS_DISCOVERY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
  }

  /**
   * Updates the internal state to sync with the process' state on the target device.
   * This method is invoked by [myStateUpdaterScheduledFuture] periodically.
   */
  private fun updateState() {
    synchronized(this) {
      if (myState.isTerminalState()) {
        // Stop polling once we reach at the terminal state.
        myStateUpdaterScheduledFuture?.cancel(false)
        myStateUpdaterScheduledFuture = null
        myTimeoutScheduledFuture = null
        return
      }
    }

    val clients = deploymentApplicationService.findClient(targetDevice, targetApplicationId)
    fun startLogcatOutputCapture() {
      clients.forEach { client ->
        myMonitoringPids.computeIfAbsent(client.clientData.pid) { pid ->
          textEmitter.emit("Connected to process $pid on device '${targetDevice.name}'.\n", ProcessOutputTypes.STDOUT)
        }
      }
    }

    val isTargetProcessFound = clients.isNotEmpty()

    synchronized(this) {
      when (myState) {
        WAITING_FOR_PROCESS -> {
          if (isTargetProcessFound) {
            myState = PROCESS_IS_RUNNING
            startLogcatOutputCapture()
          }
        }
        PROCESS_IS_RUNNING -> {
          if (!isTargetProcessFound) {
            detachAndClose()
          }
        }
        else -> {
          /* Nothing to do here */
        }
      }
    }
  }

  /**
   * Moves the state to timeout if the current state is still in [WAITING_FOR_PROCESS]. If the process has been
   * found already and you call this method, this does nothing.
   */
  @Synchronized
  private fun timeout() {
    if (myState == WAITING_FOR_PROCESS) {
      myState = PROCESS_NOT_FOUND
      close()
    }
  }

  /**
   * Detaches the remote process on Android device from this monitor.
   * Unlike [close], this method does not kill the target remote processes.
   */
  @Synchronized
  fun detachAndClose() {
    if (!myState.isTerminalState()) {
      myState = PROCESS_DETACHED
      close()
    }
  }

  @Synchronized
  fun replaceListenerAndClose(newListener: SingleDeviceAndroidProcessMonitorStateListener?) {
    listener = newListener ?: object : SingleDeviceAndroidProcessMonitorStateListener {
      // dummy listener
      override fun onStateChanged(monitor: SingleDeviceAndroidProcessMonitor, newState: SingleDeviceAndroidProcessMonitorState) {}
    }
    close()
  }

  /**
   * Closes the monitor and stops monitoring. This terminates all running target processes on the monitored devices.
   * If you want to keep those process running and just stop the monitor, use [detachAndClose] instead.
   */
  @Synchronized
  @WorkerThread
  override fun close() {

    when (myState) {
      WAITING_FOR_PROCESS, PROCESS_IS_RUNNING -> {
        finishAndroidProcessCallback(targetDevice)
        // For non-debuggable app we don't have a client, so we do our best shot - force stop.
        targetDevice.forceStop(targetApplicationId)
        myState = PROCESS_FINISHED
      }

      PROCESS_DETACHED, PROCESS_FINISHED, PROCESS_NOT_FOUND -> {
        /* Nothing to do here */
      }
    }

    myStateUpdaterScheduledFuture?.cancel(false)
    myTimeoutScheduledFuture?.cancel(false)
  }
}

/**
 * Represents an state of [SingleDeviceAndroidProcessMonitor].
 */
enum class SingleDeviceAndroidProcessMonitorState {
  /**
   * The initial state.
   */
  INIT,

  /**
   * The monitor is waiting for target processes to be appear and running.
   */
  WAITING_FOR_PROCESS,

  /**
   * The target process has been found and is currently running.
   */
  PROCESS_IS_RUNNING,

  /**
   * The monitor has been detached from the target process. This is the terminal state.
   */
  PROCESS_DETACHED,

  /**
   * The target process has been found and now terminated. This is the terminal state.
   */
  PROCESS_FINISHED,

  /**
   * The target process has not found within timeout period. This is the terminal state.
   */
  PROCESS_NOT_FOUND,
}

/**
 * Returns true if the state is terminal state, otherwise false.
 */
fun SingleDeviceAndroidProcessMonitorState.isTerminalState(): Boolean {
  return when (this) {
    PROCESS_DETACHED, PROCESS_FINISHED, PROCESS_NOT_FOUND -> true
    else -> false
  }
}

/**
 * An interface to listen [SingleDeviceAndroidProcessMonitorState] changes.
 */
interface SingleDeviceAndroidProcessMonitorStateListener {
  /**
   * This method is invoked when the [monitor] state changed.
   *
   * @param monitor the monitor instance which the state changed to [newState]
   * @param newState the new current state of [monitor]
   */
  fun onStateChanged(monitor: SingleDeviceAndroidProcessMonitor, newState: SingleDeviceAndroidProcessMonitorState)
}