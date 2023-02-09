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
package com.android.build.attribution.ui.controllers

import com.android.build.attribution.data.StudioProvidedInfo
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

class ConfigurationCacheTestBuildFlowRunner(val project: Project) {

  private var testConfigurationCacheBuildRequest: GradleBuildInvoker.Request? = null
  var runningFirstConfigurationCacheBuild: Boolean = false

  fun isTestConfigurationCacheBuild(request: GradleBuildInvoker.Request): Boolean =
    testConfigurationCacheBuildRequest?.let { it == request } ?: false

  companion object {
    fun getInstance(project: Project): ConfigurationCacheTestBuildFlowRunner {
      return project.getService(ConfigurationCacheTestBuildFlowRunner::class.java)
    }
  }

  fun startTestBuildsFlow(
    originalBuildRequestData: GradleBuildInvoker.Request.RequestData,
    isFeatureConsideredStable: Boolean
  ) {
    runFirstConfigurationCacheTestBuildWithConfirmation(originalBuildRequestData, isFeatureConsideredStable)
  }

  private val confirmationDialogHeader = "Configuration Cache Compatibility Assessment"

  private fun runFirstConfigurationCacheTestBuildWithConfirmation(
    originalBuildRequestData: GradleBuildInvoker.Request.RequestData,
    isFeatureConsideredStable: Boolean,
  ) {
    invokeLater(ModalityState.NON_MODAL) {
      val confirmationResult = Messages.showIdeaMessageDialog(
        project,
        """
          |This test will rerun the latest build twice to check that Gradle can serialize the task graph
          |and then reuse it from the cache. The builds will run in the background and they will fail
          |with details if any incompatibilities are detected.
        """.trimMargin(),
        confirmationDialogHeader,
        arrayOf("Run Builds", Messages.getCancelButton()), 0,
        Messages.getInformationIcon(), null
      )
      if (confirmationResult == Messages.OK) {
        scheduleRebuildWithCCOptionAndRunOnSuccess(originalBuildRequestData, firstBuild = true, onBuildFailure = this::showFailureMessage) {
          invokeLater {
            scheduleRebuildWithCCOptionAndRunOnSuccess(
              originalBuildRequestData,
              firstBuild = false,
              onBuildFailure = this::showFailureMessage
            ) {
              showFinalSuccessMessage(isFeatureConsideredStable)
            }
          }
        }
      }
    }
  }

  private fun showFinalSuccessMessage(isFeatureConsideredStable: Boolean) {
    invokeLater(ModalityState.NON_MODAL) {
      val message = if (isFeatureConsideredStable) {
        """
        |Both trial builds with Configuration cache on were successful. You can turn on
        |Configuration cache in gradle.properties.
        |
        |Note: We only tested a basic scenario for your build compatibility with Configuration cache,
        |there may be more hidden incompatibilities with different tasks’ runs in the future.
        """.trimMargin()
      }
      else {
        """
        |Both trial builds with Configuration cache on were successful. You can turn on
        |Configuration cache in gradle.properties.
        |
        |Note: Configuration cache is an experimental feature of Gradle and there may be
        |incompatibilities with different tasks’ runs in the future.
        """.trimMargin()
      }
      val confirmationResult = Messages.showIdeaMessageDialog(
        project,
        message,
        confirmationDialogHeader,
        arrayOf("Enable Configuration Cache", Messages.getCancelButton()), 0,
        Messages.getInformationIcon(), null
      )
      if (confirmationResult == Messages.OK) StudioProvidedInfo.turnOnConfigurationCacheInProperties(project)
    }
  }

  fun scheduleRebuildWithCCOptionAndRunOnSuccess(
    originalBuildRequestData: GradleBuildInvoker.Request.RequestData,
    firstBuild: Boolean,
    onBuildFailure: (GradleInvocationResult) -> Unit,
    onSuccess: () -> Unit
  ) {
    val request = GradleBuildInvoker.Request.Builder(project, originalBuildRequestData)
      .setCommandLineArguments(originalBuildRequestData.commandLineArguments.plus("--configuration-cache"))
      .build()

    testConfigurationCacheBuildRequest = request
    runningFirstConfigurationCacheBuild = firstBuild

    val future = GradleBuildInvoker.getInstance(project).executeTasks(request)
    Futures.addCallback(future, object : FutureCallback<GradleInvocationResult> {
      override fun onSuccess(result: GradleInvocationResult?) {
        runningFirstConfigurationCacheBuild = false
        testConfigurationCacheBuildRequest = null
        if (result!!.isBuildSuccessful) onSuccess()
        else onBuildFailure(result)
      }

      override fun onFailure(t: Throwable) {
        runningFirstConfigurationCacheBuild = false
        testConfigurationCacheBuildRequest = null
        throw t
      }
    }, MoreExecutors.directExecutor())
  }

  private fun showFailureMessage(result: GradleInvocationResult) {
    invokeLater(ModalityState.NON_MODAL) {
      if (result.isBuildCancelled) {
        Messages.showIdeaMessageDialog(
          project,
          "Build was cancelled.",
          confirmationDialogHeader,
          arrayOf(Messages.getOkButton()), 0,
          Messages.getInformationIcon(), null
        )
      }
      //TODO (b/186203445): we have configuration cache exception with a detailed message and a link to the html report inside.
      // So I can present that in the Dialog. find cause recursively?
      Messages.showIdeaMessageDialog(
        project,
        "Build failed. Please, check build output for a detailed report of incompatibilities detected by Gradle.",
        confirmationDialogHeader,
        arrayOf(Messages.getOkButton()), 0,
        Messages.getErrorIcon(), null
      )
    }
  }
}