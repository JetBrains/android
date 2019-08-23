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
package com.android.tools.idea.run

import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.IDevice
import com.android.tools.idea.run.SingleDeviceAndroidProcessMonitorState.PROCESS_NOT_FOUND
import com.intellij.execution.process.ProcessOutputTypes
import java.io.Closeable
import javax.annotation.concurrent.GuardedBy

/**
 * A thread-safe manager to manage multiple [SingleDeviceAndroidProcessMonitor]s concurrently.
 *
 * You can add devices by [add] method. The manager starts monitoring a device as soon as it is added by the method.
 *
 * In order to release resources properly, you must call either [close] or [detachAndClose].
 *
 * @param targetApplicationId a target application id to be monitored
 * @param deploymentApplicationService a service to be used to look up running processes on a device
 * @param textEmitter a text emitter to be used to output message from this class such as logcat message and error messages
 * @param listener a listener to listen events from this manager
 * @param singleDeviceAndroidProcessMonitorFactory a factory method to constructor single device android process monitor
 */
class AndroidProcessMonitorManager(
  private val targetApplicationId: String,
  private val deploymentApplicationService: DeploymentApplicationService,
  private val textEmitter: TextEmitter,
  private val listener: AndroidProcessMonitorManagerListener,
  private val singleDeviceAndroidProcessMonitorFactory: SingleDeviceAndroidProcessMonitorFactory =
    { _, device, monitorListener, _, logcatCaptor ->
      SingleDeviceAndroidProcessMonitor(targetApplicationId,
                                        device,
                                        monitorListener,
                                        deploymentApplicationService,
                                        logcatCaptor,
                                        textEmitter)
    }
) : Closeable {
  @GuardedBy("this")
  private val myMonitors: HashMap<IDevice, SingleDeviceAndroidProcessMonitor> = HashMap()

  private val myMonitorListener = object : SingleDeviceAndroidProcessMonitorStateListener {
    override fun onStateChanged(monitor: SingleDeviceAndroidProcessMonitor, newState: SingleDeviceAndroidProcessMonitorState) {
      if (newState == PROCESS_NOT_FOUND) {
        textEmitter.emit(
          "Timed out waiting for process (${monitor.targetApplicationId}) to appear on ${monitor.targetDevice.name}.\n",
          ProcessOutputTypes.STDOUT)
      }

      if (newState.isTerminalState()) {
        remove(monitor.targetDevice)
      }
    }
  }

  private val logcatCaptor = AndroidLogcatOutputCapture(textEmitter)

  /**
   * Adds a [device] and starts monitoring processes on the device. If the given device has been added already, it will be no-op.
   */
  @WorkerThread
  @Synchronized
  fun add(device: IDevice) {
    myMonitors.computeIfAbsent(device) {
      singleDeviceAndroidProcessMonitorFactory.invoke(
        targetApplicationId, device, myMonitorListener, deploymentApplicationService, logcatCaptor)
    }
  }

  /**
   * Returns true if a given [device] is managed by this class.
   */
  @WorkerThread
  @Synchronized
  fun isAssociated(device: IDevice) = myMonitors.containsKey(device)

  /**
   * Removes a [device] and notifies [AndroidProcessMonitorManagerListener.onAllTargetProcessesTerminated] if this is the very last one.
   */
  @WorkerThread
  private fun remove(device: IDevice) {
    var allTargetProcessFinished = false
    synchronized(this) {
      if (myMonitors.remove(device) != null) {
        allTargetProcessFinished = myMonitors.isEmpty()
      }
    }

    if (allTargetProcessFinished) {
      listener.onAllTargetProcessesTerminated()
    }
  }

  /**
   * Terminates all target processes running on monitored devices and stops capturing logcat messages from devices.
   */
  @Synchronized
  override fun close() {
    myMonitors.values.forEach { it.close() }
    myMonitors.clear()
    logcatCaptor.stopAll()
  }

  /**
   * Detaches monitor from devices and stops capturing logcat messages from devices.
   * Unlike [close], all target processes will not be terminated and leave running.
   */
  @Synchronized
  fun detachAndClose() {
    myMonitors.values.forEach { it.detachAndClose() }
    close()
  }
}

/**
 * An interface to listen an event from [AndroidProcessMonitorManager].
 */
interface AndroidProcessMonitorManagerListener {
  /**
   * This method is invoked when the very last target process among all target devices terminate.
   */
  fun onAllTargetProcessesTerminated()
}

private typealias SingleDeviceAndroidProcessMonitorFactory =
  (targetApplicationId: String,
   targetDevice: IDevice,
   listener: SingleDeviceAndroidProcessMonitorStateListener,
   deploymentApplicationService: DeploymentApplicationService,
   androidLogcatOutputCapture: AndroidLogcatOutputCapture) -> SingleDeviceAndroidProcessMonitor
