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
package com.android.screenshottest.gradle

import com.android.screenshottest.ScreenshotTestResultListenerToken
import com.android.screenshottest.ScreenshotTestSuite
import com.android.screenshottest.ui.ScreenshotTestDetailsViewManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.project.Project

/**
 * Displays screenshot test execution and result in tool window view and editor view
 */
class ScreenshotTestResultListenerGradleToken : ScreenshotTestResultListenerToken<GradleProjectSystem>, GradleToken {

  override fun onTestSuiteScheduled(project: Project, testSuite: ScreenshotTestSuite) {
    ScreenshotTestDetailsViewManager.showTestResultInEditorTab(project, testSuite)
  }

  override fun onTestCaseStarted(project: Project, testSuite: ScreenshotTestSuite) {
    ScreenshotTestDetailsViewManager.updateTestResultWithoutFocus(project, testSuite)
  }

  override fun onTestSuiteFinished(project: Project, testSuite: ScreenshotTestSuite) {
    // TODO("Not yet implemented")
  }

  override fun onRerunScheduled(project: Project, testSuite: ScreenshotTestSuite) {
    // TODO("Not yet implemented")
  }

  override fun isApplicable(projectSystem: AndroidProjectSystem): Boolean =
    StudioFlags.ENABLE_SCREENSHOT_TESTING.get() && projectSystem is GradleProjectSystem
}