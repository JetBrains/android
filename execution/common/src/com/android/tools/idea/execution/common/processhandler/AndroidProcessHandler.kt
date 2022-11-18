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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.WorkerThread
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.execution.common.AppRunConfiguration
import com.android.tools.idea.run.DeploymentApplicationService
import com.android.tools.idea.run.deployable.SwappableProcessHandler
import com.intellij.execution.DefaultExecutionTarget
import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.KillableProcess
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.VisibleForTesting
import java.io.OutputStream

/**
 * A [ProcessHandler] that corresponds to a single Android app potentially running on multiple connected devices.
 *
 * This process handler monitors remote processes running on Android devices with an application name of [targetApplicationId].
 * You can add Android device by [addTargetDevice] and logcat messages from all monitored devices will be redirected and broadcast
 * by [notifyTextAvailable].
 *
 * As same as regular process handler, it has two terminal states, detach and destroy.
 *
 * You can reach at detach state only if you call [detachProcess] before no target processes start or while those processes are running.
 * When you detach, all those processes are kept running and this process handler just stops capturing logcat messages from them.
 *
 * There are two ways you can get to destroy state. First, if you call [destroyProcess] method, this process handler terminates all
 * running target processes and moves to destroy state. Second, when all target processes terminate and [autoTerminate] is true,
 * this process handler automatically terminate.
 *
 * @param project IDE project which uses this process handler
 * @param targetApplicationId a target application id to be monitored
 * @param finishAndroidProcessCallback custom way to finish a started process, used by AndroidProcessMonitorManager.
 * @param deploymentApplicationService a service to be used to look up running processes on a device
 * @param androidProcessMonitorManagerFactory a factory method to construct [AndroidProcessMonitorManager]
 */
