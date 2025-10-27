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

import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.impl.RunManagerImpl
import com.android.screenshottest.ui.UpdateReferenceImagesDialog
import com.android.screenshottest.listener.UpdateScreenshotTestResultsListener
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Action to add or update the reference images for screenshot tests.
 * This action must be stateless, as the IDE creates a single instance.
 */
class UpdateReferenceImagesAction : AnAction(
  "Add/Update Reference Images",
  "Updates the reference images for screenshot tests.",
  AllIcons.FileTypes.Image
) {

  override fun actionPerformed(e: AnActionEvent) {
    val context = ConfigurationContext.getFromEvent(e)
    val project = context.project ?: return

    val validateRunconfigSettings = context.createConfigurationsFromContext()?.firstOrNull()?.configurationSettings
                           ?: return
    val updateRunconfigSettings = RunManagerImpl.getInstanceImpl(project).createConfiguration(validateRunconfigSettings.configuration, validateRunconfigSettings.factory)
    updateRunconfigSettings.isTemporary = true
    updateRunconfigSettings.isActivateToolWindowBeforeRun = false
    val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID) ?: return

    val dialog = UpdateReferenceImagesDialog(project)

    project.messageBus.connect(dialog.disposable).subscribe(AndroidTestSuiteView.ANDROID_TEST_SUITE_TOPIC, UpdateScreenshotTestResultsListener(dialog))
    ExecutionUtil.runConfiguration(updateRunconfigSettings, executor)
    dialog.show()
  }
}