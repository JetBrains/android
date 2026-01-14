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
package com.android.screenshottest.listener

import com.android.screenshottest.ui.PreviewDetails
import com.android.screenshottest.ui.UpdateReferenceImagesDialog
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.intellij.openapi.application.ApplicationManager

/**
 * A listener that receives screenshot test results and passes them to the UI dialog.
 *
 * @param dialog The dialog to update with the test results.
 */
class UpdateScreenshotTestResultsListener(private val dialog: UpdateReferenceImagesDialog) : AndroidTestResultListener {

  override fun onTestCaseFinished(device: AndroidDevice, testSuite: AndroidTestSuite, testCase: AndroidTestCase) {
    ApplicationManager.getApplication().invokeLater {
      val className = testCase.className
      val methodName = testCase.additionalTestArtifacts["PreviewScreenshot.methodName"]?: " "
      val rawPreviewName = testCase.additionalTestArtifacts["PreviewScreenshot.previewName"]?: " "
      val previewName = cleanPreviewName(rawPreviewName)
      val testId = "$className.$methodName.$previewName"
      val previewDetails = PreviewDetails(
        testId = testId,
        className = className,
        methodName = methodName,
        previewName = previewName,
        testResult = testCase.result,
        destImagePath = testCase.additionalTestArtifacts["PreviewScreenshot.refImagePath"],
        srcImagePath = testCase.additionalTestArtifacts["PreviewScreenshot.newImagePath"],
        diffImagePath = testCase.additionalTestArtifacts["PreviewScreenshot.diffImagePath"],
        diffPercent = testCase.additionalTestArtifacts["PreviewScreenshot.diffPercent"]
      )
      dialog.updateDialogWithTestResult(previewDetails, true)
    }
  }

  override fun onTestSuiteFinished(device: AndroidDevice, testSuite: AndroidTestSuite) {
    ApplicationManager.getApplication().invokeLater {
      dialog.onTestSuiteFinished()
    }
  }

  /**
   * Cleans the preview name by formatting parameter lists.
   *
   * Parses raw strings like "[{provider=com.example.MyProvider}]" into "MyProvider",
   * extracting simple class names for providers.
   */
  private fun cleanPreviewName(name: String): String {
    if (name.startsWith("[{") && name.contains("}]")) {
      return name.substringAfter("[{")
        .substringBefore("}]")
        .split(", ")
        .joinToString("_") { part ->
          if (part.startsWith("provider=")) part.substringAfter("provider=").substringAfterLast('.')
          else part
        } + name.substringAfter("}]")
    }
    return name
  }
}