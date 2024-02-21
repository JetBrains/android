/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.execution.common.deploy

import com.android.tools.deployer.Deployer
import com.android.tools.deployer.DeployerException
import com.android.tools.idea.execution.common.AndroidExecutionException
import com.android.tools.idea.execution.common.RunConfigurationNotifier
import com.android.tools.idea.execution.common.applychanges.ApplyChangesAction
import com.intellij.execution.CantRunException
import com.intellij.execution.ExecutionException
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger

/**
 * Performs the deployer action and handles [DeployerException].
 *
 * If the deployer action is successfully performed, it returns a list of [Deployer.Result]. Each APK has its own [Deployer.Result].
 * If the deployer action fails and the resolution action is [DeployerException.ResolutionAction.NONE], it throws an [AndroidExecutionException].
 * If the resolution action is [DeployerException.ResolutionAction.RETRY] and [automaticallyApplyResolutionAction] is true, it retries once.
 *
 * For any other resolution actions, if [automaticallyApplyResolutionAction] is true, it performs the action and notify user about it; otherwise, it shows a balloon error
 * with a link for performing the action. in both cases throws [CantRunException.CustomProcessedCantRunException] to avoid duplicates in notifications/error balloons.
 *
 * Regardless of the resolution action, the function **always** throws an [ExecutionException] if the deployer action is not successful.
 *
 * @param env The execution environment.
 * @param deployerAction The action to be performed by the deployer.
 * @param automaticallyApplyResolutionAction Indicates whether to automatically apply the resolution action or show a balloon error.
 * @return A list of [Deployer.Result] objects if the deployer action is successful.
 * @throws AndroidExecutionException if the deployer action fails and no resolution action is specified.
 * @throws CantRunException.CustomProcessedCantRunException if resolution action is specified to avoid notification duplicates.
 */
@Throws(ExecutionException::class)
fun deployAndHandleError(
  env: ExecutionEnvironment, deployerAction: () -> List<Deployer.Result>, automaticallyApplyResolutionAction: Boolean = false
): List<Deployer.Result> {
  val LOG = Logger.getInstance(::deployAndHandleError.javaClass)

  try {
    return deployerAction()
  } catch (e: DeployerException) {
    LOG.warn("Installation failed: ${e.message} ${e.details}")

    val error = e.error
    var resolutionAction = error.resolution
    if (resolutionAction == DeployerException.ResolutionAction.NONE) {
      throw AndroidExecutionException(e.id, e.message)
    }
    if (resolutionAction == DeployerException.ResolutionAction.RETRY && automaticallyApplyResolutionAction) {
      LOG.info("Retrying previous deploy action")
      return deployAndHandleError(env, deployerAction, false)
    }

    val bubbleError = StringBuilder("Installation failed")
    var callToAction = error.callToAction

    if (env.executor.id == DefaultDebugExecutor.EXECUTOR_ID && resolutionAction == DeployerException.ResolutionAction.APPLY_CHANGES) {

      // Resolutions to Apply Changes in Debug mode needs to be remapped to Rerun.
      callToAction = "Rerun"
      resolutionAction = DeployerException.ResolutionAction.RUN_APP
    }
    val actionName = when (resolutionAction) {
      DeployerException.ResolutionAction.APPLY_CHANGES -> ApplyChangesAction.ID
      DeployerException.ResolutionAction.RUN_APP, DeployerException.ResolutionAction.RETRY -> env.executor.actionName
      else -> throw RuntimeException("Unknown resolution action: $resolutionAction")
    }

    val actionRunnable = createRunnable(actionName)

    if (automaticallyApplyResolutionAction) {
      bubbleError.append('\n')
      bubbleError.append("$callToAction will be done automatically")
      RunConfigurationNotifier.notifyError(env.project, env.runProfile.name, bubbleError.toString())
      ApplicationManager.getApplication().invokeLater(actionRunnable)
    }
    else {
      val notificationAction = NotificationAction.createSimpleExpiring(callToAction!!, actionRunnable)
      bubbleError.append('\n')
      bubbleError.append("Suggested action:")
      RunConfigurationNotifier.notifyErrorWithAction(env.project, env.runProfile.name, bubbleError.toString(), notificationAction)
    }

    throw AndroidExecutionException(e.id, "${e.message} ${e.details}")
  }
}

private fun createRunnable(actionName: String): Runnable {
  val action = ActionManager.getInstance().getAction(actionName)
  return Runnable { ActionManager.getInstance().tryToExecute(action, null, null, null, true) }
}