class AndroidProcessHandler @JvmOverloads constructor(
  private val project: Project,
  @VisibleForTesting val targetApplicationId: String,
  finishAndroidProcessCallback: (IDevice) -> Unit = { device -> device.forceStop(targetApplicationId) },
  val autoTerminate: Boolean = true,
  private val ansiEscapeDecoder: AnsiEscapeDecoder = AnsiEscapeDecoder(),
  private val deploymentApplicationService: DeploymentApplicationService = DeploymentApplicationService.instance,
  androidProcessMonitorManagerFactory: AndroidProcessMonitorManagerFactory = { textEmitter, listener ->
    AndroidProcessMonitorManager(targetApplicationId, deploymentApplicationService, textEmitter, listener,
                                 finishAndroidProcessCallback)
  }) : ProcessHandler(), KillableProcess, SwappableProcessHandler {

  companion object {
    private var LOG = Logger.getInstance(AndroidProcessHandler::class.java)
  }

  init {
    putCopyableUserData(SwappableProcessHandler.EXTENSION_KEY, this)
  }

  /**
   * Logcat messages from all target devices are redirected to [notifyTextAvailable]. When all target processes terminate on
   * all devices, it invokes [notifyProcessTerminated] to terminate android process handler.
   */
  private val myMonitorManager = androidProcessMonitorManagerFactory(
    object : TextEmitter {
      override fun emit(message: String, key: Key<*>) = notifyTextAvailable(message, key)
    },
    object : AndroidProcessMonitorManagerListener {
      override fun onAllTargetProcessesTerminated() {
        if (autoTerminate) {
          notifyProcessTerminated(0)
        }
      }
    })

  override fun notifyTextAvailable(text: String, outputType: Key<*>) {
    ansiEscapeDecoder.escapeText(text, outputType) { processedText, attributes ->
      super.notifyTextAvailable(processedText, attributes)
    }
  }

  /**
   * Adds a target device to this handler.
   */
  @AnyThread
  fun addTargetDevice(device: IDevice) {
    myMonitorManager.add(device)

    LOG.info("Adding device ${device.name} to monitor for launched app: ${targetApplicationId}")
  }

  /**
   * Kills the target [Client] on the target [device] and restarts monitoring.
   *
   * @return true if the [device] was already being monitored, false otherwise
   */
  @WorkerThread
  fun killClientAndRestartMonitor(device: IDevice): Boolean {
    return myMonitorManager.closeAndReplace(device) != null
  }

  /**
   * Detaches a given device from target devices. No-op if the given device is not associated with this handler.
   * If the target device list becomes empty after detaching a given device, it calls [detachProcess].
   */
  @WorkerThread
  fun detachDevice(device: IDevice) {
    myMonitorManager.detachDevice(device)
    if (myMonitorManager.isEmpty()) {
      detachProcess()
    }
  }

  /**
   * Checks if a given device is monitored by this handler. Returns true if it is monitored otherwise false.
   */
  @AnyThread
  fun isAssociated(device: IDevice) = myMonitorManager.isAssociated(device)

  /**
   * Returns jdwp client of a target application running on a given device, or null if the device is not monitored by
   * this handler or the process is not running on a device.
   */
  @WorkerThread
  fun getClient(device: IDevice): Client? {
    return if (isAssociated(device)) {
      deploymentApplicationService.findClient(device, targetApplicationId).firstOrNull()
    }
    else {
      null
    }
  }

  /**
   * Initiates a termination of managed processes. This method returns without waiting for processes' termination.
   * It just moves the process handler's state to to-be-destroyed state and [isProcessTerminating] becomes true
   * after the method call. Upon the processes termination, the state moves to destroyed and [isProcessTerminated]
   * becomes true. You can listen state changes by registering a lister by [addProcessListener]. When processes are
   * being destroyed, [com.intellij.execution.process.ProcessListener.processWillTerminate] is called with
   * willBeDestroyed = true.
   */
  @AnyThread
  override fun destroyProcessImpl() {
    AppExecutorUtil.getAppExecutorService().submit {
      myMonitorManager.close()
      notifyProcessTerminated(0)
    }
  }

  /**
   * Initiates a detach of managed processes. This method returns without waiting for processes' to be detached.
   * It just moves the process handler's state to to-be-destroyed state and [isProcessTerminating] becomes true
   * after the method call. Upon the processes are detached, the state moves to destroyed and [isProcessTerminated]
   * becomes true. You can listen state changes by registering a lister by [addProcessListener]. When processes are
   * being destroyed, [com.intellij.execution.process.ProcessListener.processWillTerminate] is called with
   * willBeDestroyed = false.
   */
  @AnyThread
  override fun detachProcessImpl() {
    AppExecutorUtil.getAppExecutorService().submit {
      myMonitorManager.detachAndClose()
      notifyProcessDetached()
    }
  }

  @AnyThread
  override fun detachIsDefault() = false

  @AnyThread
  override fun getProcessInput(): OutputStream? = null

  /**
   * We provide a custom implementation to tie the device combo box selector to the global Stop button.
   * Note the global Stop button prefers the result of this method over content descriptor internal state,
   * but the tool window Stop button prefers the content descriptor internal state over this method.
   */
  @AnyThread
  override fun canKillProcess(): Boolean {
    val activeTarget = ExecutionTargetManager.getInstance(project).activeTarget
    if (activeTarget === DefaultExecutionTarget.INSTANCE || activeTarget !is AndroidExecutionTarget) {
      return false
    }
    return areAnyDevicesAssociated(activeTarget)
  }

  @AnyThread
  override fun killProcess() {
    destroyProcess()
  }

  @AnyThread
  override fun getExecutor() = getUserData(AndroidSessionInfo.KEY)?.executor

  @AnyThread
  override fun isRunningWith(runConfiguration: RunConfiguration, executionTarget: ExecutionTarget): Boolean {
    val sameRunningApp = runConfiguration is AppRunConfiguration && runConfiguration.appId == targetApplicationId
    if (!sameRunningApp) {
      return false
    }

    if (executionTarget is AndroidExecutionTarget) {
      return areAnyDevicesAssociated(executionTarget)
    }

    return false
  }

  @AnyThread
  private fun areAnyDevicesAssociated(executionTarget: AndroidExecutionTarget): Boolean {
    return executionTarget.runningDevices.any { isAssociated(it) }
  }

  override fun toString(): String {
    return "AndroidProcessHandler[$targetApplicationId]"
  }
}

private typealias AndroidProcessMonitorManagerFactory = (textEmitter: TextEmitter,
                                                         listener: AndroidProcessMonitorManagerListener) -> AndroidProcessMonitorManager