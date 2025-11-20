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

import com.android.screenshottest.ui.PreviewDetails
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.screenshottest.ui.UpdateReferenceImagesDialog
import com.android.screenshottest.util.UpdateReferenceImagesActionUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.icons.AllIcons

class UpdateReferenceImagesFromTestPanelAction : AnAction(UpdateReferenceImagesActionUtils.UPDATE_ACTION_TEXT,
                                                          "Updates the reference images for screenshot tests from test panel.",
                                                          AllIcons.FileTypes.Image) {

  var testResults: AndroidTestResults? = null


  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = true
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val results = testResults ?: return

    val dialog = UpdateReferenceImagesDialog(project)
    val allTestCases = results.getAllTestCases()

    for (testCase in allTestCases) {
        val artifacts = testCase.additionalTestArtifacts
        val methodName = artifacts["PreviewScreenshot.methodName"]
        val previewName = artifacts["PreviewScreenshot.previewName"]
        if (methodName != null && previewName != null) {
          val testId = "${testCase.className}.$methodName.$previewName"
          val previewDetails = PreviewDetails(
            testId = testId,
            className = testCase.className,
            methodName = methodName,
            previewName = previewName,
            testResult = testCase.result,
            destImagePath = artifacts["PreviewScreenshot.refImagePath"],
            srcImagePath = artifacts["PreviewScreenshot.newImagePath"],
            diffImagePath = artifacts["PreviewScreenshot.diffImagePath"],
            diffPercent = artifacts["PreviewScreenshot.diffPercent"]
          )
          dialog.updateDialogWithTestResult(previewDetails, testCase.result == com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult.FAILED)
      }
    }

    dialog.onTestSuiteFinished()
    dialog.show()
  }
}
