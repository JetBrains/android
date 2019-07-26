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
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.tools.idea.run.deployable.SwappableProcessHandler
import com.android.tools.idea.run.deployment.AndroidExecutionTarget
import com.intellij.execution.DefaultExecutionTarget
import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.io.OutputStream

/**
 * A thread-safe implementation of [AndroidProcessHandler].
 *
 * This process handler monitors remote processes running on Android devices with an application name of [targetApplicationId].
 * You can add Android device by [addTargetDevice] and logcat messages from all monitored devices will be redirected and broadcast
 * by [notifyTextAvailable].
 *
 * As same as regular process handler, [AndroidProcessHandler] has two terminal states, detach and destroy.
 *
 * You can reach at detach state only if you call [detachProcess] before no target processes start or while those processes are running.
 * When you detach, all those processes are kept running and this process handler just stops capturing logcat messages from them.
 *
 * There are two ways you can get to destroy state. First, if you call [destroyProcess] method, this process handler terminates all
 * running target processes and moves to destroy state. Second, when all target processes terminate this process handler automatically
 * terminate.
 *
 * @param project IDE project which uses this process handler
 * @param targetApplicationId a target application id to be monitored
 * @param deploymentApplicationService a service to be used to look up running processes on a device
 * @param androidProcessMonitorManagerFactory a factory method to construct [AndroidProcessMonitorManager]
 */
class AndroidProcessHandlerImpl @JvmOverloads constructor(
  private val project: Project,
  private val targetApplicationId: String,
  private val deploymentApplicationService: DeploymentApplicationService = DeploymentApplicationService.getInstance(),
  androidProcessMonitorManagerFactory: AndroidProcessMonitorManagerFactory = { _, _, textEmitter, listener ->
    AndroidProcessMonitorManager(targetApplicationId, deploymentApplicationService, textEmitter, listener)
  }) : AndroidProcessHandler() {

  companion object {
    private var LOG = Logger.getInstance(AndroidProcessHandlerImpl::class.java)
  }

  init {
    putCopyableUserData(SwappableProcessHandler.EXTENSION_KEY, this)
  }

  /**
   * Logcat messages from all target devices are redirected to [notifyTextAvailable]. When all target processes terminate on
   * all devices, it invokes [destroyProcess] to terminate android process handler.
   */
  private val myMonitorManager = androidProcessMonitorManagerFactory.invoke(
    targetApplicationId,
    deploymentApplicationService,
    object : TextEmitter {
      override fun emit(message: String, key: Key<*>) = notifyTextAvailable(message, key)
    },
    object : AndroidProcessMonitorManagerListener {
      override fun onAllTargetProcessesTerminated() = destroyProcess()
    })

  @WorkerThread
  override fun addTargetDevice(device: IDevice) {
    myMonitorManager.add(device)

    // Keep track of the lowest API level among the monitored devices by this handler.
    synchronized(this) {
      val lowestApiLevel = getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)
      if (lowestApiLevel == null || device.version < lowestApiLevel) {
        putUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL, device.version)
      }
    }

    LOG.info("Adding device ${device.name} to monitor for launched app: ${targetApplicationId}")
  }

  @WorkerThread
  override fun isAssociated(device: IDevice) = myMonitorManager.isAssociated(device)

  @WorkerThread
  override fun getClient(device: IDevice): Client? {
    return if (isAssociated(device)) {
      deploymentApplicationService.findClient(device, targetApplicationId).firstOrNull()
    }
    else {
      null
    }
  }

  override fun destroyProcessImpl() {
    myMonitorManager.close()
    notifyProcessTerminated(0)
  }

  override fun detachProcessImpl() {
    myMonitorManager.detachAndClose()
    notifyProcessDetached()
  }

  override fun detachIsDefault() = false

  override fun getProcessInput(): OutputStream? = null

  /**
   * We provide a custom implementation to tie the device combo box selector to the global Stop button.
   * Note the global Stop button prefers the result of this method over content descriptor internal state,
   * but the tool window Stop button prefers the content descriptor internal state over this method.
   */
  override fun canKillProcess(): Boolean {
    val activeTarget = ExecutionTargetManager.getInstance(project).activeTarget
    if (activeTarget === DefaultExecutionTarget.INSTANCE || activeTarget !is AndroidExecutionTarget) {
      return false
    }
    return activeTarget.iDevice?.let { myMonitorManager.isAssociated(it) } ?: false
  }

  override fun killProcess() {
    destroyProcess()
  }

  override fun getExecutor() = getUserData(AndroidSessionInfo.KEY)?.executor

  override fun isRunningWith(runConfiguration: RunConfiguration, executionTarget: ExecutionTarget): Boolean {
    val sessionInfo = getUserData(AndroidSessionInfo.KEY) ?: return false
    if (sessionInfo.runConfiguration !== runConfiguration) {
      return false
    }

    if (executionTarget is AndroidExecutionTarget) {
      return executionTarget.iDevice?.let { myMonitorManager.isAssociated(it) } ?: false
    }

    return sessionInfo.executionTarget.id == executionTarget.id
  }
}

private typealias AndroidProcessMonitorManagerFactory = (targetApplicationId: String,
                                                         deploymentApplicationService: DeploymentApplicationService,
                                                         textEmitter: TextEmitter,
                                                         listener: AndroidProcessMonitorManagerListener) -> AndroidProcessMonitorManager