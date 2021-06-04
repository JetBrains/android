/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented

import com.android.tools.idea.testartifacts.instrumented.configuration.AndroidTestConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

/**
 * Shows the dialog to ask a user to opt-in RUN_ANDROID_TEST_USING_GRADLE feature.
 *
 * The dialog is displayed only once and it updates the configuration when a user accepts it.
 */
@JvmOverloads
fun showGradleAndroidTestRunnerOptInDialog(
  project: Project,
  config: AndroidTestConfiguration = AndroidTestConfiguration.getInstance(),
  showDialogFunc: () -> Boolean = {
    Messages.showOkCancelDialog(
      project,
      "Android Studio now supports running all tests via the Gradle test runner, " +
      "to help provide more consistent test results.\n" +
      "This change is compatible with your existing test run configurations.",
      "Running all tests via the Gradle test runner",
      "Enable and Run",
      "Not Now",
      null) == Messages.OK
  }) {
  if (!config.SHOW_RUN_ANDROID_TEST_USING_GRADLE_OPT_IN_DIALOG ||
      config.RUN_ANDROID_TEST_USING_GRADLE) {
    return
  }

  val accepted = showDialogFunc()
  if (accepted) {
    config.RUN_ANDROID_TEST_USING_GRADLE = true
  }

  config.SHOW_RUN_ANDROID_TEST_USING_GRADLE_OPT_IN_DIALOG = false
}
