/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.screenshottest.action

import com.android.screenshottest.listener.UpdateScreenshotTestResultsListener
import com.android.screenshottest.ui.UpdateReferenceImagesDialog
import com.android.screenshottest.util.UpdateReferenceImagesDialogManager
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.intellij.execution.DefaultExecutionTarget
import com.intellij.execution.ExecutionManager
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import javax.swing.Icon
import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment

/**
 * Base action for adding or updating reference images for screenshot tests.
 * This action is responsible for creating and running the appropriate Gradle configuration.
 */
abstract class UpdateReferenceImagesBaseAction(
  text: String,
  description: String,
  icon: Icon? = null
) : AnAction(text, description, icon) {

  private val LOG = Logger.getInstance(this.javaClass)

  override fun update(e: AnActionEvent) {
    val context = ConfigurationContext.getFromEvent(e)
    val configurations = context.createConfigurationsFromContext()
    val isVisible = configurations?.any { it.configurationSettings.name.startsWith("Screenshot Tests") } == true
    e.presentation.isEnabledAndVisible = isVisible
  }

  override fun actionPerformed(e: AnActionEvent) {
    LOG.debug("UpdateReferenceImagesBaseAction triggered for event: $e")
    val context = ConfigurationContext.getFromEvent(e)
    val project = context.project ?: return

    // Use Manager to prevent multiple dialogs/runs
    val dialog = UpdateReferenceImagesDialogManager.getInstance(project).showOrGetDialog() ?: return

    val validateRunconfigSettings = context.createConfigurationsFromContext()
                                      ?.firstOrNull { it.configurationSettings.name.startsWith("Screenshot Tests") }
                                      ?.configurationSettings
                                    ?: return
    val updateRunconfigSettings = RunManagerImpl.getInstanceImpl(project).createConfiguration(validateRunconfigSettings.configuration, validateRunconfigSettings.factory)
    updateRunconfigSettings.isTemporary = true
    updateRunconfigSettings.isActivateToolWindowBeforeRun = false

    val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID) ?: return

    val connection = project.messageBus.connect(dialog.disposable)
    connection.subscribe(AndroidTestSuiteView.ANDROID_TEST_SUITE_TOPIC, UpdateScreenshotTestResultsListener(dialog))

    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
      override fun processTerminated(
        executorId: String,
        env: ExecutionEnvironment,
        handler: ProcessHandler,
        exitCode: Int
      ) {
        // Check if this termination corresponds to our run configuration
        if (env.runnerAndConfigurationSettings == updateRunconfigSettings && exitCode != 0) {
          dialog.onBuildFailed()
        }
      }
    })

    LOG.debug("Executing gradle task for project: $project, configuration: ${updateRunconfigSettings.name}")
    ExecutionManager.getInstance(project).restartRunProfile(
      project,
      executor,
      DefaultExecutionTarget.INSTANCE,
      updateRunconfigSettings,
      null
    )
    dialog.show()
  }
}