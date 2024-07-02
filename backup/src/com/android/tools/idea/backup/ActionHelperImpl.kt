/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.backup

import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.run.editor.DeployTargetContext
import com.android.tools.idea.run.editor.DeployTargetProvider
import com.intellij.CommonBundle
import com.intellij.execution.RunManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

/** Production implementation of [ActionHelper] */
class ActionHelperImpl : ActionHelper {
  override fun getApplicationId(project: Project): String? {
    val runManager = RunManager.getInstance(project)
    val runConfig = runManager.selectedConfiguration?.configuration ?: return null
    return project.getProjectSystem().getApplicationIdProvider(runConfig)?.packageName
  }

  override fun getDeployTargetCount(project: Project) =
    getDeployTarget(project)?.getAndroidDevices(project)?.size ?: 0

  override suspend fun getDeployTargetSerial(project: Project): String? {
    val deployTarget = getDeployTarget(project) ?: return null
    val targets = deployTarget.getAndroidDevices(project)
    if (targets.size != 1) {
      return null
    }
    val target = targets.firstOrNull { it.isRunning } ?: return null
    return target.launchedDevice.await().serialNumber
  }

  override suspend fun showWarning(project: Project, title: String, message: String) {
    withContext(uiThread) {
      @Suppress("UnstableApiUsage")
      MessageDialogBuilder.Message(title, message)
        .buttons(CommonBundle.getOkButtonText())
        .asWarning()
        .show(project)
    }
  }

  private fun getDeployTarget(project: Project): DeployTarget? {
    val targetProvider: DeployTargetProvider =
      DeployTargetContext().getCurrentDeployTargetProvider()
    return when (targetProvider.requiresRuntimePrompt(project)) {
      true -> null
      false -> targetProvider.getDeployTarget(project)
    }
  }
}
