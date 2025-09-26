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

package com.android.screenshottest.run

import com.android.screenshottest.ui.UpdateReferenceImagesDialog
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager

/**
 * Action to add or update the reference images for screenshot tests.
 */
class UpdateReferenceImagesAction : AnAction("Add/Update Reference Images",
                                             "Updates the reference images for screenshot tests.",
                                             AllIcons.FileTypes.Image) {

  override fun actionPerformed(e: AnActionEvent) {
    val context = ConfigurationContext.getFromEvent(e)
    val project = context.project ?: return
    val validateScreenshotRunConfig = context.createConfigurationsFromContext()?.firstOrNull() ?: return
    val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID) ?: return

    val dialog = UpdateReferenceImagesDialog(project)

    project.messageBus
      .connect(dialog.disposable)
      .subscribe(
        AndroidTestSuiteView.ANDROID_TEST_SUITE_TOPIC,
          object: AndroidTestResultListener {
            override fun onTestCaseFinished(device: AndroidDevice, testSuite: AndroidTestSuite, testCase: AndroidTestCase) {
              val additionalTestArtifacts = testCase.additionalTestArtifacts
              val newImage = additionalTestArtifacts["PreviewScreenshot.newImagePath"]
              val refImage = additionalTestArtifacts["PreviewScreenshot.refImagePath"]
              val diffImage = additionalTestArtifacts["PreviewScreenshot.diffImagePath"]

              val isScreenshotTestCase = (newImage != null || refImage != null || diffImage != null)
              if (isScreenshotTestCase) {
                // TODO: Add this test result to the dialog.
              }
            }
            override fun onTestSuiteFinished(device: AndroidDevice, testSuite: AndroidTestSuite) {
              ApplicationManager.getApplication().invokeLater {
                dialog.onImageLoadCompleted()
              }
            }
          }
      )

    ExecutionUtil.runConfiguration(validateScreenshotRunConfig.configurationSettings, executor)

    dialog.show()
  }
}
