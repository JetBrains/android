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
package com.android.tools.idea.run.tasks

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.run.LaunchInfo
import com.android.tools.idea.run.ProcessHandlerConsolePrinter
import com.android.tools.idea.run.debug.startJavaReattachingDebugger
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ANDROID_TEST_RESULT_LISTENER_KEY
import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt


/**
 * A wrapper for [ConnectDebuggerTaskBase] that need to keep reattaching the debugger.
 *
 * <p>Wires up adb listeners to automatically reconnect the debugger for each test. This is necessary when
 * using instrumentation runners that kill the instrumentation process between each test, disconnecting
 * the debugger. We listen for the start of a new test, waiting for a debugger, and reconnect.
 */
class ReattachingConnectDebuggerTask(private val base: ConnectDebuggerTaskBase,
                                     private val masterAndroidProcessName: String,
                                     private val listener: ReattachingConnectDebuggerTaskListener?) : ConnectDebuggerTask by base,
                                                                                                      ReattachingConnectDebuggerController {
  companion object {
    /**
     * Changes to [Client] instances that mean a new debugger should be connected.
     *
     * The target application can either:
     * 1. Match our target name, and become available for debugging.
     * 2. Be available for debugging, and suddenly have its name changed to match.
     */
    const val CHANGE_MASK = Client.CHANGE_DEBUGGER_STATUS or Client.CHANGE_NAME
  }

  /**
   * An internal listener that listens the changes on the target process on Android device.
   * The listener becomes non-null value while this task is actively listening to the changes,
   * otherwise the value is null.
   */
  private var myReattachingListener: AndroidDebugBridge.IClientChangeListener? = null

  override fun perform(
    launchInfo: LaunchInfo, device: IDevice, status: ProcessHandlerLaunchStatus, printer: ProcessHandlerConsolePrinter) {
    if (base is ConnectJavaDebuggerTask) {
      val processHandler: ProcessHandler = status.processHandler
      // Reuse the current ConsoleView to retain the UI state and not to lose test results.
      val androidTestResultListener = processHandler.getCopyableUserData(ANDROID_TEST_RESULT_LISTENER_KEY)

      executeOnPooledThread {
        startJavaReattachingDebugger(
          base.myProject,
          device,
          masterAndroidProcessName,
          base.myApplicationIds.toSet(),
          launchInfo.env,
          androidTestResultListener as? ConsoleView,
          processHandler::detachProcess
        )
          .then { runInEdt { it.showSessionTab() } }
      }
    }

    // Unregister the previous listener just in case there is the old one.
    stop()

    // Start listening and reattaching to the process.
    myReattachingListener = AndroidDebugBridge.IClientChangeListener { client, changeMask ->
      val data = client.clientData
      val clientDescription = data.clientDescription
      if (base.myApplicationIds.contains(clientDescription)) {
        if (changeMask and CHANGE_MASK != 0 && data.debuggerConnectionStatus == ClientData.DebuggerStatus.WAITING) {
          ApplicationManager.getApplication().invokeLater {
            // Make sure the Android session is still active. b/156897049.
            val descriptor = RunContentManager.getInstance(base.myProject).findContentDescriptor(launchInfo.executor, status.processHandler)
            if (descriptor != null && descriptor.component != null) {
              base.launchDebugger(launchInfo, client, status, printer)
            }
          }
        }
      }
    }
    AndroidDebugBridge.addClientChangeListener(myReattachingListener)

    listener?.onStart(launchInfo, device, this)

  }

  /**
   * Stops reattaching to the target process.
   */
  override fun stop() {
    if (myReattachingListener != null) {
      AndroidDebugBridge.removeClientChangeListener(myReattachingListener)
    }
    myReattachingListener = null
  }

  /**
   * Returns the current reattaching listener.
   * <p>Do not use this method in production code.
   */
  @VisibleForTesting
  fun getReattachingListenerForTesting() = myReattachingListener
}

/**
 * An interface to control the reattaching debug connector.
 */
interface ReattachingConnectDebuggerController {
  /**
   * Stops monitoring processes running on device and stop reattaching debugger to
   * the target process.
   */
  fun stop()
}

/**
 * An interface to listen event from the reattaching debug connector.
 */
interface ReattachingConnectDebuggerTaskListener {
  /**
   * This method is invoked when the reattaching debug connector starts monitoring
   * processes on device and reattaching to the target process.
   */
  fun onStart(launchInfo: LaunchInfo,
              device: IDevice,
              controller: ReattachingConnectDebuggerController)
}