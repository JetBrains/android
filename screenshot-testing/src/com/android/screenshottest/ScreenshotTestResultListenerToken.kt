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
package com.android.screenshottest

import com.android.annotations.concurrency.AnyThread
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.Token
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * A project system specific screenshot-test test result listener. Use this to display and update test results details editor tab.
 */
interface ScreenshotTestResultListenerToken<P : AndroidProjectSystem> : Token {
  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName<ScreenshotTestResultListenerToken<AndroidProjectSystem>>(
      "com.android.screenshottest.screenshotTestResultListenerToken")
  }

  /**
   * Called when screenshot test is scheduled.
   */
  @AnyThread
  fun onTestSuiteScheduled(project: Project, testSuite: ScreenshotTestSuite)

  /**
   * Called when a screenshot test suite starts.
   */
  @AnyThread
  fun onTestCaseStarted(project: Project, testSuite: ScreenshotTestSuite)

  /**
   * Called when a screenshot test suite finishes
   */
  @AnyThread
  fun onTestSuiteFinished(project: Project, testSuite: ScreenshotTestSuite)

  /**
   * Called when a screenshot test is scheduled for re-run.
   */
  @AnyThread
  fun onRerunScheduled(project: Project, testSuite: ScreenshotTestSuite)
}

/**
 * Temporary data class for screenshot test suite.
 */
data class ScreenshotTestSuite(
  val id: String = "",
  val name: String = "",
  val testResult: ScreenshotTestResultProto = ScreenshotTestResultProto()
)

/**
 * Temporary data class for screenshot test result. This should be replaced by the actual proto in production.
 */
data class ScreenshotTestResultProto(
  val newImageUrl: String = "",
  val diffImageUrl: String = "",
  val referenceImageUrl: String = "",
)
