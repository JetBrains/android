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

import com.android.tools.idea.testartifacts.instrumented.testsuite.actions.AndroidTestSuiteDetailsActionProvider
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diagnostic.Logger

class ScreenshotTestActionProvider : AndroidTestSuiteDetailsActionProvider {
  private val LOG = Logger.getInstance(this.javaClass)

  override fun isApplicable(runConfiguration: RunConfiguration): Boolean {
    val isApplicable = runConfiguration.name.contains("Screenshot Tests", ignoreCase = true)
    LOG.debug("Checking applicability for run configuration: ${runConfiguration.name}, result: $isApplicable")
    return isApplicable
  }

  override fun getDetailsViewHeaderActions(testResults: AndroidTestResults?): List<AnAction> {
    LOG.debug("Getting details view header actions for test results: $testResults")
    val action = ActionManager.getInstance().getAction("com.android.screenshottest.action.UpdateReferenceImagesFromTestPanelAction")
    (action as? UpdateReferenceImagesFromTestPanelAction)?.let {
      it.testResults = testResults
    }
    action.templatePresentation.putClientProperty("SHOW_TEXT_IN_TOOLBAR", true)
    return listOf(action)
  }
}
